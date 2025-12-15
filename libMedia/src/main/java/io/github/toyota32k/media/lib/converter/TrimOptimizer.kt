package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import io.github.toyota32k.media.lib.converter.TrimmingRangeList.Companion.toRangeMsList
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.processor.contract.ICancellable
import io.github.toyota32k.media.lib.processor.contract.IMultiPhaseProgress
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.processor.optimizer.OptimizingProcessorPhase
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration

/**
 * コンバーターの All-In-One クラス
 * - 出力コーデックや編集パラメーターに応じて、Converter(再エンコードあり)/Splitter(再エンコードなし)を自動的に選択
 * - コンバート後に続けて、FastStart を実行可能
 */
class TrimOptimizer(
    private val applicationContext: Context,
    private val converterBuilder: Converter.Builder,
    private val fastStart:Boolean,
    private val removeFreeOnFastStart:Boolean,
    private val workDirectory:File?,
    private val forceConvert:Boolean,
    private val progressCallback:((IMultiPhaseProgress<OptimizingProcessorPhase>)->Unit)?): ICancellable {
    companion object {
        val logger = Converter.logger
    }
    class MultiPhaseProgress(override val phaseCount: Int) : IMultiPhaseProgress<OptimizingProcessorPhase> {
        override var phase = OptimizingProcessorPhase.CONVERTING
        override var total: Long = 0L
        override var current: Long = 0L
        override var remainingTime: Long = 0L

        fun updatePhase(phase: OptimizingProcessorPhase) = apply {
            this.phase = phase
            total = 0L
            current = 0L
            remainingTime = 0L
        }

        fun updateProgress(progress: IProgress) = apply {
            total = progress.total
            current = progress.current
            remainingTime = progress.remainingTime
        }
    }

    class Builder(val applicationContext:Context) {
        // region Builder Parameters

        private val mConverterBuilder: Converter.Builder = Converter.builder
        private var mFastStart:Boolean = false
        private var mRemoveFreeOnFastStart:Boolean = false
        private var mForceConvert = false
        private var mProgressHandler: ((IMultiPhaseProgress<OptimizingProcessorPhase>)->Unit)? = null
        private var mWorkDirectory: File? = null

        // endregion

        // region Optimizer specific instruction

        fun fastStart(flag:Boolean) = apply {
            mFastStart = flag
        }
        fun removeFreeOnFastStart(flag:Boolean) = apply {
            mRemoveFreeOnFastStart = flag
        }
        fun forceConvert(flag:Boolean) = apply {
            mForceConvert = flag
        }
        fun workDirectory(dir:File) = apply {
            mWorkDirectory = dir
        }

        // endregion

        // region Setter: I/O files

        /**
         * 入力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * @param IInputMediaFile
         */
        fun input(src:IInputMediaFile) = apply {
            mConverterBuilder.input(src)
        }

        /**
         * 入力ファイルを設定
         * @param File
         */
        fun input(path: File)
                = input(AndroidFile(path))

        /**
         * 入力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun input(uri: Uri, context: Context)
                = input(AndroidFile(uri, context))

        /**
         * 入力ファイルを設定
         * @param String URL (http/https)
         * @param Context
         */
        fun input(url: String, context: Context) = apply {
            if(!url.startsWith("http")) throw IllegalArgumentException("url must be http or https")
            input (HttpInputFile(context, url))
        }

        /**
         * 入力ファイルを設定
         * @param IHttpStreamSource
         * @param Context
         */
        fun input(source: IHttpStreamSource, context: Context)
                = input(HttpInputFile(context, source))

        /**
         * 出力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * ただし、inputと異なり、HttpFileは利用不可
         * @param IOutputMediaFile
         */
        fun output(dst:IOutputMediaFile) = apply {
            mConverterBuilder.output(dst)
        }

        /**
         * 出力ファイルを設定
         * @param File
         */
        fun output(path: File)
                = output(AndroidFile(path))

        /**
         * 出力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun output(uri: Uri, context: Context)
                = output(AndroidFile(uri, context))

        // endregion

        // region Setter: Strategies

        /**
         * VideoStrategyを設定
         */
        fun videoStrategy(s:IVideoStrategy) = apply {
            mConverterBuilder.videoStrategy(s)
        }

        /**
         * AudioStrategyを設定
         */
        fun audioStrategy(s: IAudioStrategy) = apply {
            mConverterBuilder.audioStrategy(s)
        }

        /**
         * Videoトラックを同じコーデックで再エンコードするとき、Profile/Levelを入力ファイルに合わせる
         */
        fun keepVideoProfile(flag:Boolean=true) = apply {
            mConverterBuilder.keepVideoProfile(flag)
        }
        /**
         * HDRが有効なVideoトラックを再エンコードするとき、出力コーデックで可能ならHDRを維持するProfileを選択する。
         */
        fun keepHDR(flag:Boolean=true) = apply {
            mConverterBuilder.keepHDR(flag)
        }

        /**
         * ソフトウェアデコーダーを優先するかどうか
         */
        fun preferSoftwareDecoder(flag:Boolean) = apply {
            mConverterBuilder.preferSoftwareDecoder(flag)
        }

        // endregion

        // region Setter: Trimming
        fun trimming(fn: TrimmingRangeList.Builder.()->Unit) = apply {
            mConverterBuilder.trimming(fn)
        }

        /**
         * 最大動画長を指定 (ms)
         * 0以下なら制限なし
         */
        fun limitDuration(durationMs:Long) = apply {
            mConverterBuilder.limitDuration(durationMs)
        }

        /**
         * 最大動画長を指定 (Duration)
         * nullまたは0以下なら制限なし
         */
        fun limitDuration(duration: Duration?)
                = limitDuration(duration?.inWholeMilliseconds ?: 0L)

        // endregion

        // region Setter: Conversion Behavior

        /**
         * 進捗報告ハンドラを設定
         */
        fun setProgressHandler(proc:(IMultiPhaseProgress<OptimizingProcessorPhase>)->Unit) = apply {
            mProgressHandler = proc
        }

        /**
         * エラー発生時に出力ファイルを削除するかどうか
         * デフォルトは true
         */
        fun deleteOutputOnError(flag:Boolean) = apply {
            mConverterBuilder.deleteOutputOnError(flag)
        }

        // endregion

        // region Setter: Conversion Instruction

        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation?) = apply {
            mConverterBuilder.rotate(rotation)
        }

        /**
         * コンテナフォーマットを指定
         * MPEG_4 以外はテストしていません。
         */
        fun containerFormat(format: ContainerFormat) = apply {
            mConverterBuilder.containerFormat(format)
        }

        fun brightness(brightness:Float?) = apply {
            mConverterBuilder.brightness(brightness)
        }
        fun crop(rect:Rect?) = apply {
            mConverterBuilder.crop(rect)
        }
        fun crop(x:Int, y:Int, cx:Int, cy:Int) = apply {
            mConverterBuilder.crop(x, y, cx, cy)
        }

        // endregion

        fun build() : TrimOptimizer {
            return TrimOptimizer(applicationContext, mConverterBuilder, mFastStart,  mRemoveFreeOnFastStart, mWorkDirectory, mForceConvert, mProgressHandler)
        }
    }

    private val progress by lazy { fastStartInfo?.progress ?: MultiPhaseProgress(1) }
    private var fastStartInfo: FastStartInfo? = null
    private var cancellable: ICancellable? = null
    private var cancelled:Boolean = false
    override fun cancel() {
        cancelled = true
        cancellable?.cancel()
    }

    private inner class FastStartInfo {
        val workFile:AndroidFile = File.createTempFile("amp", ".tmp", workDirectory?:applicationContext.cacheDir).toAndroidFile()
        val outputFile: AndroidFile = converterBuilder.properties.outPath as? AndroidFile ?: throw IllegalStateException("no output AndroidFile specified.")
        val deleteOutputOnError = converterBuilder.properties.deleteOutputOnError
        val progress = MultiPhaseProgress(2)
        init {
            converterBuilder
                .output(workFile)
                .deleteOutputOnError(true)
        }
        suspend fun fastStart(prevResult: IConvertResult): IConvertResult {
            if (!prevResult.succeeded) return prevResult

            progressCallback?.invoke(progress.updatePhase(OptimizingProcessorPhase.OPTIMIZING))
            return withContext(Dispatchers.IO) {
                val result = FastStart.process(workFile, outputFile, removeFreeOnFastStart) { p: IProgress ->
                    progressCallback?.invoke(progress.updateProgress(p))
                }
                if (result) return@withContext prevResult
                try {
                    outputFile.copyFrom(workFile)
                    prevResult
                } catch(e:Throwable) {
                    logger.error(e)
                    ConvertResult.error(e)
                }
            }
        }

        fun dispose() {
            workFile.safeDelete()
        }
    }

    /**
     * deleteOutputFileOnErrorオプションに従い、エラー発生時に出力ファイルを削除する。
     */
    private fun cleanOutputFileIfError(result: IConvertResult):IConvertResult {
        if (!result.succeeded) {
            val deleteOutputOnError = fastStartInfo?.deleteOutputOnError ?: converterBuilder.properties.deleteOutputOnError
            if (deleteOutputOnError) {
                val output = fastStartInfo?.outputFile ?: converterBuilder.properties.outPath
                output?.safeDelete()
            }
        }
        return result
    }

    /**
     * 変換を実行
     */
    suspend fun execute(): IConvertResult {
        if (fastStart) {
            fastStartInfo = FastStartInfo()
        }
        val result = try {
            val converted = if (forceConvert || converterBuilder.properties.checkReEncodingNecessity(true)) {
                convert()
            } else {
                extract()
            }
            fastStartInfo?.fastStart(converted) ?: converted
        } catch(e:Throwable) {
            ConvertResult.error(e)
        } finally {
            fastStartInfo?.dispose()
            fastStartInfo = null
        }
        return cleanOutputFileIfError(result)
    }

    private suspend fun convert(): IConvertResult {
        if (progressCallback != null) {
            progressCallback(progress.updatePhase(OptimizingProcessorPhase.CONVERTING))
            converterBuilder.setProgressHandler { p ->
                progressCallback(progress.updateProgress(p))
            }
        }
        val converter = converterBuilder.build()
        cancellable = converter
        if (cancelled) {
            return ConvertResult.cancelled
        }
        return try {
            converter.execute()
        } finally {
            cancellable?.cancel()
        }
    }

    private suspend fun extract(): IConvertResult {
        val splitter = Splitter
            .builder
            .deleteOutputOnError(converterBuilder.properties.deleteOutputOnError)
            .rotate(converterBuilder.properties.rotation)
            .apply {
                if (progressCallback!=null) {
                    progressCallback(progress.updatePhase(OptimizingProcessorPhase.CONVERTING))
                    setProgressHandler { p->
                        progressCallback(progress.updateProgress(p))
                    }
                }
            }
            .build()
        cancellable = splitter
        if (cancelled) {
            return ConvertResult.cancelled
        }

        val trimmingRangeList = converterBuilder.properties.trimmingRangeListBuilder.build()
        val inPath = converterBuilder.properties.inPath ?: throw IllegalStateException("no input file specified.")
        val outPath = converterBuilder.properties.outPath ?: throw IllegalStateException("no output file specified.")
        return try {
            splitter.trim(inPath, outPath, trimmingRangeList.list.toRangeMsList())
        } finally {
            cancellable = null
        }
    }
}