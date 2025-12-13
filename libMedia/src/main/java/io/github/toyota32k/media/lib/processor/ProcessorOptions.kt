package io.github.toyota32k.media.lib.processor

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import io.github.toyota32k.media.lib.converter.ActualSoughtMapImpl
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.converter.Converter.Companion.analyze
import io.github.toyota32k.media.lib.converter.HttpInputFile
import io.github.toyota32k.media.lib.converter.IHttpStreamSource
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.IOutputMediaFile
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.processor.misc.IFormattable
import io.github.toyota32k.media.lib.processor.misc.RangeUsListBuilder
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.report.Summary
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.surface.RenderOption
import io.github.toyota32k.media.lib.utils.RangeUs
import io.github.toyota32k.media.lib.utils.RangeUs.Companion.formatAsUs
import io.github.toyota32k.media.lib.utils.RangeUs.Companion.ms2us
import java.io.File
import kotlin.time.Duration

/**
 * Processor.process() に渡すパラメーターを１つのオブジェクトのまとめるクラス。
 * process()に個々のパラメータを指定してもよいが、通常は、BuilderパターンでProcessorOptionsを構築して利用する。
 */
data class ProcessorOptions(
    override val inPath: IInputMediaFile,
    override val outPath: IOutputMediaFile,
    override val videoStrategy: IVideoStrategy,
    override val audioStrategy: IAudioStrategy,
    override val rangesUs: List<RangeUs>,
    override val limitDurationUs: Long,
    override val rotation: Rotation?,
    override val renderOption: RenderOption?,
): Processor.IOptions, IFormattable {
    override fun toString() : String {
        return format().toString()
    }
    override fun format(sb: StringBuilder): StringBuilder {
        return sb.apply {
            appendLine("## Processor Options")
            appendLine("input : ${inPath}")
            appendLine("output: ${outPath}")
            appendLine("video strategy: ${videoStrategy.javaClass.name}")
            appendLine("audio strategy: ${audioStrategy.javaClass.name}")
            appendLine("ranges: ${rangesUs.joinToString(", ") { it.toString() }}")
            appendLine("limit duration: ${limitDurationUs.formatAsUs()}")
            appendLine("rotation: ${rotation?:"unspecified"}")
            appendLine("crop: ${renderOption?.matrixProvider?.toString()?:"unspecified"}")
            appendLine("brightness: ${renderOption?.brightness?:"unspecified"}")
        }
    }

    class Builder {
        private var mInPath: IInputMediaFile? = null
        private var mOutPath: IOutputMediaFile? = null
        private var mPreferSoftwareDecoder = false
        private var mKeepProfile: Boolean = false
        private var mKeepHDR: Boolean = false
        private var mForceReEncodeDespiteOfNecessity: Boolean = false
        private var mVideoStrategy: IVideoStrategy = PresetVideoStrategies.InvalidStrategy
        private var mAudioStrategy: IAudioStrategy = PresetAudioStrategies.InvalidStrategy
        private var mBrightnessFactor = 1f
        private var mCropRect: Rect? = null
        private var mRotation: Rotation? = null

        private val mTrimmingRangeListBuilder: RangeUsListBuilder = RangeUsListBuilder()
        private var mClipStartUs:Long = 0L
        private var mClipEndUs:Long = 0L
        private var mLimitDurationUs: Long = 0L

        // region Computed Parameters

        val renderOption: RenderOption
            get() = if (mCropRect != null) {
                val summary = inputSummary.videoSummary ?: throw IllegalStateException("no video information, cannot crop.")
                RenderOption.create(summary.width, summary.height, mCropRect!!, mBrightnessFactor)
            } else if (mBrightnessFactor != 1f) {
                RenderOption.create(mBrightnessFactor)
            } else {
                RenderOption.DEFAULT
            }

        val inputSummary: Summary by lazy {
            analyze(mInPath ?: throw IllegalStateException("input file is not specified."))
        }

        // endregion


        // region Setter: I/O files

        val input: IInputMediaFile? get() = mInPath


        /**
         * 入力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * @param IInputMediaFile
         */
        fun input(src: IInputMediaFile) = apply {
            mInPath = src
        }

        /**
         * 入力ファイルを設定
         * @param File
         */
        fun input(path: File) = input(AndroidFile(path))

        /**
         * 入力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun input(uri: Uri, context: Context) = input(AndroidFile(uri, context))

        /**
         * 入力ファイルを設定
         * @param String URL (http/https)
         * @param Context
         */
        fun input(url: String, context: Context) = apply {
            if (!url.startsWith("http")) throw IllegalArgumentException("url must be http or https")
            input(HttpInputFile(context, url))
        }

        /**
         * 入力ファイルを設定
         * @param IHttpStreamSource
         * @param Context
         */
        fun input(source: IHttpStreamSource, context: Context) = input(HttpInputFile(context, source))

        val output: IOutputMediaFile? get() = mOutPath
        /**
         * 出力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * ただし、inputと異なり、HttpFileは利用不可
         * @param IOutputMediaFile
         */
        fun output(dst: IOutputMediaFile) = apply {
            mOutPath = dst
        }

        /**
         * 出力ファイルを設定
         * @param File
         */
        fun output(path: File) = output(AndroidFile(path))

        /**
         * 出力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun output(uri: Uri, context: Context) = output(AndroidFile(uri, context))

        // endregion

        // region Strategies

        fun videoStrategy(strategy: IVideoStrategy) = apply {
            mVideoStrategy = strategy
        }

        fun audioStrategy(strategy: IAudioStrategy) = apply {
            mAudioStrategy = strategy
        }

        /**
         * Videoトラックを同じコーデックで再エンコードするとき、Profile/Levelを入力ファイルに合わせる
         */
        fun keepVideoProfile(flag: Boolean = true) = apply {
            mKeepProfile = flag
        }
        val keepVideoProfile: Boolean get() = mKeepProfile


        /**
         * HDRが有効なVideoトラックを再エンコードするとき、出力コーデックで可能ならHDRを維持するProfileを選択する。
         */
        fun keepHDR(flag: Boolean = true) = apply {
            mKeepHDR = flag
        }
        val keepHDR: Boolean get() = mKeepHDR

        /**
         * ソフトウェアデコーダーを優先するかどうか
         */
        fun preferSoftwareDecoder(flag: Boolean) = apply {
            mPreferSoftwareDecoder = flag
        }

        /**
         * 必要性がなくても VideoStrategyにしたがって再エンコードするなら true
         * デフォルトは false (不必要な再エンコードは抑制する)
         */
        fun forceReEncodeDespiteOfNecessity(flag: Boolean) = apply {
            mForceReEncodeDespiteOfNecessity = flag
        }
        val forceReEncodeDespiteOfNecessity:Boolean get() = mForceReEncodeDespiteOfNecessity

        // endregion

        // region Setter: Trimming

        fun trimming(fn: RangeUsListBuilder.()->Unit) = apply {
            mTrimmingRangeListBuilder.fn()
        }

        // trimming に被せて、切り取る範囲を指定。
        // trimming + splitting に利用する想定

        fun clipStartUs(us:Long) = apply {
            mClipStartUs = us
        }
        fun clipEndUs(us:Long) = apply {
            mClipEndUs = us
        }
        fun clipStartMs(ms:Long) =
            clipStartUs(ms.ms2us())

        fun clipEndMs(ms:Long) =
            clipEndUs(ms.ms2us())

        fun clipRangeUs(rangeUs: RangeUs) = apply {
            clipStartUs(rangeUs.startUs)
            clipEndUs(rangeUs.endUs)
        }
        fun clipReset() = apply {
            clipStartUs(0L)
            clipEndUs(0L)
        }

        /**
         * 最大動画長を指定 (ms)
         * 0以下なら制限なし
         */
        fun limitDuration(durationMs:Long) = apply {
            mLimitDurationUs = durationMs.ms2us()
        }

        /**
         * 最大動画長を指定 (Duration)
         * nullまたは0以下なら制限なし
         */
        fun limitDuration(duration: Duration?)
                = limitDuration(duration?.inWholeMilliseconds ?: 0L)

        // endregion

        // region Render Options

        /**
         * 切り抜き範囲を設定
         */
        fun crop(rect: Rect?) = apply {
            mCropRect = rect
        }

        /**
         * 切り抜き範囲を設定
         */
        fun crop(x: Int, y: Int, cx: Int, cy: Int) = apply {
            mCropRect = Rect(x, y, x + cx, y + cy)
        }
        val crop:Rect? get() = mCropRect

        /**
         * 明るさ補正係数 ... not implemented yet
         */
        fun brightness(brightness: Float?) = apply {
            mBrightnessFactor = brightness ?: 1f
        }
        val brightness: Float get() = mBrightnessFactor

        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation?) = apply {
            mRotation = if (rotation == Rotation.nop) null else rotation
        }

        // endregion

        /**
         * ProcessorOption を作成する
         */
        fun build(): ProcessorOptions {
            return ProcessorOptions(
                inPath = mInPath ?: throw IllegalStateException("input file is not specified."),
                outPath = mOutPath ?: throw IllegalStateException("output file is not specified."),
                videoStrategy = StrategyAdjuster.fromOptionBuilder(this).adjust(mVideoStrategy),
                audioStrategy = mAudioStrategy,
                rangesUs = mTrimmingRangeListBuilder.toRangeUsListWithClipUs(mClipStartUs, mClipEndUs),
                limitDurationUs = mLimitDurationUs,
                rotation = mRotation,
                renderOption = renderOption
            )
        }

    }
}