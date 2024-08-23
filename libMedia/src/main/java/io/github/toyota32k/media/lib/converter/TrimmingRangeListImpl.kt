package io.github.toyota32k.media.lib.converter

class TrimmingRangeListImpl : ITrimmingRangeList {
    override val list = mutableListOf<TrimmingRange>()
    override val isEmpty: Boolean
        get() = list.isEmpty()
    private var naturalDurationUs: Long = -1L

    override fun closeBy(naturalDurationUs:Long) {
        this.naturalDurationUs = naturalDurationUs
        val itr = list.listIterator()
        while(itr.hasNext()) {
            val r = itr.next()
            if (!r.closeBy(naturalDurationUs)) {
                itr.remove()
            }
        }
    }

    fun addRange(startUs:Long, endUs:Long) {
        val s =  list.firstOrNull()?.startUs ?: 0L
        val e =  list.lastOrNull()?.endUs ?: 0L

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

//    val trimmingStart:Long
//        get() = list.firstOrNull()?.startUs ?: 0L
//    val trimmingEnd:Long
//        get() = list.lastOrNull()?.actualEndUs ?: naturalDurationUs

    override val trimmedDurationUs: Long get() {
        if(naturalDurationUs<0) throw java.lang.IllegalStateException("call closeBy() in advance.")
        return if(list.isEmpty()) {
            naturalDurationUs
        } else {
            list.fold(0L) { acc, t -> acc + t.durationUs }
        }
    }

    override fun getPositionInTrimmedDuration(positionUs: Long): Long {
        if(list.isEmpty()) return positionUs
        var pos = 0L
        for(t in list) {
            // ------| range-1 |-------| range-2 | -----
            //    V
            //    　　　　V
            //    　　　　         V
            if(t.actualEndUs < positionUs) {
                pos += t.durationUs
            } else {
                if(t.startUs < positionUs) { // && positionUs <= t.actualEndUs
                    pos += (positionUs-t.startUs)
                }
                break
            }
        }
        return pos
    }

//    override fun isEnd(positionUs: Long): Boolean {
//        if(naturalDurationUs<0) throw java.lang.IllegalStateException("call closeBy() in advance.")
//        val trimmingEnd = list.lastOrNull()?.actualEndUs ?: naturalDurationUs
//        return trimmingEnd <= positionUs
//    }
//
//    override fun isValidPosition(positionUs: Long): Boolean {
//        if(naturalDurationUs<0) throw java.lang.IllegalStateException("call closeBy() in advance.")
//        if(list.isEmpty()) return 0<= positionUs && positionUs < naturalDurationUs
//        return list.firstOrNull { it.contains(positionUs) } != null
//    }

    override fun positionState(positionUs: Long): ITrimmingRangeList.PositionState {
        if(naturalDurationUs<0) throw java.lang.IllegalStateException("call closeBy() in advance.")
        return if(list.isEmpty()) {
            when {
                positionUs<0 -> ITrimmingRangeList.PositionState.END        // positionUs < 0 : EOS
                // metadata から得た Durationが間違っているかもしれないので、このチェックはやめて、EOS まで読み込む
                // Chromebook のカメラで撮影したウソクソメタ情報問題を回避できるかと思ったけど、Extractor が Duration位置で読み込みをやめてしまうのでダメだった。
                // いずれにしても、このチェックは Extractor に任せたので良さそう。
                // positionUs>=naturalDurationUs -> ITrimmingRangeList.PositionState.END
                else -> ITrimmingRangeList.PositionState.VALID
            }
        } else {
            when {
                positionUs<0 -> ITrimmingRangeList.PositionState.END
                list.firstOrNull { it.contains(positionUs) } != null -> ITrimmingRangeList.PositionState.VALID
                list.last().actualEndUs < positionUs -> ITrimmingRangeList.PositionState.END
                else -> ITrimmingRangeList.PositionState.OUT_OF_RANGE
            }
        }
    }

    override fun getNextValidPosition(positionUs: Long): TrimmingRange? {
        if(list.isEmpty()) return null
        return list.firstOrNull { positionUs < it.startUs }
    }


}