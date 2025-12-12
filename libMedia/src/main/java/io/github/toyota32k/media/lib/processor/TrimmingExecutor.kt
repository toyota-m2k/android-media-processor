package io.github.toyota32k.media.lib.processor

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.ActualSoughtMapImpl
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.converter.Converter.Companion.analyze
import io.github.toyota32k.media.lib.converter.HttpInputFile
import io.github.toyota32k.media.lib.converter.IConvertResult
import io.github.toyota32k.media.lib.converter.IHttpStreamSource
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.IOutputMediaFile
import io.github.toyota32k.media.lib.converter.IProgress
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.isHDR
import io.github.toyota32k.media.lib.processor.misc.RangeUsListBuilder
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.report.Summary
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IHDRSupport
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.MaxDefault
import io.github.toyota32k.media.lib.strategy.MinDefault
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.strategy.VideoStrategy
import io.github.toyota32k.media.lib.surface.RenderOption
import io.github.toyota32k.media.lib.utils.RangeUs
import io.github.toyota32k.media.lib.utils.RangeUs.Companion.ms2us
import io.github.toyota32k.media.lib.utils.RangeUs.Companion.outlineRangeUs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

/**
 * Processor を 旧Converterと（だいたい）互換APIで利用するためのラッパークラス
 */
class TrimmingExecutor(
    val processor: Processor,
    val inPath: IInputMediaFile,
    val outPath: IOutputMediaFile,
    val ranges: List<RangeUs>,
    val limitDurationUs: Long,
    val deleteOutputOnError:Boolean,
    val rotation: Rotation?,
    val renderOption: RenderOption?,
) : IExecutor {
    companion object {
        val logger = UtLog("TrimmingExecutor", Processor.logger)
    }
    val report = Report()
    override suspend fun execute(): IConvertResult {
        return withContext(Dispatchers.IO) {
            try {
                val actualSoughtMapImpl = ActualSoughtMapImpl()
                processor.trimming(inPath, outPath, ranges, limitDurationUs, rotation, renderOption, actualSoughtMapImpl, report)
                ProcessResult.success(outPath, ranges.outlineRangeUs(report.sourceDurationUs).toRangeMs(), actualSoughtMapImpl, report)
            } catch(e:Throwable) {
                logger.error(e)
                if (deleteOutputOnError) {
                    outPath.safeDelete()
                }
                ProcessResult.error(e)
            }
        }
    }

    override fun cancel() {
        processor.cancel()
    }

    class Builder(val processorBuilder:Processor.Builder = Processor.Builder()) {
        constructor(processor: Processor) : this(Processor.Builder.fromInstance(processor)) {
            mVideoStrategy = processor.videoStrategy
            mAudioStrategy = processor.audioStrategy
        }

        // region Processor Properties

        /**
         * コンテナフォーマットを指定
         * MPEG_4 以外はテストしていません。
         */
        fun containerFormat(containerFormat: ContainerFormat) = apply {
            processorBuilder.containerFormat(containerFormat)
        }

        /**
         * 作業バッファサイズ（NoReEncodeの場合にのみ使用）
         */
        fun bufferSize(sizeInBytes:Int) = apply {
            processorBuilder.bufferSize(sizeInBytes)
        }

        /**
         * video strategy を指定
         */
        fun videoStrategy(strategy: IVideoStrategy) = apply {
            mVideoStrategy = strategy
        }

        /**
         * audio strategy を指定
         */
        fun audioStrategy(strategy: IAudioStrategy) = apply {
            mAudioStrategy = strategy
        }
        /**
         * 進捗報告ハンドラを設定
         */
        fun setProgressHandler(proc:(IProgress)->Unit) = apply {
            processorBuilder.onProgress(proc)
        }

        // endregion

        private val mTrimmingRangeListBuilder: RangeUsListBuilder = RangeUsListBuilder()
        private var mClipStartUs:Long = 0L
        private var mClipEndUs:Long = 0L
        private var mLimitDurationUs: Long = 0L
        private var mInPath:IInputMediaFile? = null
        private var mOutPath: IOutputMediaFile? = null
        private var mVideoStrategy: IVideoStrategy = PresetVideoStrategies.AVC720Profile
        private var mAudioStrategy: IAudioStrategy = PresetAudioStrategies.AACDefault
        private var mDeleteOutputOnError = true
        private var mRotation: Rotation? = null
        private var mContainerFormat: ContainerFormat = ContainerFormat.MPEG_4
        private var mPreferSoftwareDecoder = false
        private var mKeepProfile:Boolean = false
        private var mKeepHDR: Boolean = false
        private var mBrightnessFactor = 1f
        private var mCropRect: Rect? = null
        private var mForceReEncodeDespiteOfNecessity:Boolean = false


        // region Setter: I/O files

        /**
         * 入力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * @param IInputMediaFile
         */
        fun input(src:IInputMediaFile) = apply {
            mInPath = src
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
            mOutPath = dst
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

        // region Executor Properties

        /**
         * Videoトラックを同じコーデックで再エンコードするとき、Profile/Levelを入力ファイルに合わせる
         */
        fun keepVideoProfile(flag:Boolean=true) = apply {
            mKeepProfile = flag
        }
        /**
         * HDRが有効なVideoトラックを再エンコードするとき、出力コーデックで可能ならHDRを維持するProfileを選択する。
         */
        fun keepHDR(flag:Boolean=true) = apply {
            mKeepHDR = flag
        }

        /**
         * ソフトウェアデコーダーを優先するかどうか
         */
        fun preferSoftwareDecoder(flag:Boolean) = apply {
            mPreferSoftwareDecoder = flag
        }

        /**
         * 必要性がなくても VideoStrategyにしたがって再エンコードするなら true
         * デフォルトは false (不必要な再エンコードは抑制する)
         */
        fun forceReEncodeDespiteOfNecessity(flag:Boolean) = apply {
            mForceReEncodeDespiteOfNecessity = flag
        }


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
        fun clipStartMs(ms:Long) = apply {
            mClipStartUs = ms.ms2us()
        }
        fun clipEndMs(ms:Long) = apply {
            mClipEndUs = ms.ms2us()
        }
        fun clipRangeUs(rangeUs: RangeUs) = apply {
            mClipStartUs = rangeUs.startUs
            mClipEndUs = rangeUs.endUs
        }
        fun clipReset() {
            mClipStartUs = 0L
            mClipEndUs = 0L
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

        // region Setter: Conversion Behavior

        /**
         * エラー発生時に出力ファイルを削除するかどうか
         * デフォルトは true
         */
        fun deleteOutputOnError(flag:Boolean) = apply {
            mDeleteOutputOnError = flag
        }

        // endregion

        // region Setter: Conversion Instruction

        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation?) = apply {
            mRotation = if (rotation == Rotation.nop) null else rotation
        }

        /**
         * 明るさ補正係数 ... not implemented yet
         */
        fun brightness(brightness:Float?) = apply {
            mBrightnessFactor = brightness ?: 1f
        }

        /**
         * 切り抜き範囲を設定
         */
        fun crop(rect:Rect?) = apply {
            mCropRect = rect
        }
        /**
         * 切り抜き範囲を設定
         */
        fun crop(x:Int, y:Int, cx:Int, cy:Int) = apply {
            mCropRect = Rect(x, y, x+cx, y+cy)
        }

        // endregion

        // region Build TrimmingExecutor

        private val inputSummary: Summary by lazy {
            val inPath = mInPath ?: throw IllegalStateException("no input path.")
            analyze(inPath)
        }

        /**
         * 現在のパラメータについて、再エンコードが必要かどうかをチェックする。
         * @return true: エンコードが必要 / false:不要
         */
        fun checkReEncodingNecessity() : Boolean {
            val summary = inputSummary.videoSummary ?: return true // ソース情報が不明
            if (mCropRect!=null || mBrightnessFactor!=1f) {
                // crop や brightness変更はコンバートが必要
                if (mVideoStrategy == PresetVideoStrategies.InvalidStrategy) {
                    // Sourceに近いStrategyを作成して設定
                    videoStrategy(VideoStrategy(
                        summary.codec ?: throw IllegalStateException("unknown video codec."),
                        summary.profile ?: throw IllegalStateException("unknown video codec profile"),
                        summary.level,
                        null,
                        VideoStrategy.SizeCriteria(Int.MAX_VALUE, Int.MAX_VALUE),
                        MaxDefault(Int.MAX_VALUE,max(summary.bitRate, 768*1000)),
                        MaxDefault(30, max(summary.frameRate, 24)),
                        MinDefault(1, summary.iFrameInterval.takeIf { it > 0 } ?: 30),
                        null,
                        null,))
                    keepHDR(true)
                    keepVideoProfile(true)
                }
                return true
            }
            if (mVideoStrategy == PresetVideoStrategies.InvalidStrategy) {
                // 明示的に再エンコード不要が指定されている
                return false
            }

            if (mVideoStrategy.codec != summary.codec) {
                // コーデックが違う
                return true
            }

            if (summary.bitRate>0 && summary.bitRate > mVideoStrategy.bitRate.max) {
                // ビットレートが、Strategy の max bitRate より大きい
                return true
            }

            if (max(summary.width, summary.height) > mVideoStrategy.sizeCriteria.longSide ||
                min(summary.width, summary.height) > mVideoStrategy.sizeCriteria.shortSide) {
                // 解像度が、Strategy の制限より大きい（縮小が必要）
                return true
            }
            // 再エンコード不要
            if (!mForceReEncodeDespiteOfNecessity) {
                videoStrategy(PresetVideoStrategies.InvalidStrategy)
            }
            return false
        }


        /**
         * 内部メソッド：build()時にVideoStrategyを調整する
         * mKeepHDR, mKeepProfile の設定に基づいて、videoStrategyを調整する
         */
        private fun adjustVideoStrategy() {
            checkReEncodingNecessity()
            val strategy = mVideoStrategy
            if (strategy is PresetVideoStrategies.InvalidStrategy) return
            if (!mKeepHDR && !mKeepProfile) return
            val summary = inputSummary
            val srcCodec = summary.videoSummary?.codec ?: return
            val srcProfile = summary.videoSummary?.profile ?: return
            val srcLevel = summary.videoSummary?.level ?: strategy.maxLevel

            val adjStrategy = if (mKeepProfile && strategy.codec == srcCodec && strategy.profile != srcProfile) {
                strategy.derived(profile = srcProfile, level = srcLevel)
            } else if (mKeepHDR && srcProfile.isHDR() && strategy is IHDRSupport) {
                if (strategy.codec == srcCodec) {
                    strategy.hdr(srcProfile, srcLevel)
                } else {
                    strategy.hdr()
                }
            } else {
                strategy
            }
            videoStrategy(adjStrategy)
        }

        fun build(): TrimmingExecutor {
            val inPath = mInPath ?: throw IllegalStateException("input file is not specified.")
            val outPath = mOutPath ?: throw IllegalStateException("output file is not specified.")
            adjustVideoStrategy()

            logger.info("### media converter information ###")
            logger.info("input : ${mInPath}")
            logger.info("output: ${mOutPath}")
            logger.info("video strategy: ${mVideoStrategy.javaClass.name}")
            logger.info("audio strategy: ${mAudioStrategy.javaClass.name}")

            try {
                val rangeListUs = mTrimmingRangeListBuilder.toRangeUsListWithClipUs(mClipStartUs, mClipEndUs)
                logger.info("delete output on error = ${mDeleteOutputOnError}")
                val renderOption = if (mCropRect != null) {
                    val summary = inputSummary.videoSummary ?: throw IllegalStateException("no video information, cannot crop.")
                    RenderOption.create(summary.width, summary.height, mCropRect!!, mBrightnessFactor)
                } else if (mBrightnessFactor != 1f) {
                    RenderOption.create(mBrightnessFactor)
                } else {
                    RenderOption.DEFAULT
                }
                val processor = processorBuilder
                    .videoStrategy(mVideoStrategy)
                    .audioStrategy(mAudioStrategy)
                    .build()
                return TrimmingExecutor(
                    processor,
                    inPath,
                    outPath,
                    ranges = rangeListUs,
                    limitDurationUs = mLimitDurationUs,
                    deleteOutputOnError = mDeleteOutputOnError,
                    rotation = mRotation,
                    renderOption = renderOption,
                )
            } catch(e:Throwable) {
                logger.error(e)
                if (mDeleteOutputOnError) {
                    mOutPath?.safeDelete()
                }
                throw e
            }
        }
        // endregion
    }
}