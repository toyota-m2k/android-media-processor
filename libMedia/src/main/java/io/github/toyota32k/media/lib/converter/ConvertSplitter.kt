package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import io.github.toyota32k.media.lib.converter.Converter.Companion.analyze
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.getDuration
import io.github.toyota32k.media.lib.report.Summary
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.utils.UtSortedList
import io.github.toyota32k.utils.UtSorter
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

fun interface IOutputFileSelector {
    fun selectOutputFile(index:Int, totalCount:Int, position:Long): IOutputMediaFile?
}

class ConvertSplitter(
    private val converterFactory: Converter.Factory,
//    private val chopList: List<Long>,
    private val rangeList: List<RangeMs>,   // trimming
    private val progressHandler: ((IProgress)->Unit)? = null
) {
    class Factory {
        val converterFactory = Converter.Factory()
//        val mChopList = UtSortedList(UtSorter.ActionOnDuplicate.REPLACE) { a: Long, b: Long -> a.compareTo(b) }
        var mOnProgress: ((IProgress)->Unit)? = null
        val mRangeList = mutableListOf<RangeMs>()

//        fun chopAt(vararg positionsMs:Long) = apply {
//            for (position in positionsMs) {
//                mChopList.add(position)
//            }
//        }
        fun setProgressHandler(handler: (IProgress)->Unit) = apply {
            mOnProgress = handler
        }

        fun addTrimmingRange(range: RangeMs) = apply {
            mRangeList.add(range)
        }

        fun addTrimmingRanges(vararg ranges: RangeMs) = apply {
            for (range in ranges) {
                mRangeList.add(range)
            }
        }

        // region Converter.Factory
//        /**
//         * 入力ファイルを設定（必須）
//         * 通常は、適当な型の引数をとるバリエーションを利用する。
//         * @param IInputMediaFile
//         */
//        fun input(src:IInputMediaFile) = apply {
//           converterFactory.input(src)
//        }
//
//        /**
//         * 入力ファイルを設定
//         * @param File
//         */
//        fun input(path: File)
//                = input(AndroidFile(path))
//
//        /**
//         * 入力ファイルを設定
//         * @param Uri
//         * @param Context
//         */
//        fun input(uri: Uri, context: Context)
//                = input(AndroidFile(uri, context))
//
//        /**
//         * 入力ファイルを設定
//         * @param String URL (http/https)
//         * @param Context
//         */
//        fun input(url: String, context: Context) = apply {
//            if(!url.startsWith("http")) throw IllegalArgumentException("url must be http or https")
//            input (HttpInputFile(context, url))
//        }
//
//        /**
//         * 入力ファイルを設定
//         * @param IHttpStreamSource
//         * @param Context
//         */
//        fun input(source: IHttpStreamSource, context: Context)
//                = input(HttpInputFile(context, source))


        /**
         * VideoStrategyを設定
         */
        fun videoStrategy(s:IVideoStrategy) = apply {
            converterFactory.videoStrategy(s)
        }

        /**
         * AudioStrategyを設定
         */
        fun audioStrategy(s: IAudioStrategy) = apply {
            converterFactory.audioStrategy(s)
        }

        /**
         * Videoトラックを同じコーデックで再エンコードするとき、Profile/Levelを入力ファイルに合わせる
         */
        fun keepVideoProfile(flag:Boolean=true) = apply {
            converterFactory.keepVideoProfile(flag)
        }
        /**
         * HDRが有効なVideoトラックを再エンコードするとき、出力コーデックで可能ならHDRを維持するProfileを選択する。
         */
        fun keepHDR(flag:Boolean=true) = apply {
            converterFactory.keepHDR(flag)
        }
        /**
         * エラー発生時に出力ファイルを削除するかどうか
         * デフォルトは true
         */
        fun deleteOutputOnError(flag:Boolean) = apply {
            converterFactory.deleteOutputOnError(flag)
        }

        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation) = apply {
            converterFactory.rotate(rotation)
        }

        /**
         * コンテナフォーマットを指定
         * MPEG_4 以外はテストしていません。
         */
        fun containerFormat(format: ContainerFormat) = apply {
            converterFactory.containerFormat(format)
        }

        /**
         * ソフトウェアデコーダーを優先するかどうか
         */
        fun preferSoftwareDecoder(flag:Boolean) = apply {
            converterFactory.preferSoftwareDecoder(flag)
        }

        fun brightness(brightness:Float) = apply {
            converterFactory.brightness(brightness)
        }
        fun crop(rect:Rect) = apply {
            converterFactory.crop(rect)
        }
        fun crop(x:Int, y:Int, cx:Int, cy:Int) = apply {
            converterFactory.crop(x,y,cx,cy)
        }

        // endregion

        fun build(): ConvertSplitter {
            return ConvertSplitter(converterFactory, mRangeList, mOnProgress)
        }
    }

    companion object {
        private fun prepareTrimmingRanges(rangeList: List<RangeMs>, startMs: Long, endMs: Long) : List<RangeMs>{
            return mutableListOf<RangeMs>().also { ranges ->
                if (rangeList.isEmpty()) {
                    ranges.add(RangeMs(startMs, endMs))
                } else {
                    for (range in rangeList) {
                        if (range.endMs <= startMs || endMs <= range.startMs) {
                            continue
                        }
                        val start = max(startMs, range.startMs)
                        val end = min(endMs, range.endMs)
                        ranges.add(RangeMs(start, end))
                    }
                }
            }
        }

        fun prepareTrimmingRangesList(rangeList: List<RangeMs>,positionMsList: List<Long>): List<List<RangeMs>> {
            val result = mutableListOf<List<RangeMs>>()
            val count = positionMsList.size
            var prev = 0L
            for (i in 0..count) {
                val endMs = if (i < count) positionMsList[i] else Long.MAX_VALUE
                val ranges = prepareTrimmingRanges(rangeList,prev, endMs)
                prev = endMs
                if (ranges.isNotEmpty()) {
                    result.add(ranges)
                }
            }
            return result
        }
        fun RangeMs.lengthMs(durationMs:Long):Long {
            return (if (endMs !in 1..durationMs) durationMs else endMs) - startMs
        }
    }

    class Result {
        val results: MutableList<ConvertResult> =  mutableListOf()
        fun add(result: ConvertResult) {
            results.add(result)
        }
    }

    class ConvertProgress(override val total: Long) : IProgress {
        override val current: Long // in us
            get () = previousDuration + currentInPart
        val initialTIme = System.currentTimeMillis()
        val elapsedTime: Long
            get() = System.currentTimeMillis() - initialTIme
        override val remainingTime: Long // in ms
            get() {
                return if (current>3000) (elapsedTime.toFloat() * ((total.toFloat() / current.toFloat())-1)).roundToLong() else 0L
            }
        var previousDuration:Long = 0L
        fun updateDuration(duration:Long) {
            previousDuration+=duration
        }
        var currentInPart:Long = 0L
        fun updateCurrent(position: Long) {
            currentInPart = position
        }
    }

    suspend fun chop(inputFile: IInputMediaFile, positionMsList: List<Long>, outputFileSelector: IOutputFileSelector): Result {
        if (positionMsList.isEmpty()) throw IllegalArgumentException("chopList is empty")
        val duration = inputFile.openMetadataRetriever().useObj { it.getDuration() } ?: throw IllegalStateException("cannot retrieve duration")

        val result = Result()
        val trimmingRangesList = prepareTrimmingRangesList(rangeList,positionMsList)
        if (trimmingRangesList.isEmpty()) {
            return result
        }
        val totalRangeMs = trimmingRangesList.fold(0L) { acc, ranges ->
            acc + ranges.fold(0L) { acc2, range ->
                acc2 + range.lengthMs(duration)
            }
        }
        var index = 0
        val convProgress = ConvertProgress(totalRangeMs*1000)
        for (ranges in trimmingRangesList) {
            val output = outputFileSelector.selectOutputFile(index, trimmingRangesList.size, ranges[0].startMs)
            if (output==null) {
                result.add(ConvertResult.cancelled)
                break
            }
            index++
            val converter = converterFactory
                .input(inputFile)
                .output(output)
                .resetTrimmingRangeList()
                .addTrimmingRanges(*ranges.toTypedArray())
                .apply {
                    if (progressHandler!=null) {
                        setProgressHandler { progress->
                            convProgress.updateDuration(progress.current)
                            progressHandler(convProgress)
                        }
                    }
                }
                .build()
            val convertResult = converter.execute()
            result.add (convertResult)
            if (convertResult.cancelled) {
                break
            }
            convProgress.updateDuration(ranges.fold(0L) { acc, range ->
                acc + (range.endMs - range.startMs)
            })
        }
        return result
    }
}