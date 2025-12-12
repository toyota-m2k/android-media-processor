package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IOutputMediaFile
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.misc.ISO6709LocationParser
import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.track.Muxer.Companion.BUFFER_SIZE
import io.github.toyota32k.media.lib.track.Muxer.Companion.logger
import io.github.toyota32k.media.lib.track.Muxer.SampleInfo
import io.github.toyota32k.media.lib.track.Muxer.SampleType
import io.github.toyota32k.media.lib.utils.DurationEstimator
import io.github.toyota32k.media.lib.utils.ExpandableByteBuffer
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SyncMuxer(val outFile: IOutputMediaFile, val containerFormat: ContainerFormat, val hasVideo:Boolean, val hasAudio:Boolean): Closeable {
    companion object {
        val logger = UtLog("Muxer", Processor.logger)
        const val BUFFER_SIZE = 64 * 1024
    }
    init {
        if (!hasVideo&&!hasAudio) throw IllegalStateException("no track")
    }

    private val mMuxerRef = outFile.openMuxer(containerFormat)
    private val muxer get() = mMuxerRef.obj

    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    private fun DurationEstimator.update(bufferInfo: MediaCodec.BufferInfo) = update(bufferInfo.presentationTimeUs, bufferInfo.size.toLong())
    private val durationEstimator = DurationEstimator()
    val naturalDurationUs:Long get() = durationEstimator.estimatedDurationUs

//    val isVideoReady get() = videoFormat != null
//    val isAudioReady get() = !hasAudio || audioFormat != null
//    val isReady get() = videoFormat != null && (!hasAudio || audioFormat != null)

    private var muxerStarted = false
    private var videoEos:Boolean = false
    private var audioEos:Boolean = false
    val eos:Boolean get() = (!hasVideo || videoEos) && (!hasAudio || audioEos)

    private val mExByteBuffer = ExpandableByteBuffer(BUFFER_SIZE,BUFFER_SIZE)
    private val mSampleInfoList = mutableListOf<SampleInfo>()

    private class SampleInfo(val video: Boolean, val size: Int, bufferInfo: MediaCodec.BufferInfo) {
        private val mPresentationTimeUs: Long = bufferInfo.presentationTimeUs
        private val mFlags: Int = bufferInfo.flags

        fun writeToBufferInfo(bufferInfo: MediaCodec.BufferInfo, offset: Int) {
            bufferInfo[offset, size, mPresentationTimeUs] = mFlags
        }
    }

    private fun trackIndexOf(video: Boolean): Int {
        return when (video) {
            true -> videoTrackIndex
            false -> audioTrackIndex
        }
    }

    fun setup(metaData: MetaData, rotation: Rotation?) {
        val metaRotation = metaData.rotation
        if (metaRotation != null) {
            val r = rotation?.rotate(metaRotation) ?: metaRotation
            muxer.setOrientationHint(r)
            Processor.Companion.logger.info("metadata: rotation=$metaRotation --> $r")
        } else if(rotation!=null){
            muxer.setOrientationHint(rotation.rotate(0))
        }
        val locationString = metaData.location
        if (locationString != null) {
            val location: FloatArray? = ISO6709LocationParser.parse(locationString)
            if (location != null) {
                muxer.setLocation(location[0], location[1])
                Processor.Companion.logger.info("metadata: latitude=${location[0]}, longitude=${location[1]}")
            } else {
                Companion.logger.error("metadata: failed to parse the location metadata: $locationString")
            }
        }
    }


    /**
     * outputFormat を設定して、muxer.addTrack()する。
     * video/audio format が揃ったら muxer.start()する。
     */
    fun setOutputFormat(video:Boolean, format: MediaFormat) {
        when (video) {
            true -> {
                if (videoFormat != null) return // 2回目以降は無視
                videoFormat = format
            }
            false -> {
                if (audioFormat != null) return // 2回目以降は無視
                audioFormat = format
            }
        }
        val videoFormat = this.videoFormat
        val audioFormat = this.audioFormat
        if ((videoFormat != null || !hasVideo) && (audioFormat != null || !hasAudio)) {
            // videoFormat と audioFormat の両方が揃ったら muxer.start()
            if (hasVideo && videoFormat!=null) {
                videoTrackIndex = muxer.addTrack(videoFormat)
                logger.debug("Added track #$videoTrackIndex with ${videoFormat.getString(MediaFormat.KEY_MIME)} to muxer")
            }
            if(hasAudio && audioFormat!=null) {
                audioTrackIndex = muxer.addTrack(audioFormat)
                logger.debug("Added track #$audioTrackIndex with ${audioFormat.getString(MediaFormat.KEY_MIME)} to muxer")
            }
            muxer.start()
            muxerStarted = true

            // フォーマット確定前に書き込まれた保留中のデータがあればmuxerに書き込む
            val byteBuffer = mExByteBuffer.bufferOrNull ?: return
            byteBuffer.flip()
            logger.debug("Output format determined, writing ${mSampleInfoList.size} samples / ${byteBuffer.limit()} bytes to muxer.")
            val bufferInfo = MediaCodec.BufferInfo()
            var offset = 0
            for (sampleInfo in mSampleInfoList) {
                sampleInfo.writeToBufferInfo(bufferInfo, offset)
                muxer.writeSampleData(trackIndexOf(sampleInfo.video), byteBuffer, bufferInfo)
                offset += sampleInfo.size
            }
            mSampleInfoList.clear()
            mExByteBuffer.free()
        }
    }

    /**
     * （Encoderから）muxerにデータを書き込む
     */
    fun writeSampleData(video:Boolean, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if(video) {
            durationEstimator.update(bufferInfo)
        }
        if (muxerStarted) {
            if(bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) {
                logger.debug("${if(video)"video" else "audio"} truck -- reached to eos.")
                if (video) {
                    videoEos = true
                } else {
                    audioEos = true
                }
                if (bufferInfo.size==0) {
                    return
                }
                bufferInfo.flags = bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
            }
            muxer.writeSampleData(trackIndexOf(video), byteBuf, bufferInfo)
            return
        }

        // フォーマットが確定する前に書き込まれたデータは退避しておく。
        byteBuf.limit(bufferInfo.offset + bufferInfo.size)
        byteBuf.position(bufferInfo.offset)

        val byteBuffer = mExByteBuffer.alloc(bufferInfo.size).order(ByteOrder.nativeOrder())
        byteBuffer.put(byteBuf)
        mSampleInfoList.add(SampleInfo(video, bufferInfo.size, bufferInfo))
    }

    fun stop() {
        if (muxerStarted) {
            muxerStarted = false
            muxer.stop()
        }
        mMuxerRef.close()
    }

    override fun close() {
        stop()
    }
}