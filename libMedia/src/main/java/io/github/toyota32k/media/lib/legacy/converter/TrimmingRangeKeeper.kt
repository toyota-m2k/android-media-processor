package io.github.toyota32k.media.lib.legacy.converter

import io.github.toyota32k.media.lib.types.RangeMs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.us2ms
import kotlin.math.min

class TrimmingRangeKeeper(val trimmingRangeList: ITrimmingRangeList = TrimmingRangeList()): ITrimmingRangeKeeper, ITrimmingRangeList by trimmingRangeList {
    // limit duration
    override var limitDurationUs: Long = 0L    // 0: no limit
    private val actualSoughtMap: Map<Long, Long> = mutableMapOf()
    override fun correctPositionUs(timeUs:Long):Long {
        return actualSoughtMap[timeUs] ?: timeUs
    }
    override fun exportToMap(map: MutableMap<Long, Long>) {
        map.putAll(actualSoughtMap)
    }
    override val entries: Set<Map.Entry<Long, Long>> get() = actualSoughtMap.entries

    override fun adjustedRangeList(ranges:List<RangeMs>) : ITrimmingRangeList {
        if (naturalDurationUs<0) throw IllegalStateException("call closeBy() in advance.")
        return super.adjustedRangeList(naturalDurationUs.us2ms(), ranges)
    }

    fun putSoughtPosition(req:Long, act:Long) {
        (actualSoughtMap as MutableMap)[req] = act
    }

    private val rawTrimmedDuration: Long
        get() = trimmingRangeList.trimmedDurationUs

    override val isEmpty: Boolean
        get() = trimmingRangeList.isEmpty && limitDurationUs<=0L

    override val trimmedDurationUs: Long
        get() = if (limitDurationUs > 0) min(limitDurationUs, rawTrimmedDuration) else rawTrimmedDuration

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

    override fun positionState(positionUs: Long): PositionState {
        return if(list.isEmpty()) {
            when {
                positionUs<0 -> PositionState.END        // positionUs < 0 : EOS
                // metadata から得た Durationが間違っているかもしれないので、このチェックはやめて、EOS まで読み込む
                // Chromebook のカメラで撮影したウソクソメタ情報問題を回避できるかと思ったけど、Extractor が Duration位置で読み込みをやめてしまうのでダメだった。
                // いずれにしても、このチェックは Extractor に任せたので良さそう。
                // positionUs>=naturalDurationUs -> ITrimmingRangeList.PositionState.END
                else -> PositionState.VALID
            }
        } else {
            if(naturalDurationUs<0) throw java.lang.IllegalStateException("call closeBy() in advance.")
            if (0 < limitDurationUs && limitDurationUs < rawTrimmedDuration) {
                val trimmedPos = getPositionInTrimmedDuration(positionUs)
                if (trimmedPos >= limitDurationUs) {
                    return PositionState.END
                }
            }
            when {
                positionUs<0 -> PositionState.END
                list.firstOrNull { it.contains(positionUs) } != null -> PositionState.VALID
                list.last().actualEndUs < positionUs -> PositionState.END
                else -> PositionState.OUT_OF_RANGE
            }
        }
    }

    override fun getNextValidPosition(positionUs: Long): TrimmingRange? {
        if(list.isEmpty()) return null
        return list.firstOrNull { positionUs < it.startUs }
    }

    companion object {
        val empty: ITrimmingRangeKeeper get() = TrimmingRangeKeeper()
        fun ITrimmingRangeList.toKeeper(): ITrimmingRangeKeeper {
            return TrimmingRangeKeeper(this)
        }
    }
}
