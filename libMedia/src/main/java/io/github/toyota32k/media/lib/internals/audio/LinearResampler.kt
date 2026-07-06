package io.github.toyota32k.media.lib.internals.audio

import java.nio.ShortBuffer

/**
 * 線形補間によるサンプルレート変換の実装。
 *
 * 出力サンプル n の入力ストリーム上の位置は p(n) = n * inputRate / outputRate （フレーム単位）。
 * 直前フレーム(prev)と現フレーム(cur)の間に位置する出力サンプルを線形補間で生成する。
 * 位相（分母 outputSampleRate の分数）を整数演算で保持するため、丸め誤差の蓄積はない。
 *
 * 音質メモ: 線形補間はダウンサンプリング時のエイリアシングや高域の減衰があり得るが、
 * 44.1kHz/48kHz 間の変換では実用上問題になりにくい。高品質化が必要になった場合は
 * IAudioResampler の別実装（多相FIR等）に差し替える。
 */
class LinearResampler(
    override val inputSampleRate: Int,
    override val outputSampleRate: Int,
    override val channelCount: Int,
) : IAudioResampler {

    init {
        require(inputSampleRate > 0) { "inputSampleRate must be positive: $inputSampleRate" }
        require(outputSampleRate > 0) { "outputSampleRate must be positive: $outputSampleRate" }
        require(channelCount == 1 || channelCount == 2) { "channelCount must be 1 or 2: $channelCount" }
    }

    private val mPrev = ShortArray(channelCount)    // 直前の入力フレーム
    private val mCur = ShortArray(channelCount)     // 現在の入力フレーム
    private var mHasPrev = false
    private var mFracNum = 0L   // prev-cur 間の出力位相（分子。分母 = outputSampleRate）

    override fun estimateOutputSampleCount(inputSampleCount: Int): Int {
        val inputFrames = inputSampleCount / channelCount
        val outputFrames = (inputFrames.toLong() * outputSampleRate + inputSampleRate - 1) / inputSampleRate + 2
        return (outputFrames * channelCount).toInt()
    }

    override fun resample(input: ShortBuffer, output: ShortBuffer) {
        while (input.remaining() >= channelCount) {
            if (!mHasPrev) {
                // 最初のフレームを prev として取り込む（位相0 = prev の位置から出力開始）
                for (c in 0 until channelCount) {
                    mPrev[c] = input.get()
                }
                mHasPrev = true
                mFracNum = 0L
                continue
            }
            // 次の入力フレームを取り込み、prev-cur 間に位置する出力フレームをすべて生成する
            for (c in 0 until channelCount) {
                mCur[c] = input.get()
            }
            while (mFracNum < outputSampleRate) {
                if (output.remaining() < channelCount) {
                    throw IllegalStateException("output buffer has no sufficient space.")
                }
                for (c in 0 until channelCount) {
                    val p = mPrev[c].toInt()
                    val q = mCur[c].toInt()
                    output.put((p + (q - p).toLong() * mFracNum / outputSampleRate).toShort())
                }
                mFracNum += inputSampleRate
            }
            mFracNum -= outputSampleRate
            mCur.copyInto(mPrev)
        }
    }

    override fun reset() {
        mHasPrev = false
        mFracNum = 0L
    }
}
