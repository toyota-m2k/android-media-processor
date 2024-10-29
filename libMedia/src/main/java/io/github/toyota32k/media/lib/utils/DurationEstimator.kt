package io.github.toyota32k.media.lib.utils

import kotlin.math.max

/**
 * ExtractorやMuxerが書き込んだ動画の総再生時間(natural duration)の推計値
 *
 * ExtractorやMuxerは、バッファーのデータの先頭時間は取得できるが、それを書き込んだ結果の総時間を取得できない。
 * つまり、最後の１回分のバッファの時間が補足できない。
 * その程度は誤差としてネグってもよいかもしれないが、それまでに書き込んだバイト数から比例計算で類推するようにしてみた。
 */
class DurationEstimator {
    var durationUs: Long = 0L
    var previousSize = 0L
    var lastSize: Long = 0L

    fun update(currentTimeUs: Long, bufferSize: Long) {
        durationUs = max(durationUs, currentTimeUs)
        previousSize += lastSize
        lastSize = bufferSize
    }

    val estimatedDurationUs get() =
        if(previousSize>0) {
            ((durationUs.toDouble() / previousSize.toDouble())*(previousSize.toDouble()+lastSize.toDouble())).toLong()
        } else {
            durationUs
        }
}