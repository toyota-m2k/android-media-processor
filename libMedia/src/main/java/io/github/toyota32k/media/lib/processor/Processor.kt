package io.github.toyota32k.media.lib.processor

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.internals.surface.RenderOption
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.legacy.converter.dump
import io.github.toyota32k.media.lib.processor.contract.IActualSoughtMap
import io.github.toyota32k.media.lib.processor.contract.ICancellable
import io.github.toyota32k.media.lib.processor.contract.IConvertResult
import io.github.toyota32k.media.lib.processor.contract.IFormattable
import io.github.toyota32k.media.lib.processor.contract.IProcessor
import io.github.toyota32k.media.lib.processor.contract.IProcessorOptions
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.processor.contract.ISoughtMap
import io.github.toyota32k.media.lib.processor.contract.ITrack
import io.github.toyota32k.media.lib.processor.contract.format3digits
import io.github.toyota32k.media.lib.processor.optimizer.Optimizer
import io.github.toyota32k.media.lib.processor.optimizer.OptimizerOptions
import io.github.toyota32k.media.lib.processor.track.SyncMuxer
import io.github.toyota32k.media.lib.processor.track.TrackSelector
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.types.RangeUs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.totalLengthUs
import io.github.toyota32k.media.lib.types.Rotation
import io.github.toyota32k.media.lib.types.SoughtMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

/**
 * 第４世代 動画ファイルプロセッサークラス
 */
class Processor(
    val containerFormat: ContainerFormat = ContainerFormat.MPEG_4,
    val bufferSize:Int = DEFAULT_BUFFER_SIZE,
) : IProcessor, ICancellable, IFormattable {
    companion object {
        val logger = UtLog("PRC", Converter.logger, this::class.java)
        const val DEFAULT_BUFFER_SIZE:Int = 8 * 1024 * 1024     // 8MB ... 1MB だと extractor.readSampleData() で InvalidArgumentException が発生
//        val DEFAULT:Processor get() = Processor()
    }

    override fun format(sb: StringBuilder): StringBuilder {
        return sb
            .appendLine("## Processor")
            .appendLine("container format: $containerFormat")
            .appendLine("buffer size: ${bufferSize.format3digits()}")
    }

    class Builder {
//        companion object {
//            fun fromInstance(instance:Processor) = Builder().apply {
//                mContainerFormat = instance.containerFormat
//                mBufferSize = instance.bufferSize
//            }
//        }
        private var mContainerFormat: ContainerFormat = ContainerFormat.MPEG_4
        private var mBufferSize:Int = DEFAULT_BUFFER_SIZE
//        private var mOnProgress : ((IProgress)->Unit)? = null

        fun containerFormat(containerFormat: ContainerFormat) = apply {
            mContainerFormat = containerFormat
        }
        fun bufferSize(sizeInBytes:Int) = apply {
            mBufferSize = sizeInBytes.coerceAtLeast(DEFAULT_BUFFER_SIZE)
        }
//        fun onProgress(progress:((IProgress)->Unit)?) = apply {
//            mOnProgress = progress
//        }
        fun build():Processor {
            return Processor(
                containerFormat = mContainerFormat,
                bufferSize = mBufferSize,
//                onProgress = mOnProgress,
            )
        }

    }


    // region Utility Classes

    /**
     * Closeable をまとめて解放できるようにするクラス
     */
    private class Closeables : Closeable {
        private val list = mutableListOf<Closeable>()
        fun <T: Closeable> add(c:T): T {
            list.add(c)
            return c
        }
        override fun close() {
            list.forEach { it.close() }
        }
    }

    // endregion

    // region Progress

    /**
     * 進捗報告用ハンドラクラス
     */
    private class ProgressHandler(private val onProgress:((IProgress)->Unit)?) : IProgress {
        private val startTick = System.currentTimeMillis()
        private var videoLength = 0L        // ms
        private var audioLength = 0L        // ms

        private var videoAvailable = false
        private var audioAvailable = false

        override var total: Long = 0L       // ms
            private set
        override val current: Long
            get() = min(if(videoAvailable) videoLength else Long.MAX_VALUE, if(audioAvailable) audioLength else Long.MAX_VALUE)
        override var remainingTime: Long = -1L
            private set
        override val valueUnit: IProgress.ValueUnit = IProgress.ValueUnit.US

        fun initialize(totalUs:Long, video:Boolean, audio:Boolean) {
            total = totalUs
            videoLength = 0L
            audioLength = 0L
            videoAvailable = video
            audioAvailable = audio
        }

        private fun updateRemainingTime() {
            val elapsedTime = System.currentTimeMillis() - startTick
            if (elapsedTime>1000 && percentage>1) {
                if (percentage<5) {
                    // 最初のうちは誤差が大きいので、未加工で表示
                    remainingTime = elapsedTime * (100 - percentage) / percentage
                } else {
                    // ある程度進めば、安定するはずなので、進捗が後戻りしないようにする。
                    remainingTime = min(elapsedTime * (100 - percentage) / percentage, remainingTime)
                }
            }
        }

        fun updateVideoUs(videoUs:Long) {
            val prev = current
            videoLength = max(videoLength, videoUs)
            videoLength += videoUs
            if (prev!=current && total>0 && total!=Long.MAX_VALUE) {
                updateRemainingTime()
                onProgress?.invoke(this)
            }
        }

        fun updateAudioUs(audioUs:Long) {
            val prev = current
            audioLength = max(audioLength, audioUs)
            audioLength += audioUs
            if (prev!=current) {
                updateRemainingTime()
                onProgress?.invoke(this)
            }
        }
    }

    // 進捗報告
    private lateinit var progress: ProgressHandler

    // endregion

    // region ICancellable

    // キャンセル
    private var isCancelled: Boolean = false
    override fun cancel() {
        isCancelled = true
    }

    // endregion

    // region Private Implementation

    /**
     * 指定範囲をextractorから読み出してmuxerに書き込む
     */
    private fun extractRange(videoTrack: ITrack, audioTrack: ITrack, rangeUs: RangeUs, soughtMap: SoughtMap) {
        val posVideo = videoTrack.startRange(rangeUs.startUs)

        audioTrack.startRange(if(posVideo>=0) posVideo else rangeUs.startUs)
        while (!videoTrack.done || !audioTrack.done) {
            if (isCancelled) {
                throw CancellationException()
            }
            if (!videoTrack.done && (audioTrack.done || videoTrack.presentationTimeUs <= audioTrack.presentationTimeUs)) {
                // video track を処理
                videoTrack.readAndWrite(rangeUs)
                progress.updateVideoUs(videoTrack.presentationTimeUs)
            } else if (!audioTrack.done) {
                // audio track を処理
                audioTrack.readAndWrite(rangeUs)
                progress.updateAudioUs(audioTrack.presentationTimeUs)
            }
        }
        videoTrack.endRange()
        audioTrack.endRange()
        soughtMap.put(rangeUs.startUs, posVideo, videoTrack.currentRangeStartPresentationTimeUs) // [rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
    }

    /**
     * IProcessorResult の実装クラス
     */
    data class Result(
        override val inputFile: IInputMediaFile,
        override val outputFile: IOutputMediaFile,
        override val soughtMap: ISoughtMap,
        override val report: Report,
    ) : IConvertResult {
        constructor(src:Result,
            inputFile: IInputMediaFile = src.inputFile,
            outputFile: IOutputMediaFile = src.outputFile,
            soughtMap: ISoughtMap = src.soughtMap,
            report: Report = src.report) : this(inputFile, outputFile, soughtMap, report)

//        override val requestedRangeMs: RangeMs
//            get() = requestedRangeUs.toRangeMs()
        @Deprecated("use soughtMap")
        override val actualSoughtMap: IActualSoughtMap? = null

        // Resultクラスはコンバート成功の場合にしか使わない
        override val succeeded: Boolean = true
        override val exception: Throwable? = null
        override val errorMessage: String? = null

        override fun toString(): String {
            return dump()
        }
    }
    data class ErrorResult(
        override val inputFile: IInputMediaFile?,
        override val exception: Throwable?,
        override val errorMessage: String? = null) : IConvertResult {
        override val outputFile: IOutputMediaFile? = null
        override val soughtMap: ISoughtMap? = null
        @Deprecated("use soughtMap")
        override val actualSoughtMap: IActualSoughtMap? = null
        override val report: Report? = null
        override val succeeded: Boolean = false
    }

    // endregion

    // region Public Function

    /**
     * 指定パラメータ（トランスコード、トリミング、切り抜きなど）にしたがって変換を実行
     *
     * @param inPath 入力ファイル
     * @param outPath 出力ファイル
     * @param rangesUs 出力する範囲のリスト
     * @param limitDurationUs 最大動画長 (us) / <=0 または、Long.MAX_VALUE を指定すると、rangesUsの最後までコンバートする。
     * @param rotation 回転
     * @param renderOption RenderOption
     */
    fun process(inPath: IInputMediaFile, outPath: IOutputMediaFile, rangesUs:List<RangeUs>, limitDurationUs:Long, rotation:Rotation?, renderOption:RenderOption?, videoStrategy: IVideoStrategy, audioStrategy: IAudioStrategy, onProgress:((IProgress)->Unit)?): IConvertResult {
        progress = ProgressHandler(onProgress)

        val report = Report().apply {
            start()
            updateVideoStrategyName(videoStrategy.name)
            updateAudioStrategyName(audioStrategy.name)
        }
        isCancelled = false
        Closeables().use { closer ->
            // Extractorの準備
            val trackSelector = TrackSelector(inPath, limitDurationUs, report, bufferSize, videoStrategy, audioStrategy).apply { closer.add(this) }
            val videoTrack = trackSelector.openVideoTrack(renderOption?:RenderOption.DEFAULT).apply { closer.add(this) }
            val audioTrack = trackSelector.openAudioTrack().apply { closer.add(this) }
            if (!videoTrack.isAvailable && !audioTrack.isAvailable) throw IllegalStateException("no track available")

            // Muxerの準備
            val muxer = SyncMuxer(outPath, containerFormat, videoTrack.isAvailable, audioTrack.isAvailable).apply {
                setup(trackSelector.inputMetaData, rotation)
                closer.add(this)
            }
            videoTrack.setup(muxer)
            audioTrack.setup(muxer)

            // Progress情報を初期化
            val totalUs = rangesUs.totalLengthUs(trackSelector.inputMetaData.durationUs ?: Long.MAX_VALUE)
            progress.initialize(if (limitDurationUs>0) min(limitDurationUs, totalUs) else totalUs, videoTrack.isAvailable, audioTrack.isAvailable)

            // Extractorから要求された範囲を読み上げてMuxerへ書き込む
            val durationUs = trackSelector.inputMetaData.durationUs ?:Long.MAX_VALUE
            val soughtMap = SoughtMap(durationUs, rangesUs)
            for (rangeUs in rangesUs) {
                if (isCancelled) throw CancellationException()
                extractRange(videoTrack, audioTrack, rangeUs, soughtMap)
            }
            // ファイナライズ
            videoTrack.finalize()
            audioTrack.finalize()
            muxer.stop()
            report.updateOutputFileInfo(outPath.getLength(), muxer.naturalDurationUs)
            report.muxerDurationUs = muxer.naturalDurationUs
            report.sourceDurationUs = totalUs
            report.end()

            return Result(inPath, outPath, soughtMap, report)
        }
    }

    /**
     * optionsにしたがって変換を実行
     */
    override fun process(options: IProcessorOptions): IConvertResult {
        return process(options.inPath, options.outPath, options.rangesUs, options.limitDurationUs, options.rotation, options.renderOption, options.videoStrategy, options.audioStrategy, options.onProgress)
    }

    /**
     * Dispatchers.IO で process()を実行
     */
    suspend fun execute(processorOptions: IProcessorOptions, deleteOutputOnError:Boolean=true): IConvertResult {
        return withContext(Dispatchers.IO) {
            try {
                process(processorOptions)
            } catch (e: Throwable) {
                if (deleteOutputOnError) {
                    processorOptions.outPath.safeDelete()
                }
                ErrorResult(processorOptions.inPath, e)
            }
        }
    }

    /**
     * process()の後、fast start を実行
     */
    suspend fun execute(processorOptions:IProcessorOptions, optimizeOption: OptimizerOptions?, deleteOutputOnError:Boolean=true): IConvertResult {
        if (optimizeOption==null) {
            // 最適化しない
            return execute(processorOptions, deleteOutputOnError)
        }
        return withContext(Dispatchers.IO) {
            try {
                Optimizer.optimize(this@Processor, processorOptions, optimizeOption)
            } catch (e: Throwable) {
                if (deleteOutputOnError) {
                    processorOptions.outPath.safeDelete()
                }
                ErrorResult(processorOptions.inPath, e)
            }
        }
    }

    // endregion
}


