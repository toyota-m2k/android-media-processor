package io.github.toyota32k.media.lib.utils

import kotlin.time.Duration

/**
 * トリミング範囲 (開始位置、終了位置) クラス (ms)
 */
data class RangeMs(val startMs:Long, val endMs:Long) {
    constructor(start: Duration?, end: Duration?) : this(
        startMs = start?.inWholeMilliseconds ?: 0L,
        endMs = end?.inWholeMilliseconds ?: 0L
    )

    fun lengthMs(durationMs: Long): Long {
        return (if (endMs !in 1..durationMs) durationMs else endMs) - startMs
    }

    val isEmpty:Boolean get() = endMs < 0

    companion object {
        val empty = RangeMs(0L, -1L)

        /**
         * 無効化領域を除く再生時間を取得
         */
        fun List<RangeMs>.totalLengthMs(durationMs: Long): Long {
            return this.fold(0L) { acc, range ->
                acc + range.lengthMs(durationMs)
            }
        }

        fun List<RangeMs>.outlineRange(durationMs: Long): RangeMs {
            if (this.isEmpty()) return RangeMs(0L, 0L)
            val start = this.minOf { it.startMs }
            val end = this.maxOf { it.startMs + it.lengthMs(durationMs) }
            return RangeMs(start, end)
        }
    }
}