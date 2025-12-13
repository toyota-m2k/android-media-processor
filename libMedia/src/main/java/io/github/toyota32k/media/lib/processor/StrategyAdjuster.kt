package io.github.toyota32k.media.lib.processor

import android.graphics.Rect
import io.github.toyota32k.media.lib.format.isHDR
import io.github.toyota32k.media.lib.report.Summary
import io.github.toyota32k.media.lib.strategy.IHDRSupport
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.MaxDefault
import io.github.toyota32k.media.lib.strategy.MinDefault
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.strategy.VideoStrategy
import kotlin.math.max
import kotlin.math.min

class StrategyAdjuster(
    val inputSummary: Summary,
    val cropRect:Rect?,
    val brightnessFactor: Float,
    val forceReEncodeDespiteOfNecessity:Boolean,
    var keepHDR: Boolean,
    var keepProfile: Boolean,
) {
    companion object {
        fun fromOptionBuilder(b:ProcessorOptions.Builder):StrategyAdjuster {
            return StrategyAdjuster(b.inputSummary, b.crop, b.brightness, b.forceReEncodeDespiteOfNecessity, b.keepHDR, b.keepVideoProfile)
        }
    }
    /**
     * 入力ファイルの情報(inputSummary)と各パラメータに基づき、再エンコードが必要かどうかをチェックし、
     * IVideoStrategyを調整する。
     * @return 調整後のIVideoStrategy
     */
    private fun adjustWithReEncodingNecessity(mVideoStrategy: IVideoStrategy) : IVideoStrategy {
        val summary = inputSummary.videoSummary ?: return mVideoStrategy // ソース情報が不明
        if (cropRect!=null || brightnessFactor!=1f) {
            // crop や brightness変更はコンバートが必要
            if (mVideoStrategy == PresetVideoStrategies.InvalidStrategy) {
                // Sourceに近いStrategyを作成して設定
                keepHDR = true
                keepProfile = true
                return VideoStrategy(
                    summary.codec ?: throw IllegalStateException("unknown video codec."),
                    summary.profile ?: throw IllegalStateException("unknown video codec profile"),
                    summary.level,
                    null,
                    VideoStrategy.SizeCriteria(Int.MAX_VALUE, Int.MAX_VALUE),
                    MaxDefault(Int.MAX_VALUE,max(summary.bitRate, 768*1000)),
                    MaxDefault(30, max(summary.frameRate, 24)),
                    MinDefault(1, summary.iFrameInterval.takeIf { it > 0 } ?: 30),
                    null,
                    null,)
            }
        }
        if (mVideoStrategy == PresetVideoStrategies.InvalidStrategy) {
            // 明示的に再エンコード不要が指定されている
            return mVideoStrategy
        }

        if (mVideoStrategy.codec != summary.codec) {
            // コーデックが違う
            return mVideoStrategy
        }

        if (summary.bitRate>0 && summary.bitRate > mVideoStrategy.bitRate.max) {
            // ビットレートが、Strategy の max bitRate より大きい
            return mVideoStrategy
        }

        if (max(summary.width, summary.height) > mVideoStrategy.sizeCriteria.longSide ||
            min(summary.width, summary.height) > mVideoStrategy.sizeCriteria.shortSide) {
            // 解像度が、Strategy の制限より大きい（縮小が必要）
            return mVideoStrategy
        }
        // 再エンコード不要
        if (!forceReEncodeDespiteOfNecessity) {
            return PresetVideoStrategies.InvalidStrategy
        }
        return mVideoStrategy
    }


    /**
     * adjustWithReEncodingNecessity()を実行したのち、さらに、
     * mKeepHDR, mKeepProfile の設定に基づいて、videoStrategyを調整する
     */
    fun adjust(mVideoStrategy: IVideoStrategy): IVideoStrategy {
        val strategy = adjustWithReEncodingNecessity(mVideoStrategy)
        if (strategy is PresetVideoStrategies.InvalidStrategy) return strategy
        if (!keepHDR && !keepProfile) return strategy
        val summary = inputSummary
        val srcCodec = summary.videoSummary?.codec ?: return strategy
        val srcProfile = summary.videoSummary?.profile ?: return strategy
        val srcLevel = summary.videoSummary?.level ?: strategy.maxLevel

        val adjStrategy = if (keepProfile && strategy.codec == srcCodec && strategy.profile != srcProfile) {
            strategy.derived(profile = srcProfile, level = srcLevel)
        } else if (keepHDR && srcProfile.isHDR() && strategy is IHDRSupport) {
            if (strategy.codec == srcCodec) {
                strategy.hdr(srcProfile, srcLevel)
            } else {
                strategy.hdr()
            }
        } else {
            strategy
        }
        return adjStrategy
    }

}