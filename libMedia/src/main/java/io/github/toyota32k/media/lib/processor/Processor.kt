package io.github.toyota32k.media.lib.processor

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.legacy.converter.ActualSoughtMapImpl
import io.github.toyota32k.media.lib.types.ConvertResult
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.legacy.converter.IActualSoughtMap
import io.github.toyota32k.media.lib.types.IConvertResult
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.types.Rotation
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.processor.contract.ICancellable
import io.github.toyota32k.media.lib.processor.contract.IProcessorOptions
import io.github.toyota32k.media.lib.processor.contract.IFormattable
import io.github.toyota32k.media.lib.processor.contract.format3digits
import io.github.toyota32k.media.lib.processor.optimizer.OptimizerOptions
import io.github.toyota32k.media.lib.processor.optimizer.Optimizer
import io.github.toyota32k.media.lib.processor.contract.ITrack
import io.github.toyota32k.media.lib.processor.track.SyncMuxer
import io.github.toyota32k.media.lib.processor.track.TrackSelector
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.internals.surface.RenderOption
import io.github.toyota32k.media.lib.types.RangeUs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.outlineRangeUs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.totalLengthUs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.us2ms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

/**
 * 第４世代 動画ファイルプロセッサークラス
 */
class Processor(
    val containerFormat: ContainerFormat = ContainerFormat.MPEG_4,
    val bufferSize:Int = DEFAULT_BUFFER_SIZE,
) : ICancellable, IFormattable {
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

        fun initialize(totalUs:Long, video:Boolean, audio:Boolean) {
            total = totalUs.us2ms()
            videoLength = 0L
            audioLength = 0L
            videoAvailable = video
            audioAvailable = audio
        }

        private fun updateRemainingTime() {
            if (percentage>10) {
                remainingTime = (System.currentTimeMillis() - startTick) * (100 - percentage) / percentage
            }
        }

        fun updateVideoUs(videoUs:Long) {
            val prev = current
            videoLength = videoUs.us2ms()
            if (prev!=current && total>0 && total!=Long.MAX_VALUE) {
                updateRemainingTime()
                onProgress?.invoke(this)
            }
        }

        fun updateAudioUs(audioUs:Long) {
            val prev = current
            audioLength = audioUs.us2ms()
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
    private fun extractRange(videoTrack: ITrack, audioTrack: ITrack, rangeUs: RangeUs, actualSoughtMap: ActualSoughtMapImpl) {
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
        actualSoughtMap.addPosition(rangeUs.startUs, posVideo) // [rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
    }

    /**
     *
     */
    data class Result(
        val outputFile: IOutputMediaFile?,
        val requestedRangeUs: RangeUs,
        val actualSoughtMap: IActualSoughtMap?,
        val report: Report) {
        constructor(src:Result,
            outputFile: IOutputMediaFile? = src.outputFile,
            requestedRangeUs: RangeUs = src.requestedRangeUs,
            actualSoughtMap: IActualSoughtMap? = src.actualSoughtMap,
            report: Report = src.report) : this(outputFile, requestedRangeUs, actualSoughtMap, report)

        fun toConvertResult(): IConvertResult {
            return ProcessorResult.success(this)
        }
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
    fun process(inPath: IInputMediaFile, outPath: IOutputMediaFile, rangesUs:List<RangeUs>, limitDurationUs:Long, rotation:Rotation?, renderOption:RenderOption?, videoStrategy: IVideoStrategy, audioStrategy: IAudioStrategy, onProgress:((IProgress)->Unit)?):Result {
        progress = ProgressHandler(onProgress)
        val actualSoughtMapImpl = ActualSoughtMapImpl()
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
            for (rangeUs in rangesUs) {
                if (isCancelled) throw CancellationException()
                extractRange(videoTrack, audioTrack, rangeUs, actualSoughtMapImpl)
            }
            // ファイナライズ
            videoTrack.finalize()
            audioTrack.finalize()
            muxer.stop()
            report.updateOutputFileInfo(outPath.getLength(), muxer.naturalDurationUs)
            report.muxerDurationUs = muxer.naturalDurationUs
            report.sourceDurationUs = totalUs
            report.end()

            return Result(outPath, rangesUs.outlineRangeUs(report.sourceDurationUs), actualSoughtMapImpl, report)
        }
    }

    /**
     * optionsにしたがって変換を実行
     */
    fun process(options: IProcessorOptions):Result {
        return process(options.inPath, options.outPath, options.rangesUs, options.limitDurationUs, options.rotation, options.renderOption, options.videoStrategy, options.audioStrategy, options.onProgress)
    }

    /**
     * Dispatchers.IO で process()を実行
     */
    suspend fun execute(processorOptions: IProcessorOptions, deleteOutputOnError:Boolean=true): IConvertResult {
        return withContext(Dispatchers.IO) {
            try {
                process(processorOptions).toConvertResult()
            } catch (e: Throwable) {
                if (deleteOutputOnError) {
                    processorOptions.outPath.safeDelete()
                }
                ConvertResult.error(e)
            }
        }
    }

    /**
     * process()の後、fast start を実行
     */
    suspend fun execute(processorOptions:IProcessorOptions, optimizeOption: OptimizerOptions, deleteOutputOnError:Boolean=true): IConvertResult {
        return withContext(Dispatchers.IO) {
            try {
                Optimizer.optimize(this@Processor, processorOptions, optimizeOption).toConvertResult()
            } catch (e: Throwable) {
                if (deleteOutputOnError) {
                    processorOptions.outPath.safeDelete()
                }
                ConvertResult.error(e)
            }
        }
    }

    // endregion
}


