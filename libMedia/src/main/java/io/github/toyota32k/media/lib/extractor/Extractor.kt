package io.github.toyota32k.media.lib.extractor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.BaseCodec
import io.github.toyota32k.media.lib.codec.BaseDecoder
import io.github.toyota32k.media.lib.converter.*
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.utils.UtLog
import java.io.Closeable
import java.nio.ByteBuffer

class Extractor(inPath: AndroidFile) : Closeable {
    lateinit var logger: UtLog
    val extractor = inPath.fileDescriptorToRead { fd-> MediaExtractor().apply { setDataSource(fd) }}
    var trackIdx:Int = -1
    protected lateinit var inputFormat:MediaFormat
    var eos:Boolean = false
        private set
    var trimmingRangeList : ITrimmingRangeList = ITrimmingRangeList.empty()
        set(v) {
//            val org = field
            field = v
//            logger.assert(false, "don't set trimmingRange twice.")
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
//            if(v.hasStart) {
//                extractor.seekTo(v.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
//                logger.debug("SeekTo: ${trimmingRange.startUs/1000L} result: ${extractor.sampleTime/1000L}")
//            } else if(org.hasStart) {
//                logger.assert(false, "don't set trimmingRange twice.")
//                extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
//            }
        }

    fun selectTrack(idx:Int, type: Muxer.SampleType) {
        logger = UtLog("Extractor($type)", Converter.logger)
        trackIdx = idx
        extractor.selectTrack(idx)
    }

    var totalTime = 0L
        private set

    fun chainTo(output: BaseDecoder) : Boolean {
        if(eos) return false
        logger.assert(trackIdx>=0, "selectTrack() must be called before.")

        val sampleTime = extractor.sampleTime
        val positionState = trimmingRangeList.positionState(sampleTime)

        // シークする（＝デコーダーにデータを書き込まない）場合は、decoder.dequeueInputBuffer() を呼んではならない。
        // dequeueInputBuffer すると、dequeue済みバッファーとして予約され queueInputBuffer されるまで使えなくなる。
        // これを何度も繰り返す（=トリミング区間が増える）と、使えるバッファがなくなって、"no buffer in the decoder" が発生する。
        if(positionState == ITrimmingRangeList.PositionState.OUT_OF_RANGE) {
            val position = trimmingRangeList.getNextValidPosition(extractor.sampleTime)
            if (position != null) {
                logger.info("seek to the next range.")
                extractor.seekTo(position.startUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
                return true
            }
        }


        val decoder = output.decoder
        val inputBufferIdx = decoder.dequeueInputBuffer(BaseCodec.TIMEOUT_IMMEDIATE)
        if(inputBufferIdx<0) {
            logger.debug("no buffer in the decoder.")
            return false
        }

        val idx = extractor.getSampleTrackIndex()
        if(idx<0||positionState == ITrimmingRangeList.PositionState.END) {
            logger.debug("found eos")
            eos = true
            decoder.queueInputBuffer(inputBufferIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } else {
            val inputBuffer = decoder.getInputBuffer(inputBufferIdx) as ByteBuffer
            val sampleSize = extractor.readSampleData(inputBuffer, 0)

            if(sampleSize>0) {
                logger.assert(sampleTime == extractor.sampleTime)
                logger.verbose {"read $sampleSize bytes at ${sampleTime/1000} ms"}
                decoder.queueInputBuffer(inputBufferIdx, 0, sampleSize, totalTime, extractor.sampleFlags)
                totalTime += sampleTime
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

