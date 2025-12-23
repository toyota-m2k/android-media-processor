package io.github.toyota32k.media.lib.legacy.extractor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC
import android.media.MediaFormat
import android.util.Log
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.codec.BaseCodec
import io.github.toyota32k.media.lib.codec.BaseDecoder
import io.github.toyota32k.media.lib.io.CloseableExtractor
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.legacy.converter.ITrimmingRangeKeeper
import io.github.toyota32k.media.lib.legacy.converter.PositionState
import io.github.toyota32k.media.lib.legacy.converter.TrimmingRangeKeeper
import io.github.toyota32k.media.lib.processor.contract.ICancellation
import io.github.toyota32k.media.lib.types.RangeUs.Companion.formatAsUs
import io.github.toyota32k.media.lib.legacy.track.Muxer
import io.github.toyota32k.media.lib.utils.DurationEstimator
import io.github.toyota32k.media.lib.utils.TimeSpan
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.math.abs

class Extractor private constructor(
    private val inPath: IInputMediaFile,
    sampleType: Muxer.SampleType,
    cancellation: ICancellation) : Closeable, ICancellation by cancellation {
    companion object {
        fun create(inPath: IInputMediaFile, type: Muxer.SampleType, cancellation: ICancellation) : Extractor? {
            return try {
                Extractor(inPath, type, cancellation)
            } catch (_:UnsupportedOperationException) {
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

    private var logger: UtLog = UtLog("Extractor($sampleType)", Converter.logger)
    private val closeableExtractor: CloseableExtractor = inPath.openExtractor()
    private val extractor:MediaExtractor get() = closeableExtractor.obj
    private var trackIdx:Int = -1
    private var firstRead = true

    private lateinit var chainedDecoder: BaseDecoder
    fun chain(decoder: BaseDecoder) : BaseDecoder {
        chainedDecoder = decoder
        return decoder
    }

    fun getMediaFormat(): MediaFormat {
        return extractor.getTrackFormat(trackIdx)
    }

    init {
        logger = UtLog("Extractor($sampleType)", Converter.logger)
        trackIdx = findTrackIdx(extractor, sampleType.trackName)
        if(trackIdx<0) throw UnsupportedOperationException("not found track: ${sampleType.trackName}")
        extractor.selectTrack(trackIdx)
    }

//    private fun Long.toUsTimeString():String {
//        return TimeSpan.formatAutoM(this/1000L)
//    }
    var eos:Boolean = false
        private set
    private var trimmingRangeList : ITrimmingRangeKeeper = TrimmingRangeKeeper.empty

    /**
     * オーディオトラックはだいたいどこへでもシーク可能なのに対して、ビデオトラックは、キーフレームにしかシークできないので、
     * あらかじめ、与えられたチャプターの先頭位置を、CLOSESTなシーク可能位置に調整しておき、音声トラックもこれを参照することで、
     * 音声と映像のズレの最小化を図る。
     */
    fun adjustAndSetTrimmingRangeList(originalList: ITrimmingRangeKeeper, durationUs:Long):ITrimmingRangeKeeper {
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
        val newList = TrimmingRangeKeeper()
        val trackIdx = findTrackIdx(extractor, "video")
        extractor.selectTrack(trackIdx)
        for (range in originalList.list) {
            if (range.startUs == 0L) {
                newList.addRange(0, range.endUs)
            } else {
                extractor.seekTo(range.startUs, SEEK_TO_CLOSEST_SYNC)
                logger.debug { "actually sought to: ${extractor.sampleTime.formatAsUs()} (req: ${range.startUs.formatAsUs()})" }
                val sampleTime = extractor.sampleTime
                newList.addRange(sampleTime, range.endUs)
                newList.putSoughtPosition(range.startUs, extractor.sampleTime)
            }
        }
        // extractor.seekTo(0, SEEK_TO_CLOSEST_SYNC)
        // logger.debug { "sought to: ${extractor.sampleTime.toUsTimeString()} (req: 0)" }
        // ローカルファイルの場合は、seekTo(0) で先頭に戻れるが、
        // httpの場合に、微妙にずれるし、そのあとのデータ読込みに失敗するので、MediaExtractorを作り直す。
        extractor.seekTo(0L, SEEK_TO_CLOSEST_SYNC)

        newList.closeBy(durationUs)
        newList.limitDurationUs = originalList.limitDurationUs
        this.trimmingRangeList = newList
        logger.verbose("adjusted trimming ranges:")
        trimmingRangeList.list.forEach {
            logger.verbose {"  ${it.startUs.formatAsUs()} - ${it.endUs.formatAsUs()}"}
        }
        return newList
    }

    fun setTrimmingRangeList(list:ITrimmingRangeKeeper) {
        this.trimmingRangeList = list
        // オーディオトラックを先頭にシークしておく
        // 通常は、オープンしたら呼び出し位置は先頭を指しているのだが、特定の動画ファイルでは、sampleTimeが負値を持っていることがあった。
        // これが負値だと、TrimmingRangeList#positionStateがEOSと判断してしまい、コンバートに失敗していた。#17124
        extractor.seekTo(0L, SEEK_TO_CLOSEST_SYNC)
    }

    private var totalTime = 0L
    private var totalSkippedTime = 0L
    private val durationEstimator = DurationEstimator()
    val naturalDurationUs:Long get() = durationEstimator.estimatedDurationUs

    fun consume() : Boolean {
        if(isCancelled) {
            return false
        }
        var effected = false
        if(!eos) {
            logger.assert(trackIdx >= 0, "selectTrack() must be called before.")

            var startPositionUs = extractor.sampleTime
            // setTrimmingRangeList で seekTo(0) しているが、プリロールを持った（音声）トラックは、初回の sampleTime が負値になっていることがある。
            // coerceAtLeast(0L)とすると、EOS (-1) が検知できなくなるので、初回に限り、負値をゼロとして扱う。
            if (firstRead) {
                firstRead = false
                if(startPositionUs<0L) {
                    startPositionUs = 0L
                }
            }
            var currentPositionUs = startPositionUs
            val positionState = trimmingRangeList.positionState(startPositionUs)

            // シークする（＝デコーダーにデータを書き込まない）場合は、decoder.dequeueInputBuffer() を呼んではならない。
            // dequeueInputBuffer すると、dequeue済みバッファーとして予約され queueInputBuffer されるまで使えなくなる。
            // これを何度も繰り返す（=トリミング区間が増える）と、使えるバッファがなくなって、"no buffer in the decoder" が発生する。
            if (positionState == PositionState.OUT_OF_RANGE) {
                logger.debug("FOUND End Of Chapter: ${startPositionUs.formatAsUs()}")
                val position = trimmingRangeList.getNextValidPosition(startPositionUs)
                if (position != null) {
                    logger.chronos(msg = "extractor.seekTo", level = Log.INFO) {
                        try {
                            // 次の位置までシークする
                            extractor.seekTo(position.startUs, SEEK_TO_CLOSEST_SYNC)
                        } catch (e: Throwable) {
                            logger.error(e)
                        }
                    }
                    currentPositionUs = extractor.sampleTime    // カレント位置をシーク後の位置に更新
                    logger.info("SKIPPED from ${startPositionUs.formatAsUs()} to ${currentPositionUs.formatAsUs()} (req:${position.startUs.formatAsUs()} Δ=${abs(position.startUs - currentPositionUs).formatAsUs()})")
                    val skippedTime = currentPositionUs - startPositionUs
                    totalSkippedTime += skippedTime     // スキップした時間を積算
                }
            }

            val decoder = chainedDecoder.decoder
            val inputBufferIdx = decoder.dequeueInputBuffer(BaseCodec.TIMEOUT_IMMEDIATE)
            if (inputBufferIdx < 0) {
                logger.verbose("no buffer in the decoder.")
            } else {
                effected = true
                val idx = extractor.sampleTrackIndex
                if (idx < 0 || positionState == PositionState.END) {
                    logger.debug("found eos")
                    if (durationEstimator.estimatedDurationUs < trimmingRangeList.trimmedDurationUs - 2 * 1000 * 1000) {
                        // 読み込んだ時間が、期待する時間より2秒以上短ければ、たぶんなんかエラーが起きている。
                        logger.warn("conversion may be failed.")
                    }
                    eos = true
                    decoder.queueInputBuffer(inputBufferIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIdx) as ByteBuffer
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)

                    if (sampleSize > 0) {
                        logger.verbose { "READING: read $sampleSize bytes at ${TimeSpan.formatAutoM(currentPositionUs / 1000)}" }
                        totalTime = currentPositionUs - totalSkippedTime
                        durationEstimator.update(totalTime, sampleSize.toLong())
                        decoder.queueInputBuffer(inputBufferIdx, 0, sampleSize, totalTime, extractor.sampleFlags)
                    } else {
                        logger.error("zero byte read. it may be eos.")
                        // 本来は、↑の idx < 0 でチェックしているので、ここには入らないはず。
                        // もし入ってきたら、dequeue した inputBufferだけは解放しておく。
                        decoder.queueInputBuffer(inputBufferIdx, 0, 0, 0, 0)
                    }
                    extractor.advance()
                }
            }
        }
        return chainedDecoder.consume() || effected
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

