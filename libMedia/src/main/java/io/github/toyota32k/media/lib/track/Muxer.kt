package io.github.toyota32k.media.lib.track

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.IOutputMediaFile
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.getLocation
import io.github.toyota32k.media.lib.format.getRotation
import io.github.toyota32k.media.lib.misc.ISO6709LocationParser
import io.github.toyota32k.media.lib.utils.DurationEstimator
import io.github.toyota32k.utils.UtLog
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Muxer(inputMetaData: MetaData, outPath: IOutputMediaFile, private val hasAudio:Boolean, rotation: Rotation?, private val containerFormat: ContainerFormat): Closeable {
    companion object {
        val logger = UtLog("Muxer", Converter.logger)
        const val BUFFER_SIZE = 64 * 1024
    }

    enum class SampleType(val trackName: String) {
        Audio("audio"),
        Video("video")
    }

    private val closeableMuxer = outPath.openMuxer(containerFormat)
    private val muxer:MediaMuxer get() = closeableMuxer.obj
    var durationUs: Long = -1L   // for progress
        private set

    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    init {
        setupMetaDataBy(inputMetaData, rotation)
    }

    private fun setupMetaDataBy(metaData: MetaData, rotation: Rotation?) {
            val metaRotation = metaData.rotation
            if (metaRotation != null) {
                val r = rotation?.rotate(metaRotation) ?: metaRotation
                muxer.setOrientationHint(r)
                logger.info("metadata: rotation=$metaRotation --> $r")
            } else if(rotation!=null){
                muxer.setOrientationHint(rotation.rotate(0))
            }
            val locationString = metaData.location
            if (locationString != null) {
                val location: FloatArray? = ISO6709LocationParser.parse(locationString)
                if (location != null) {
                    muxer.setLocation(location[0], location[1])
                    logger.info("metadata: latitude=${location[0]}, longitude=${location[1]}")
                } else {
                    logger.error("metadata: failed to parse the location metadata: $locationString")
                }
            }
        val duration = metaData.duration
        if(duration!=null) {
            durationUs = duration*1000L
            logger.info("metadata: duration=${durationUs / 1000} ms")
        }
    }

    val isVideoReady get() = videoFormat != null
    val isAudioReady get() = !hasAudio || audioFormat != null
    val isReady get() = videoFormat != null && (!hasAudio || audioFormat != null)
    private var mByteBuffer: ByteBuffer? = null
    private val mSampleInfoList = mutableListOf<SampleInfo>()

    private fun trackIndexOf(sampleType: SampleType): Int {
        return when (sampleType) {
            SampleType.Video -> videoTrackIndex
            SampleType.Audio -> audioTrackIndex
        }
    }

    fun setOutputFormat(type: SampleType, format: MediaFormat) {
        when (type) {
            SampleType.Video -> {
                if (videoFormat != null) throw IllegalStateException("video format changed more than twice.")
                videoFormat = format
            }
            SampleType.Audio -> {
                if (audioFormat != null) throw IllegalStateException("audio format changed more than twice.")
                audioFormat = format
            }
        }
        if (isReady) {
            videoTrackIndex = muxer.addTrack(videoFormat!!)
            logger.debug("Added track #$videoTrackIndex with ${videoFormat!!.getString(MediaFormat.KEY_MIME)} to muxer")
            if(hasAudio) {
                audioTrackIndex = muxer.addTrack(audioFormat!!)
                logger.debug("Added track #$audioTrackIndex with ${audioFormat!!.getString(MediaFormat.KEY_MIME)} to muxer")
            }
            muxer.start()

            // フォーマット確定前に書き込まれた保留中のデータがあればmuxerに書き込む
            val byteBuffer = mByteBuffer ?: return
            byteBuffer.flip()
            logger.debug("Output format determined, writing ${mSampleInfoList.size} samples / ${byteBuffer.limit()} bytes to muxer.")
            val bufferInfo = MediaCodec.BufferInfo()
            var offset = 0
            for (sampleInfo in mSampleInfoList) {
                sampleInfo.writeToBufferInfo(bufferInfo, offset)
                muxer.writeSampleData(trackIndexOf(sampleInfo.sampleType), byteBuffer, bufferInfo)
                offset += sampleInfo.size
            }
            mSampleInfoList.clear()
            mByteBuffer = null
        }
    }

    fun writeSampleData(sampleType: SampleType, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if(sampleType== SampleType.Video) {
            durationEstimator.update(bufferInfo)
        }
        if (isReady) {
            muxer.writeSampleData(trackIndexOf(sampleType), byteBuf, bufferInfo)
            if(bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) {
                logger.debug("$sampleType -- reached to eos.")
                complete(sampleType)
            }
            return
        }

        // フォーマットが確定する前に書き込まれたデータは退避しておく。
        byteBuf.limit(bufferInfo.offset + bufferInfo.size)
        byteBuf.position(bufferInfo.offset)

        val byteBuffer = mByteBuffer ?: ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder()).also { mByteBuffer = it }
        byteBuffer.put(byteBuf)
        mSampleInfoList.add(SampleInfo(sampleType, bufferInfo.size, bufferInfo))
    }

    private var isVideoCompleted:Boolean = false
    private var isAudioCompleted:Boolean = false
    private var muxerStopped = false

    fun complete(sampleType: SampleType) {
        when(sampleType) {
            SampleType.Audio -> isAudioCompleted = true
            SampleType.Video -> isVideoCompleted = true
        }
        if(isAudioCompleted && isVideoCompleted) {
            stopMuxer()
        }
    }

    private fun stopMuxer() {
        if(!muxerStopped) {
            muxerStopped = true
            try {
                logger.debug("muxer stopped.")
                muxer.stop()
            } catch (e: Throwable) {
                logger.stackTrace(e)
            }
        }
    }

    private class SampleInfo(sampleType: SampleType, size: Int, bufferInfo: MediaCodec.BufferInfo) {
        val sampleType: SampleType
        val size: Int
        private val mPresentationTimeUs: Long
        private val mFlags: Int

        fun writeToBufferInfo(bufferInfo: MediaCodec.BufferInfo, offset: Int) {
            bufferInfo[offset, size, mPresentationTimeUs] = mFlags
        }

        init {
            this.sampleType = sampleType
            this.size = size
            mPresentationTimeUs = bufferInfo.presentationTimeUs
            mFlags = bufferInfo.flags
        }
    }

    private var disposed = false
    override fun close() {
        if(!disposed) {
            disposed = true
            closeableMuxer.close()
            logger.debug("disposed")
        }
    }

    private fun DurationEstimator.update(bufferInfo: MediaCodec.BufferInfo) = update(bufferInfo.presentationTimeUs, bufferInfo.size.toLong())
    private val durationEstimator = DurationEstimator()
    val naturalDurationUs:Long get() = durationEstimator.estimatedDurationUs
}