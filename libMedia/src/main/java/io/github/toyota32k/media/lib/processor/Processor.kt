package io.github.toyota32k.media.lib.processor

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.internals.surface.RenderOption
import io.github.toyota32k.media.lib.internals.surface.ScaleMatrixProvider
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.legacy.converter.dump
import io.github.toyota32k.media.lib.processor.contract.IActualSoughtMap
import io.github.toyota32k.media.lib.processor.contract.ICancellable
import io.github.toyota32k.media.lib.processor.contract.IConcatOptions
import io.github.toyota32k.media.lib.processor.contract.IConvertOptions
import io.github.toyota32k.media.lib.processor.contract.IConvertResult
import io.github.toyota32k.media.lib.processor.contract.IFormattable
import io.github.toyota32k.media.lib.processor.contract.IProcessor
import io.github.toyota32k.media.lib.processor.contract.IProcessorOptions
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.processor.contract.ISoughtMap
import io.github.toyota32k.media.lib.processor.contract.ITrack
import io.github.toyota32k.media.lib.processor.contract.format3digits
import io.github.toyota32k.media.lib.processor.optimizer.Optimizer
import io.github.toyota32k.media.lib.processor.track.EmptyTrack
import io.github.toyota32k.media.lib.processor.track.SilentAudioTrack
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

//    class Builder {
//        private var mContainerFormat: ContainerFormat = ContainerFormat.MPEG_4
//        private var mBufferSize:Int = DEFAULT_BUFFER_SIZE
//
//        fun containerFormat(containerFormat: ContainerFormat) = apply {
//            mContainerFormat = containerFormat
//        }
//        fun bufferSize(sizeInBytes:Int) = apply {
//            mBufferSize = sizeInBytes.coerceAtLeast(DEFAULT_BUFFER_SIZE)
//        }
//
//        fun build():Processor {
//            return Processor(
//                containerFormat = mContainerFormat,
//                bufferSize = mBufferSize,
//            )
//        }
//    }


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
            if (prev!=current && total>0 && total!=Long.MAX_VALUE) {
                updateRemainingTime()
                onProgress?.invoke(this)
            }
        }

        fun updateAudioUs(audioUs:Long) {
            val prev = current
            audioLength = max(audioLength, audioUs)
            if (prev!=current && total>0 && total!=Long.MAX_VALUE) {
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
     * options（トランスコード、トリミング、切り抜きなど）にしたがって変換を実行
     */
    private fun convertCore(options: IConvertOptions, onProgress:((IProgress)->Unit)?): IConvertResult {
        val inPath: IInputMediaFile = options.inPath
        val outPath: IOutputMediaFile = options.outPath
        val rangesUs:List<RangeUs> = options.rangesUs
        val limitDurationUs:Long = options.limitDurationUs
        val rotation:Rotation? = options.rotation
        val renderOption:RenderOption? = options.renderOption
        val videoStrategy: IVideoStrategy = options.videoStrategy
        val audioStrategy: IAudioStrategy = options.audioStrategy

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
     * 複数の動画ファイルを結合（concatenation）して1つのファイルに出力する。
     *
     * - 各入力の再生区間（トリミング）は ConcatOptions.Builder.addInput() で指定できる。
     * - 画素数の異なる動画は、1番目の入力を基準サイズとして ScaleMode にしたがってスケーリングされる。
     * - 入力の回転メタデータはレンダリング時に正規化され、出力に回転メタデータは設定されない。
     * - 結合は再エンコード前提（videoStrategy/audioStrategy 必須）。
     * - 音声はサンプルレート・チャネル数の異なるソースが混在してもよい（リサンプリング/リミックスされる）。
     *   音声トラックを持たないソースが混在する場合、その区間には無音が挿入される。
     *   音声を出力しない場合は PresetAudioStrategies.NoAudio を指定する。
     *
     * 制限:
     * - すべての入力に映像トラックが必要（音声のみのファイルは結合できない）。
     */
    private fun concatCore(options: IConcatOptions, onProgress: ((IProgress) -> Unit)?): IConvertResult {
        progress = ProgressHandler(onProgress)
        val report = Report().apply {
            start()
            updateVideoStrategyName(options.videoStrategy.name)
            updateAudioStrategyName(options.audioStrategy.name)
        }
        isCancelled = false

        // 解析・バリデーション・統一出力フォーマット(UnifiedOutputFormat)の決定
        val plan = ConcatAnalyzer.analyze(options)
        val unified = plan.unified

        Closeables().use { closer ->
            // Muxerの準備
            // 回転はGLレンダリングで正規化するため orientation hint は設定しない。
            // location は先頭ソースのものを引き継ぐ。
            val muxer = SyncMuxer(options.outPath, containerFormat, hasVideo = true, hasAudio = unified.hasAudio).apply {
                setup(plan.sourceInfos[0].metaData, rotation = null, applyRotation = false)
                closer.add(this)
            }

            // Progress情報を初期化（いずれかのソースの長さが不明なら進捗報告は無効）
            val sourceLengths = options.sources.mapIndexed { i, source ->
                source.effectiveRangesUs.totalLengthUs(plan.sourceInfos[i].durationUs ?: Long.MAX_VALUE)
            }
            val totalUs = if (sourceLengths.any { it == Long.MAX_VALUE }) Long.MAX_VALUE else sourceLengths.sum()
            progress.initialize(totalUs, video = true, audio = unified.hasAudio)

            // ソースを順に処理して1つのMuxerに書き込む
            var basePresentationTimeUs = 0L
            options.sources.forEachIndexed { index, source ->
                if (isCancelled) throw CancellationException()
                val info = plan.sourceInfos[index]
                logger.info("concat: source[$index] ${source.input} base=${basePresentationTimeUs}us")
                report.beginInputSource("Input Stream #${index + 1}")
                Closeables().use { sourceCloser ->
                    val trackSelector = TrackSelector(source.input, 0L, report, bufferSize, options.videoStrategy, options.audioStrategy).apply { sourceCloser.add(this) }
                    val renderOption = RenderOption(
                        ScaleMatrixProvider(info.width, info.height, info.rotation, unified.width, unified.height, options.scaleMode),
                        brightness = 1f)
                    val videoTrack = trackSelector.openVideoTrack(renderOption, unified.videoFormat).apply { sourceCloser.add(this) }
                    val audioTrack = when {
                        !unified.hasAudio -> EmptyTrack
                        info.audioSampleRate != null ->
                            // 音声ありソース: 統一サンプルレート/チャネル数で再エンコード（必要ならリサンプリング）
                            trackSelector.openAudioTrack(unified.audioSampleRate, unified.audioChannelCount).apply { sourceCloser.add(this) }
                        else ->
                            // 音声なしソース: 無音を挿入して A/V 同期を維持する
                            SilentAudioTrack(options.audioStrategy, unified.audioSampleRate, unified.audioChannelCount, videoTrack, info.durationUs, report).apply { sourceCloser.add(this) }
                    }
                    if (!videoTrack.isAvailable) throw IllegalStateException("no video track available: ${source.input}")

                    videoTrack.setup(muxer)
                    audioTrack.setup(muxer)
                    // 前のソースの末尾PTSを引き継ぐ
                    videoTrack.setBasePresentationTimeUs(basePresentationTimeUs)
                    audioTrack.setBasePresentationTimeUs(basePresentationTimeUs)

                    val soughtMap = SoughtMap(info.durationUs ?: Long.MAX_VALUE, source.effectiveRangesUs)
                    for (rangeUs in source.effectiveRangesUs) {
                        if (isCancelled) throw CancellationException()
                        extractRange(videoTrack, audioTrack, rangeUs, soughtMap)
                    }
                    videoTrack.finalize()
                    audioTrack.finalize()

                    // 次のソースの出力開始PTS:
                    // 映像・音声とも共通の基準値を使うことで、ソース境界のA/Vずれを次ソースに持ち越さない
                    basePresentationTimeUs = max(
                        videoTrack.presentationTimeUs + unified.videoFrameIntervalUs,
                        if (audioTrack.isAvailable) audioTrack.presentationTimeUs + unified.audioFrameIntervalUs else 0L
                    )
                }
            }

            // ファイナライズ
            muxer.stop()
            report.updateOutputFileInfo(options.outPath.getLength(), muxer.naturalDurationUs)
            report.muxerDurationUs = muxer.naturalDurationUs
            report.sourceDurationUs = totalUs
            report.end()

            return Result(options.sources.first().input, options.outPath, SoughtMap(muxer.naturalDurationUs, emptyList()), report)
        }
    }

    /**
     * concat()の後、fast start を実行
     */
    suspend fun concat(options: IConcatOptions, onProgress: ((IProgress) -> Unit)?): IConvertResult {
        return withContext(Dispatchers.IO) {
            try {
                Optimizer.process( options, onProgress) {
                    concatCore(options, onProgress)
                }
            } catch (e: Throwable) {
                if (options.deleteOutputOnError) {
                    options.outPath.safeDelete()
                }
                ErrorResult(options.sources.firstOrNull()?.input, e)
            }
        }
    }

    /**
     * Dispatchers.IO で process()を実行
     */
    suspend fun convert(options: IConvertOptions, onProgress:((IProgress)->Unit)?): IConvertResult {
        return withContext(Dispatchers.IO) {
            try {
                Optimizer.process( options, onProgress) {
                    convertCore(options, onProgress)
                }
            } catch (e: Throwable) {
                if (options.deleteOutputOnError) {
                    options.outPath.safeDelete()
                }
                ErrorResult(options.inPath, e)
            }
        }
    }

    /**
     * optionsにしたがって変換を実行
     */
    override suspend fun process(options: IProcessorOptions, onProgress:((IProgress)->Unit)?): IConvertResult {
        return when (options) {
            is IConvertOptions -> convert(options, onProgress)
            is IConcatOptions -> concat(options, onProgress)
            else -> throw IllegalArgumentException("invalid options")
        }
    }
    // endregion
}


