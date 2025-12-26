package io.github.toyota32k.media.lib.types

import io.github.toyota32k.media.lib.utils.TimeSpan

/**
 * US単位の時間範囲を保持するクラス
 */
class RangeUs(val startUs:Long, val endUs:Long) {
    companion object {
        val FULL = RangeUs(0L, Long.MAX_VALUE)

        fun fromMs(startMs: Long, endMs: Long): RangeUs =
            if (endMs <= 0 || endMs == Long.MAX_VALUE) RangeUs(startMs.ms2us(), Long.MAX_VALUE)
            else RangeUs(startMs.ms2us(), endMs.ms2us())

        fun fromMs(rangeMs: RangeMs): RangeUs =
            fromMs(rangeMs.startMs, rangeMs.endMs)

        fun Long.isValidTime(): Boolean {
            return this >= 0 && this != Long.MAX_VALUE
        }

        fun Long.us2ms(): Long {
            return if (this < 0 || this == Long.MAX_VALUE) Long.MAX_VALUE
            else this / 1000L
        }

        fun Long.ms2us(): Long {
            return if (this < 0 || this == Long.MAX_VALUE) Long.MAX_VALUE
            else this * 1000L
        }

        fun Long.usToTimeSpan(): TimeSpan {
            return TimeSpan(this.us2ms())
        }

        fun Long.msToTimeSpan(): TimeSpan {
            return TimeSpan(this)
        }

        fun Long.formatAsUs(): String {
            return when {
                this < 0 -> "(uav)"
                this == Long.MAX_VALUE -> "(inf)"
                else -> this.usToTimeSpan().formatAutoM()
            }
        }

        fun Long.formatAsMs():String {
            return when {
                this < 0 -> "(uav)"
                this == Long.MAX_VALUE -> "(inf)"
                else -> this.msToTimeSpan().formatAutoM()
            }
        }



        fun List<RangeUs>.toRangeMsList(): List<RangeMs> {
            return this.map { range ->
                RangeMs(range.startUs.us2ms(), range.endUs.us2ms())
            }
        }

        fun List<RangeMs>.toRangeUsList(): List<RangeUs> {
            return this.map { range ->
                RangeUs(range.startMs.ms2us(), range.endMs.ms2us())
            }
        }


        /**
         * 無効化領域を除く再生時間を取得
         */
        fun List<RangeUs>.totalLengthUs(durationUs: Long): Long {
            return this.fold(0L) { acc, range ->
                acc + range.lengthUs(durationUs)
            }
        }

        /**
         * range list の先頭と末端を返す
         *
         * @param durationUs ソース動画の総再生時間 (endUs が不定の場合に使用)
         * @return RangeUs
         */
        fun List<RangeUs>.outlineRangeUs(durationUs: Long): RangeUs {
            if (this.isEmpty()) return RangeUs(0L, 0L)
            val start = this.minOf { it.startUs }
            val end = this.maxOf { it.actualEndUs(durationUs) }
            return RangeUs(start, end)
        }
    }

    fun lengthUs(durationUs:Long):Long {
        val length = actualEndUs(durationUs)
        return if (length==Long.MAX_VALUE) length else length - startUs
    }

    fun actualEndUs(durationUs:Long?):Long {
        return if (endUs<=0L || endUs==Long.MAX_VALUE) durationUs ?: Long.MAX_VALUE else endUs
    }

    fun toRangeMs(): RangeMs {
        return RangeMs(startUs.us2ms(), endUs.us2ms())
    }

    override fun toString():String {
        return "(${startUs.formatAsUs()}-${endUs.formatAsUs()})}"
    }
}