package io.github.toyota32k.media.lib.extractor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC
import android.media.MediaFormat
import android.util.Log
import io.github.toyota32k.media.lib.codec.BaseCodec
import io.github.toyota32k.media.lib.codec.BaseDecoder
import io.github.toyota32k.media.lib.converter.CloseableExtractor
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.ITrimmingRangeList
import io.github.toyota32k.media.lib.converter.TrimmingRangeListImpl
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.utils.DurationEstimator
import io.github.toyota32k.media.lib.utils.TimeSpan
import io.github.toyota32k.utils.UtLog
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.math.abs

class Extractor private constructor(private val inPath: IInputMediaFile, private val type: Muxer.SampleType) : Closeable {
    companion object {
        fun create(inPath: IInputMediaFile, type: Muxer.SampleType) : Extractor? {
            return try {
                Extractor(inPath, type)
            } catch (e:UnsupportedOperationException) {
                return null
            }
        }
        fun findTrackIdx(extractor: MediaExtractor, type: String): Int {
            for (idx in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(idx)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith(type) == true) {
                    return idx
                }
            }
            return -1
        }

        fun getMediaFormat(extractor: MediaExtractor, idx:Int): MediaFormat {
            return extractor.getTrackFormat(idx)
        }
    }

    private val closeableExtractor: CloseableExtractor = inPath.openExtractor()
    val extractor get() = closeableExtractor.obj
    private var trackIdx:Int = -1
    var logger: UtLog = UtLog("Extractor", Converter.logger)

    fun getMediaFormat(): MediaFormat {
        return extractor.getTrackFormat(trackIdx)
    }

    init {
        logger = UtLog("Extractor($type)", Converter.logger)
        trackIdx = findTrackIdx(extractor, type.trackName)
        if(trackIdx<0) throw UnsupportedOperationException("not found track: ${type.trackName}")
        extractor.selectTrack(trackIdx)
    }

    private fun Long.toUsTimeString():String {
        return TimeSpan.formatAutoM(this/1000L)
    }
    var eos:Boolean = false
        private set
    private var trimmingRangeList : ITrimmingRangeList = ITrimmingRangeList.empty()
//        set(v) {
////            val org = field
//            field = v
////            logger.assert(false, "don't set trimmingRange twice.")
//            test()
//            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
////            if(v.hasStart) {
////                extractor.seekTo(v.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
////                logger.debug("SeekTo: ${trimmingRange.startUs/1000L} result: ${extractor.sampleTime/1000L}")
////            } else if(org.hasStart) {
////                logger.assert(false, "don't set trimmingRange twice.")
////                extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
////            }
//        }

//    fun test(originalList:ITrimmingRangeList) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
//            logger.debug("TEST: sampleTime = ${TimeSpan.formatAutoM(extractor.sampleTime/1000)}, cachedDuration=${TimeSpan.formatAutoM(extractor.cachedDuration/1000)}, sampleFlags=${extractor.sampleFlags}, sampleSize=${extractor.sampleSize}")
//
//            var state: ITrimmingRangeList.PositionState
//            var position = 0L
//            while(true) {
//                val range = trimmingRangeList.getNextValidPosition(position) ?: break
//                logger.debug("TEST: seekTo: ${TimeSpan.formatAutoM(range.startUs/1000)}")
//                extractor.seekTo(range.startUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
//                logger.debug("TEST: seekTo(NEXT   ) sampleTime = ${TimeSpan.formatAutoM(extractor.sampleTime/1000)}, cachedDuration=${TimeSpan.formatAutoM(extractor.cachedDuration/1000)}, sampleFlags=${extractor.sampleFlags}, sampleSize=${extractor.sampleSize}")
//                extractor.seekTo(range.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
//                logger.debug("TEST: seekTo(PREV   ) sampleTime = ${TimeSpan.formatAutoM(extractor.sampleTime/1000)}, cachedDuration=${TimeSpan.formatAutoM(extractor.cachedDuration/1000)}, sampleFlags=${extractor.sampleFlags}, sampleSize=${extractor.sampleSize}")
//                extractor.seekTo(range.startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
//                logger.debug("TEST: seekTo(CLOSEST) sampleTime = ${TimeSpan.formatAutoM(extractor.sampleTime/1000)}, cachedDuration=${TimeSpan.formatAutoM(extractor.cachedDuration/1000)}, sampleFlags=${extractor.sampleFlags}, sampleSize=${extractor.sampleSize}")
//                position = range.actualEndUs
//                if(position==0L) break
//            }
//        }
//    }

    /**
     * オーディオトラックはだいたいどこへでもシーク可能なのに対して、ビデオトラックは、キーフレームにしかシークできないので、
     * あらかじめ、与えられたチャプターの先頭位置を、CLOSESTなシーク可能位置に調整しておき、音声トラックもこれを参照することで、
     * 音声と映像のズレの最小化を図る。
     */
    fun adjustAndSetTrimmingRangeList(originalList: ITrimmingRangeList, durationUs:Long):ITrimmingRangeList {
        if(originalList.list.isEmpty()) {
            originalList.closeBy(durationUs)
            this.trimmingRangeList = originalList
            return originalList
        }

        if(!inPath.seekable) {
            logger.assert(false, "trimming may not work because input file is not seekable")
        }

        // 与えられたRangeList の開始位置を、実際にビデオトラックでシーク可能な位置に調整する。
        // 終了位置は、readSampleData()が適当に刻んでくるので、そのまま使う。
        val newList = TrimmingRangeListImpl()
        val trackIdx = findTrackIdx(extractor, "video")
        extractor.selectTrack(trackIdx)
        for (range in originalList.list) {
            if (range.startUs == 0L) {
                newList.addRange(0, range.endUs)
            } else {
                extractor.seekTo(range.startUs, SEEK_TO_CLOSEST_SYNC)
                logger.debug { "actually sought to: ${extractor.sampleTime.toUsTimeString()} (req: ${range.startUs.toUsTimeString()})" }
                newList.addRange(extractor.sampleTime, range.endUs)
            }
        }
        // extractor.seekTo(0, SEEK_TO_CLOSEST_SYNC)
        // logger.debug { "sought to: ${extractor.sampleTime.toUsTimeString()} (req: 0)" }
        // ローカルファイルの場合は、seekTo(0) で先頭に戻れるが、
        // httpの場合に、微妙にずれるし、そのあとのデータ読込みに失敗するので、MediaExtractorを作り直す。
        extractor.seekTo(0L, SEEK_TO_CLOSEST_SYNC)

        newList.closeBy(durationUs)
        this.trimmingRangeList = newList
        logger.verbose("adjusted trimming ranges:")
        trimmingRangeList.list.forEach {
            logger.verbose {"  ${it.startUs.toUsTimeString()} - ${it.endUs.toUsTimeString()}"}
        }
        return newList
    }
    fun setTrimmingRangeList(list:ITrimmingRangeList) {
        this.trimmingRangeList = list
    }

    private var totalTime = 0L
    private var totalSkippedTime = 0L
    private val durationEstimator = DurationEstimator()
    val naturalDurationUs:Long get() = durationEstimator.estimatedDurationUs

//    private var _sinkBuffer : ByteBuffer? = null
//    private fun getSinkBuffer(size:Long) : ByteBuffer {
//        val buffer = _sinkBuffer
//        if(buffer==null || buffer.capacity()<size) {
//           _sinkBuffer = ByteBuffer.allocate(max((size+1024).toInt(),5000))
//        }
//        return _sinkBuffer!!
//    }

    /**
     * extractor.seekTo で正確にシークできないので、SEEK_TO_PREVIOUS_SYNC でシーク後、readSampleData()で指定位置まで読み飛ばす作戦。
     * かなり正確にシークできるのだが、この後の、本番読み込み(readSampleData+advance) で extractor.sampleTime がSEEK_TO_PREVIOUS_SYNCの位置まで戻ってしまう現象が発生。
     * もう１回余分にreadSampleData+advance することで、前に進むようになったが、やはり、キーフレームがない状態で動画を繋いでしまうため、画像が乱れる。
     * --> 没
     */
//    private fun bestEffortSeekTo(positionUs:Long) {
//        logger.debug("SKIPPING from:${extractor.sampleTime.toUsTimeString()} to:${positionUs.toUsTimeString()}")
//        extractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            while(extractor.sampleTime<positionUs) {
//                val buffer = getSinkBuffer(extractor.sampleSize)
//                val sampleSize = extractor.readSampleData(buffer, 0)
//                extractor.advance()
//                logger.debug("SEEKING: current=${extractor.sampleTime.toUsTimeString()} to:to:${positionUs.toUsTimeString()} (size=$sampleSize)")
//            }
//            logger.debug("SKIPPED to:${extractor.sampleTime.toUsTimeString()} ideal:${positionUs.toUsTimeString()} -- delta=${abs(positionUs-extractor.sampleTime)}")
//            val buffer = getSinkBuffer(extractor.sampleSize)
//            val sampleSize = extractor.readSampleData(buffer, 0)
//            extractor.advance()
//            logger.debug("SKIPPED2 to:${extractor.sampleTime.toUsTimeString()} ideal:${positionUs.toUsTimeString()} -- delta=${abs(positionUs-extractor.sampleTime)}")
//        } else {
//            extractor.seekTo(positionUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
//        }
//    }

    fun chainTo(output: BaseDecoder) : Boolean {
        if(eos) return false
        logger.assert(trackIdx>=0, "selectTrack() must be called before.")

        var currentPositionUs = extractor.sampleTime
        val positionState = trimmingRangeList.positionState(currentPositionUs)

        // シークする（＝デコーダーにデータを書き込まない）場合は、decoder.dequeueInputBuffer() を呼んではならない。
        // dequeueInputBuffer すると、dequeue済みバッファーとして予約され queueInputBuffer されるまで使えなくなる。
        // これを何度も繰り返す（=トリミング区間が増える）と、使えるバッファがなくなって、"no buffer in the decoder" が発生する。
        if(positionState == ITrimmingRangeList.PositionState.OUT_OF_RANGE) {
            logger.debug("FOUND End Of Chapter: ${TimeSpan.formatAutoM(extractor.sampleTime/1000)}")
            val position = trimmingRangeList.getNextValidPosition(extractor.sampleTime)
            if (position != null) {
                logger.chronos(msg="extractor.seekTo", level = Log.INFO) {
                    try {
                        extractor.seekTo(position.startUs, SEEK_TO_CLOSEST_SYNC)
                    } catch(e:Throwable) {
                        logger.error(e)
                    }
                }
                logger.info("SKIPPED from ${currentPositionUs.toUsTimeString()} to ${extractor.sampleTime.toUsTimeString()} (req:${position.startUs.toUsTimeString()} Δ=${abs(position.startUs-extractor.sampleTime).toUsTimeString()})")
                val skippedTime = extractor.sampleTime - currentPositionUs
                currentPositionUs = extractor.sampleTime
                totalSkippedTime += skippedTime
            }
        }

//        if(skipping) {
//            skipping = false
//            skippedTime += (sampleTime-lastSampleTime)
//            logger.debug("SKIPPED (${logger.tag}: ${TimeSpan.formatAutoM(skippingTo/1000)} (ideal:${TimeSpan.formatAutoM(sampleTime/1000)}) delta=${sampleTime-skippingTo} us (${TimeSpan.formatAutoM(abs(sampleTime-skippingTo) /1000)}")
//        }

        val decoder = output.decoder
        val inputBufferIdx = decoder.dequeueInputBuffer(BaseCodec.TIMEOUT_IMMEDIATE)
        if(inputBufferIdx<0) {
            logger.verbose("no buffer in the decoder.")
            return false
        }

        val idx = extractor.sampleTrackIndex
        if(idx<0||positionState == ITrimmingRangeList.PositionState.END) {
            logger.debug("found eos")
            if(durationEstimator.estimatedDurationUs < trimmingRangeList.trimmedDurationUs - 2*1000*1000) {
                // 読み込んだ時間が、期待する時間より2秒以上短ければ、たぶんなんかエラーが起きている。
                logger.warn("conversion may be failed.")
            }
            eos = true
            decoder.queueInputBuffer(inputBufferIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } else {
            val inputBuffer = decoder.getInputBuffer(inputBufferIdx) as ByteBuffer
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                logger.debug("READING: sampleTime = ${TimeSpan.formatAutoM(extractor.sampleTime/1000)}, cachedDuration=${TimeSpan.formatAutoM(extractor.cachedDuration/1000)}, sampleFlags=${extractor.sampleFlags}, sampleSize=${extractor.sampleSize}")
//            }
            val sampleSize = extractor.readSampleData(inputBuffer, 0)

            if(sampleSize>0) {
//                logger.assert(currentPositionUs == extractor.sampleTime)
                logger.verbose {"READING: read $sampleSize bytes at ${TimeSpan.formatAutoM(currentPositionUs/1000)}"}
                totalTime = currentPositionUs - totalSkippedTime
                durationEstimator.update(totalTime, sampleSize.toLong())
                decoder.queueInputBuffer(inputBufferIdx, 0, sampleSize, totalTime, extractor.sampleFlags)
            } else {
                logger.error("zero byte read.")
            }
            extractor.advance()
//            logger.debug("READ: from ${currentPositionUs.toUsTimeString()} to ${extractor.sampleTime.toUsTimeString()}")
        }
        return true
    }

    private var disposed = false
    override fun close() {
        if(!disposed) {
            disposed = true
            closeableExtractor.close()
            logger.debug("disposed")
        }
    }
}

