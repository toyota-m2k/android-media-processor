package io.github.toyota32k.media.lib.extractor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.BaseCodec
import io.github.toyota32k.media.lib.codec.BaseDecoder
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.converter.TrimmingRange
import io.github.toyota32k.media.lib.utils.UtLog
import java.io.Closeable
import java.nio.ByteBuffer

class Extractor(inPath: AndroidFile) : Closeable {
//    companion object {
//        val logger = UtLog("Extractor", null, "io.github.toyota32k.")
//    }

    lateinit var logger:UtLog
    val extractor = inPath.fileDescriptorToRead { fd-> MediaExtractor().apply { setDataSource(fd) }}
    var trackIdx:Int = -1
    protected lateinit var inputFormat:MediaFormat
    var eos:Boolean = false
        private set
    var trimmingRange = TrimmingRange.Empty
        set(v) {
            val org = field
            field = v
            if(v.hasStart) {
                extractor.seekTo(v.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                logger.debug("SeekTo: ${trimmingRange.startUs/1000L} result: ${extractor.sampleTime/1000L}")
            } else if(org.hasStart) {
                logger.assert(false, "don't set trimmingRange twice.")
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
        }

    fun selectTrack(idx:Int, type: Muxer.SampleType) {
        logger = UtLog("Extractor($type)", null, "io.github.toyota32k.")
        trackIdx = idx
        extractor.selectTrack(idx)
    }

    fun chainTo(output: BaseDecoder) : Boolean {
        if(eos) return false
        logger.assert(trackIdx>=0, "selectTrack() must be called before.")

        val decoder = output.decoder
        val inputBufferIdx = decoder.dequeueInputBuffer(BaseCodec.TIMEOUT_IMMEDIATE)
        if(inputBufferIdx<0) {
            logger.debug("no input buffer")
            return false
        }

        val idx = extractor.getSampleTrackIndex()
//        if(trimStartUs>0) {
//            extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
//            logger.debug("SeekTo: $trimStartUs/1000 result: ${extractor.sampleTime/1000L}")
//            trimStartUs = 0L
//        }
        if(idx<0||!trimmingRange.checkEnd(extractor.sampleTime)) {
            logger.debug("found eos")
            eos = true
            decoder.queueInputBuffer(inputBufferIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } else {
            val inputBuffer = decoder.getInputBuffer(inputBufferIdx) as ByteBuffer
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            if(sampleSize>0) {
                val sampleTime = extractor.sampleTime
//                logger.debug("read $sampleSize bytes at ${sampleTime/1000} ms")
                decoder.queueInputBuffer(inputBufferIdx, 0, sampleSize, sampleTime, extractor.sampleFlags)
            } else {
                logger.error("zero byte read.")
            }
            extractor.advance()
        }
        return true
    }

    private var disposed = false
    override fun close() {
        if(!disposed) {
            disposed = true
            extractor.release()
            logger.debug("disposed")
        }
    }
}

