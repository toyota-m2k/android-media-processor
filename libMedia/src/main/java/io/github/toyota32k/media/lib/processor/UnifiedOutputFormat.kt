package io.github.toyota32k.media.lib.processor

import android.media.MediaFormat

/**
 * 動画結合（concat）時に、全入力ソースで共有する出力フォーマット。
 *
 * SyncMuxer は最初に確定したフォーマットしか受け付けないため、
 * 結合処理の開始前に ConcatAnalyzer がこれを決定し、
 * 全ソースのエンコーダーに同一の出力フォーマットを強制する。
 *
 * @param videoFormat       エンコーダーに設定する映像フォーマット（width/height/bitrate等 確定済み）
 * @param width             出力映像の幅（videoFormat と同値。参照用）
 * @param height            出力映像の高さ（videoFormat と同値。参照用）
 * @param frameRate         出力フレームレート（ソース間のPTSギャップ計算に使用）
 * @param hasAudio          出力に音声トラックを含めるか
 * @param audioSampleRate   出力音声のサンプルレート（hasAudio=false のとき 0）
 * @param audioChannelCount 出力音声のチャネル数 1 or 2（hasAudio=false のとき 0）
 */
class UnifiedOutputFormat(
    val videoFormat: MediaFormat,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val hasAudio: Boolean,
    val audioSampleRate: Int,
    val audioChannelCount: Int,
) {
    /** ソース間PTSギャップ: 映像1フレーム分 (us) */
    val videoFrameIntervalUs: Long get() = 1_000_000L / frameRate.coerceAtLeast(1)

    /** ソース間PTSギャップ: 音声1フレーム(AAC:1024サンプル)分 (us) */
    val audioFrameIntervalUs: Long get() =
        if (hasAudio && audioSampleRate > 0) 1024L * 1_000_000L / audioSampleRate else 0L

    override fun toString(): String {
        return "UnifiedOutputFormat(${width}x${height}@${frameRate}fps, audio=${if(hasAudio) "${audioSampleRate}Hz/${audioChannelCount}ch" else "none"})"
    }
}
