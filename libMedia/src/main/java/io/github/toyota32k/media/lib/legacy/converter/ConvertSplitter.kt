package io.github.toyota32k.media.lib.legacy.converter

import android.graphics.Rect
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.types.Rotation
import io.github.toyota32k.media.lib.legacy.converter.TrimmingRangeList.Companion.toRangeMsList
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.getDuration
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.types.RangeUs.Companion.ms2us
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.types.RangeMs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class ConvertSplitter(
    private val converterFactory: Converter.Builder,
    private val abortOnError: Boolean,
    private val progressHandler: ((IProgress)->Unit)? = null
) : IMultiChopper, IMultiPartitioner {
    class Builder {
        private val mConverterFactory = Converter.Builder()
        private var mOnProgress: ((IProgress)->Unit)? = null
        private var mAbortOnError = false

        fun setProgressHandler(handler: (IProgress)->Unit) = apply {
            mOnProgress = handler
        }

        fun trimming(fn: TrimmingRangeList.Builder.()->Unit) = apply {
            mConverterFactory.trimming(fn)
        }

        /**
         * VideoStrategyを設定
         */
        fun videoStrategy(s:IVideoStrategy) = apply {
            mConverterFactory.videoStrategy(s)
        }

        /**
         * AudioStrategyを設定
         */
        fun audioStrategy(s: IAudioStrategy) = apply {
            mConverterFactory.audioStrategy(s)
        }

        /**
         * Videoトラックを同じコーデックで再エンコードするとき、Profile/Levelを入力ファイルに合わせる
         */
        fun keepVideoProfile(flag:Boolean=true) = apply {
            mConverterFactory.keepVideoProfile(flag)
        }
        /**
         * HDRが有効なVideoトラックを再エンコードするとき、出力コーデックで可能ならHDRを維持するProfileを選択する。
         */
        fun keepHDR(flag:Boolean=true) = apply {
            mConverterFactory.keepHDR(flag)
        }
        /**
         * エラー発生時に出力ファイルを削除するかどうか
         * デフォルトは true
         */
        fun deleteOutputOnError(flag:Boolean) = apply {
            mConverterFactory.deleteOutputOnError(flag)
        }

        /**
         * エラー発生時に残りのファイル出力を中止するか？
         * デフォルト： false
         */
        fun abortOnError(flag: Boolean) = apply {
            mAbortOnError = flag
        }


        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation) = apply {
            mConverterFactory.rotate(rotation)
        }

        /**
         * コンテナフォーマットを指定
         * MPEG_4 以外はテストしていません。
         */
        fun containerFormat(format: ContainerFormat) = apply {
            mConverterFactory.containerFormat(format)
        }

        /**
         * ソフトウェアデコーダーを優先するかどうか
         */
        fun preferSoftwareDecoder(flag:Boolean) = apply {
            mConverterFactory.preferSoftwareDecoder(flag)
        }

        fun brightness(brightness:Float) = apply {
            mConverterFactory.brightness(brightness)
        }
        fun crop(rect:Rect) = apply {
            mConverterFactory.crop(rect)
        }
        fun crop(x:Int, y:Int, cx:Int, cy:Int) = apply {
            mConverterFactory.crop(x,y,cx,cy)
        }

        // endregion

        fun build(): ConvertSplitter {
            return ConvertSplitter(mConverterFactory, mAbortOnError, mOnProgress)
        }
    }

    companion object {
        val builder: Builder get() = Builder()

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

        fun prepareTrimmingRangesList(rangeList: List<RangeMs>, positionMsList: List<Long>): List<List<RangeMs>> {
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

        fun positionMsListToRangeMsList(positionMsList: List<Long>, duration:Long): List<RangeMs> {
            val result = mutableListOf<RangeMs>()
            var prev = 0L
            for (positionMs in positionMsList) {
                result.add(RangeMs(prev, positionMs))
                prev = positionMs
            }
            result.add(RangeMs(prev, duration))
            return result
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

    private var converter: Converter? = null

    /**
     * 分割位置を指定して複数ファイルに分割
     *
     * @param inputFile         ソース動画ファイル
     * @param positionMsList    分割位置(ms)のリスト
     * @param outputFileSelector 出力ファイル選択コールバック
     * @return IMultiSplitResult
     */
    override suspend fun chop(inputFile: IInputMediaFile, positionMsList: List<Long>, outputFileSelector: IOutputFileSelector): IMultiSplitResult {
        if (positionMsList.isEmpty()) throw IllegalArgumentException("chopList is empty")
        val duration = inputFile.openMetadataRetriever().useObj { it.getDuration() } ?: throw IllegalStateException("cannot retrieve duration")

        val result = Splitter.MultiResult()
        val baseTrimmingRangeList = converterFactory.properties.tryGetTrimmingRangeList() ?: return result
        val rangeMsList = positionMsListToRangeMsList(positionMsList, duration)
        if (rangeMsList.isEmpty()) {
            return result
        }
        val totalLengthMs = rangeMsList.fold(0L) { acc, range ->
            acc + range.lengthMs(duration)
        }
        if (!outputFileSelector.initialize(rangeMsList)) {
            return result.cancel()
        }
        var index = 0
        val convProgress = ConvertProgress(totalLengthMs.ms2us())
        for (range in rangeMsList) {
            val output = outputFileSelector.selectOutputFile(index, range.startMs)
            if (output == null) {
                result.cancel()
                break
            }
            index++
            val converter = converterFactory
                .input(inputFile)
                .output(output)
                .trimming {
                    setRangesUs(baseTrimmingRangeList.list)
                    startFromMs(range.startMs)
                    endAtMs(range.endMs)
                }
                .apply {
                    if (progressHandler!=null) {
                        setProgressHandler { progress->
                            convProgress.updateDuration(progress.current)
                            progressHandler(convProgress)
                        }
                    }
                }
                .build()

            this.converter = converter
            if (cancelled) {
                result.cancel()
                break
            }
            val convertResult = converter.execute()
            result.add (convertResult)
            if (convertResult.cancelled) {
                break
            } else if (abortOnError && convertResult.hasError) {
                break
            }
            convProgress.updateDuration(range.lengthMs(duration))
        }
        outputFileSelector.terminate()
        return result
    }

    /**
     * チャプター毎に動画を切り出す
     * @param inputFile         ソース動画ファイル
     * @param outputFileSelector 出力ファイル選択コールバック
     * @return IMultiSplitResult
     */
    override suspend fun chop(inputFile: IInputMediaFile, outputFileSelector: IOutputFileSelector): IMultiSplitResult {
        val result = Splitter.MultiResult()
        val rangeList = converterFactory.properties.tryGetTrimmingRangeList()?.list?.toRangeMsList()
        if (rangeList == null || rangeList.isEmpty()) {
            throw IllegalStateException("no trimming range list")
        }
        val duration = inputFile.openMetadataRetriever().useObj { it.getDuration() } ?: throw IllegalStateException("cannot retrieve duration")

        if (!outputFileSelector.initialize(rangeList)) {
            return result.cancel()
        }

        val totalRangeMs = rangeList.fold(0L) { acc, range ->
            acc + range.lengthMs(duration)
        }
        var index = 0
        val convProgress = ConvertProgress(totalRangeMs.ms2us())
        for (range in rangeList) {
            val output = outputFileSelector.selectOutputFile(index, range.startMs)
            if (output==null) {
                result.cancel()
                break
            }
            index++
            val converter = converterFactory
                .input(inputFile)
                .output(output)
                .trimming {
                    reset()
                    addRangeMs(range)
                }
                .apply {
                    if (progressHandler!=null) {
                        setProgressHandler { progress->
                            convProgress.updateDuration(progress.current)
                            progressHandler(convProgress)
                        }
                    }
                }
                .build()
            this.converter = converter
            if (cancelled) {
                result.cancel()
                break
            }
            val convertResult = converter.execute()
            result.add (convertResult)
            if (convertResult.cancelled) {
                break
            } else if (abortOnError && convertResult.hasError) {
                break
            }
            convProgress.updateDuration(range.lengthMs(duration))
        }
        outputFileSelector.terminate()
        return result
    }

    var cancelled = false
    override fun cancel() {
        cancelled = true
        converter?.cancel()
    }
}