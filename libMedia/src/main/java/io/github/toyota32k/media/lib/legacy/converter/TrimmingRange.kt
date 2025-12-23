package io.github.toyota32k.media.lib.legacy.converter

import java.lang.IllegalStateException

/**
 * トリミング範囲
 */
data class TrimmingRange(val startUs:Long = 0L, val endUs:Long = 0L) {
    val hasStart = startUs>0L
    val hasEnd = endUs>0L
    val hasAny = hasStart||hasEnd
    @Suppress("unused")
    val isEmpty = !hasAny
    var naturalDurationUs:Long = -1L

    fun closeBy(naturalDurationUs: Long):Boolean {
        this.naturalDurationUs = naturalDurationUs
        return startUs < naturalDurationUs
    }

    fun checkStart(timeUs:Long):Boolean {
        return startUs==0L || startUs <= timeUs
    }
    fun checkEnd(timeUs:Long):Boolean {
        return endUs==0L || timeUs<=endUs
    }

    fun contains(timeUs:Long):Boolean {
        return checkStart(timeUs) && checkEnd(timeUs)
    }

    val actualEndUs :Long
        get() {
            if(naturalDurationUs<0L) throw IllegalStateException("call closeBy() first.")
            return if (endUs == 0L) naturalDurationUs else endUs
        }

    val durationUs:Long
        get() = actualEndUs - startUs

    companion object {
        @Suppress("unused")
        val Empty = TrimmingRange()
    }
}