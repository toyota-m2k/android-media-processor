package io.github.toyota32k.media.lib.track

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import io.github.toyota32k.media.lib.codec.BaseEncoder
import io.github.toyota32k.media.lib.misc.ISO6709LocationParser
import io.github.toyota32k.media.lib.misc.MediaFile
import io.github.toyota32k.media.lib.utils.UtLog
import io.github.toyota32k.media.lib.utils.UtLoggerInstance
import java.io.Closeable
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Muxer(inPath:MediaFile, outPath:MediaFile, val hasAudio:Boolean): Closeable {
    companion object {
        val logger = UtLog("Muxer", null, "io.github.toyota32k.")
        const val BUFFER_SIZE = 64 * 1024
    }

    enum class SampleType { Audio, Video }

    val muxer:MediaMuxer = outPath.fileDescriptorToWrite { fd-> MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) }
    var durationUs: Long = -1L   // for progress
        private set
    var videoFormat: MediaFormat? = null
    var audioFormat: MediaFormat? = null
    var videoTrackIndex = -1
    var audioTrackIndex = -1

    init {
        setupMetaDataBy(inPath)
    }

    private fun setupMetaDataBy(inPath: MediaFile) {
        val mediaMetadataRetriever = inPath.fileDescriptorToRead { fd-> MediaMetadataRetriever().apply { setDataSource(fd) }}
        val rotation = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
        if (rotation != null) {
            muxer.setOrientationHint(rotation)
            logger.info("metadata: rotation=$rotation")
        }

        val locationString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
        if (locationString != null) {
            val location: FloatArray? = ISO6709LocationParser.parse(locationString)
            if (location != null) {
                muxer.setLocation(location[0], location[1])
                logger.info("metadata: latitude=${location[0]}, longitude=${location[1]}")
            } else {
                logger.error("metadata: failed to parse the location metadata: $locationString")
            }
        }

        val duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.also {
            durationUs = it * 1000
            logger.info("metadata: duration=${durationUs/1000} ms")
        }
    }

    val isReady get() = videoFormat != null && (!hasAudio || audioFormat != null)
    private var mByteBuffer: ByteBuffer? = null
    private val mSampleInfoList = mutableListOf<SampleInfo>()

    fun trackIndexOf(sampleType: SampleType): Int {
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
        if (isReady) {
            muxer.writeSampleData(trackIndexOf(sampleType), byteBuf, bufferInfo)
            return
        }

        // フォーマットが確定する前に書き込まれたデータは退避しておく。
        byteBuf.limit(bufferInfo.offset + bufferInfo.size)
        byteBuf.position(bufferInfo.offset)

        val byteBuffer = mByteBuffer ?: ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder()).also { mByteBuffer = it }
        byteBuffer.put(byteBuf)
        mSampleInfoList.add(SampleInfo(sampleType, bufferInfo.size, bufferInfo))
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
            muxer.stop()
            muxer.release()
        }
    }
}