package io.github.toyota32k.media.lib.converter

class TrimmingRangeList {
    val list = mutableListOf<TrimmingRange>()

    fun addRange(startUs:Long, endUs:Long) {
        val s =  trimmingStart
        val e =  trimmingEnd

        if(list.isNotEmpty() && e==0L) {
            throw IllegalStateException("previous range is not terminated.")
        }
        if(endUs!=0L && startUs>=endUs) {
            throw IllegalArgumentException("trimming range is invalid: start=$startUs, end=$endUs")
        }
        if(startUs<s) {
            throw IllegalArgumentException("trimming range must be sorted in caller.")
        }
        if(e!=0L && startUs<e) {
            throw IllegalArgumentException("trimming range must not be wrapped over.")
        }
        list.add(TrimmingRange(startUs,endUs))
    }

    val trimmingStart:Long
        get() = list.firstOrNull()?.startUs ?: 0L
    val trimmingEnd:Long
        get() = list.lastOrNull()?.endUs ?: 0L
}