package io.github.toyota32k.media.lib.converter

/**
 * トリミング範囲
 */
data class TrimmingRange(val startUs:Long = 0L, val endUs:Long = 0L) {
    val hasStart = startUs>0L
    val hasEnd = endUs>0L
    val hasAny = hasStart||hasEnd
    val isEmpty = !hasEnd

    fun checkStart(timeUs:Long):Boolean {
        return startUs==0L || startUs <= timeUs
    }
    fun checkEnd(timeUs:Long):Boolean {
        return endUs==0L || timeUs<=endUs
    }

    fun contains(timeUs:Long):Boolean {
        return checkStart(timeUs) && checkEnd(timeUs)
    }

    companion object {
        val Empty = TrimmingRange()
    }
}