package io.github.toyota32k.media.lib.extractor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.BaseCodec
import io.github.toyota32k.media.lib.codec.BaseDecoder
import io.github.toyota32k.media.lib.misc.MediaFile
import io.github.toyota32k.media.lib.utils.Chronos
import io.github.toyota32k.media.lib.utils.UtLog
import io.github.toyota32k.media.lib.utils.UtLoggerInstance
import java.io.Closeable
import java.nio.ByteBuffer

class Extractor(inPath: MediaFile) : Closeable {
    companion object {
        val logger = UtLog("Extractor", null, "io.github.toyota32k.")
    }

    val extractor = inPath.fileDescriptorToRead { fd-> MediaExtractor().apply { setDataSource(fd) }}
    var trackIdx:Int = -1
    protected lateinit var inputFormat:MediaFormat
    var eos:Boolean = false
        private set

    fun selectTrack(idx:Int) {
        trackIdx = idx
        extractor.selectTrack(idx)
    }

    fun chainTo(output: BaseDecoder) : Boolean {
        return Chronos(logger).measure {
            if(eos) return@measure false
            logger.assert(trackIdx>=0, "selectTrack() must be called before.")
            val decoder = output.decoder
            val inputBufferIdx = decoder.dequeueInputBuffer(BaseCodec.TIMEOUT_IMMEDIATE).takeIf { it>=0 } ?: return@measure false
            val idx = extractor.getSampleTrackIndex()
            if(idx<0) {
                eos = true
                decoder.queueInputBuffer(inputBufferIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                val inputBuffer = decoder.getInputBuffer(inputBufferIdx) as ByteBuffer
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                decoder.queueInputBuffer(inputBufferIdx, 0, sampleSize, extractor.sampleTime, extractor.sampleFlags)
                extractor.advance()
            }
            true
        }
    }

    private var disposed = false
    override fun close() {
        if(!disposed) {
            disposed = true
            extractor.release()
        }
    }
}

