package io.github.toyota32k.media.lib.internals.audio

import java.nio.ShortBuffer

/**
 * 16bit PCM (interleaved) のサンプルレート変換 i/f
 *
 * ストリーミング動作を前提とし、ブロック（バッファ）境界をまたいで
 * 内部状態（直前フレーム・位相）を保持する。
 * 実装を差し替えられるよう（線形補間→多相FIRなど）、インターフェースとして定義する。
 */
interface IAudioResampler {
    val inputSampleRate: Int
    val outputSampleRate: Int
    val channelCount: Int

    /**
     * input の position..limit の PCM データをレート変換して、output の position 以降に書き込む。
     *
     * - input は消費される（position が limit まで進む）。
     * - output には estimateOutputSampleCount(input.remaining()) 以上の空き容量が必要。
     *   不足している場合は IllegalStateException をスローする。
     * - サンプルレート変換は再生時間を変えない（サンプル数だけが変わる）ため、
     *   変換前のPTSは変換後もそのまま有効。
     */
    fun resample(input: ShortBuffer, output: ShortBuffer)

    /**
     * inputSampleCount 個の入力サンプル（チャネル込みの short 数）から生成される
     * 出力サンプル数の上限を返す。出力バッファのサイズ決定に使用する。
     */
    fun estimateOutputSampleCount(inputSampleCount: Int): Int

    /**
     * 内部状態（直前フレーム・位相）をリセットする。
     * ストリームが不連続になる場合（別ソースへの切り替えなど）に呼び出す。
     */
    fun reset()
}
