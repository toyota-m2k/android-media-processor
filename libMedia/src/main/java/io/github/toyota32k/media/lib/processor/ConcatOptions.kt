package io.github.toyota32k.media.lib.processor

import android.content.Context
import android.net.Uri
import io.github.toyota32k.media.lib.io.AndroidFile
import io.github.toyota32k.media.lib.io.HttpInputFile
import io.github.toyota32k.media.lib.io.IHttpStreamSource
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.processor.contract.IConcatOptions
import io.github.toyota32k.media.lib.processor.contract.IFormattable
import io.github.toyota32k.media.lib.processor.contract.IProcessorOptions
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.processor.optimizer.OptimizerOptions
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.types.RangeUs
import io.github.toyota32k.media.lib.types.ScaleMode
import io.github.toyota32k.media.lib.utils.RangeUsListBuilder
import io.github.toyota32k.utils.UtLib
import java.io.File

/**
 * 結合対象の入力ソース。
 *
 * @param input     入力ファイル
 * @param rangesUs  この入力から出力する再生区間のリスト（空リストなら全区間）
 */
data class ConcatSource(
    val input: IInputMediaFile,
    val rangesUs: List<RangeUs> = emptyList(),
) {
    /** 実際に処理する区間リスト（空なら全区間を表す FULL に置き換える） */
    val effectiveRangesUs: List<RangeUs>
        get() = rangesUs.ifEmpty { listOf(RangeUs.FULL) }
}

/**
 * Processor.concat() に渡すパラメーターをまとめるクラス。
 * Builderパターンで構築して利用する。
 */
class ConcatOptions private constructor(
    override val sources: List<ConcatSource>,
    override val outPath: IOutputMediaFile,
    override val videoStrategy: IVideoStrategy,
    override val audioStrategy: IAudioStrategy,
    override val scaleMode: ScaleMode,
    override val optimizerOptions: OptimizerOptions?,
    override val deleteOutputOnError: Boolean
) : IConcatOptions, IFormattable {

    /**
     * 出力先・進捗コールバックを差し替えた ConcatOptions を作成する（Optimizer/FastStart 用）。
     */
    override fun derive(
        outPath: IOutputMediaFile,
        optimizerOptions: OptimizerOptions?,
    ): ConcatOptions {
        return ConcatOptions(sources, outPath, videoStrategy, audioStrategy, scaleMode, optimizerOptions, deleteOutputOnError)
    }

    override fun toString(): String {
        return format().toString()
    }

    override fun format(sb: StringBuilder): StringBuilder {
        return sb.apply {
            appendLine("## Concat Options")
            sources.forEachIndexed { i, src ->
                appendLine("input[$i] : ${src.input} ranges: ${src.effectiveRangesUs.joinToString(", ") { it.toString() }}")
            }
            appendLine("output: $outPath")
            appendLine("video strategy: ${videoStrategy.javaClass.name}")
            appendLine("audio strategy: ${audioStrategy.javaClass.name}")
            appendLine("scale mode: $scaleMode")
        }
    }

    class Builder {
        private val mSources = mutableListOf<ConcatSource>()
        private var mOutPath: IOutputMediaFile? = null
        private var mVideoStrategy: IVideoStrategy = PresetVideoStrategies.InvalidStrategy
        private var mAudioStrategy: IAudioStrategy = PresetAudioStrategies.InvalidStrategy
        private var mScaleMode: ScaleMode = ScaleMode.FitInside
        private var mDeleteOutputOnError: Boolean = true
        private var mOptimizerOptions: OptimizerOptions? = null

        // region Input Sources

        /**
         * 結合する入力ファイルを追加する（追加した順に結合される）。
         *
         * @param src       入力ファイル
         * @param trimming  この入力から出力する再生区間の指定（省略時は全区間）
         */
        fun addInput(src: IInputMediaFile, trimming: (RangeUsListBuilder.() -> Unit)? = null) = apply {
            val ranges = if (trimming != null) {
                RangeUsListBuilder().apply(trimming).toRangeUsListWithClipUs()
            } else {
                emptyList()
            }
            mSources.add(ConcatSource(src, ranges))
        }

        fun addInput(path: File, trimming: (RangeUsListBuilder.() -> Unit)? = null) =
            addInput(AndroidFile(path), trimming)

        fun addInput(uri: Uri, context: Context, trimming: (RangeUsListBuilder.() -> Unit)? = null) =
            addInput(AndroidFile(uri, context), trimming)
        fun addInput(uri: Uri, trimming: (RangeUsListBuilder.() -> Unit)? = null) =
            addInput(AndroidFile(uri), trimming)

        fun addInput(url: String, context: Context, trimming: (RangeUsListBuilder.() -> Unit)? = null) = apply {
            if (!url.startsWith("http")) throw IllegalArgumentException("url must be http or https")
            addInput(HttpInputFile(context, url), trimming)
        }
        fun addInput(url: String, trimming: (RangeUsListBuilder.() -> Unit)? = null) =
            addInput(url, UtLib.applicationContext, trimming)

        fun addInput(source: IHttpStreamSource, context: Context, trimming: (RangeUsListBuilder.() -> Unit)? = null) =
            addInput(HttpInputFile(context, source), trimming)
        fun addInput(source: IHttpStreamSource, trimming: (RangeUsListBuilder.() -> Unit)? = null) =
            addInput(HttpInputFile(source), trimming)


        // endregion

        // region Output

        fun output(dst: IOutputMediaFile) = apply {
            mOutPath = dst
        }

        fun output(path: File) = output(AndroidFile(path))

        fun output(uri: Uri, context: Context) = output(AndroidFile(uri, context))
        fun output(uri: Uri) = output(AndroidFile(uri))

        // endregion

        // region Strategies / Options
        fun optimize(applicationContext:Context, removeFreeAtom:Boolean) = apply {
            mOptimizerOptions = OptimizerOptions(applicationContext, removeFreeAtom)
        }
        fun optimize(optimizerOptions: OptimizerOptions?) = apply {
            mOptimizerOptions = optimizerOptions
        }

        /**
         * 映像ストラテジー（必須）
         * 結合は再エンコード前提のため、InvalidStrategy（無変換）は指定できない。
         */
        fun videoStrategy(strategy: IVideoStrategy) = apply {
            mVideoStrategy = strategy
        }

        /**
         * 音声ストラテジー（必須）
         * InvalidStrategy（無変換）は指定できない。音声を出力しない場合は NoAudio を指定する。
         */
        fun audioStrategy(strategy: IAudioStrategy) = apply {
            mAudioStrategy = strategy
        }

        /**
         * 基準サイズと異なるサイズの動画のスケーリングモード（デフォルト: FitInside）
         */
        fun scaleMode(mode: ScaleMode) = apply {
            mScaleMode = mode
        }

        // endregion

        fun build(): ConcatOptions {
            if (mSources.size < 2) throw IllegalStateException("at least 2 input files are required.")
            if (mVideoStrategy is PresetVideoStrategies.InvalidStrategy) throw IllegalStateException("video strategy is not specified. (concat requires re-encoding)")
            if (mAudioStrategy is PresetAudioStrategies.InvalidStrategy) throw IllegalStateException("audio strategy is not specified. (concat requires re-encoding: use NoAudio to drop audio)")
            return ConcatOptions(
                sources = mSources.toList(),
                outPath = mOutPath ?: throw IllegalStateException("output file is not specified."),
                videoStrategy = mVideoStrategy,
                audioStrategy = mAudioStrategy,
                scaleMode = mScaleMode,
                optimizerOptions = mOptimizerOptions,
                deleteOutputOnError = mDeleteOutputOnError
            )
        }
    }
}
