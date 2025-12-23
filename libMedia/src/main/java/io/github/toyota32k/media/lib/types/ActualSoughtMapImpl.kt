package io.github.toyota32k.media.lib.types

import io.github.toyota32k.media.lib.legacy.converter.ITrimmingRangeList
import io.github.toyota32k.media.lib.processor.contract.IActualSoughtMap

class ActualSoughtMapImpl : IActualSoughtMap {
    // 位置補正情報
    private var actualSoughtMap = mutableMapOf<Long,Long>()
    var durationMs:Long = 0L
        private set

    /**
     * 切り取り位置の補正
     * splitAtMs や startMs で指定した位置と実際に切り取られた位置（キーフレーム位置）は異なる可能性がある。
     */
    override fun correctPositionUs(timeUs: Long): Long {
        return actualSoughtMap[timeUs] ?: timeUs
    }

    override fun exportToMap(map: MutableMap<Long, Long>) {
        map.putAll(actualSoughtMap)
    }

    override val entries: Set<Map.Entry<Long, Long>> get() = actualSoughtMap.entries

    /**
     * trim() に使った Array<RangeMs> を一括補正する。
     * trim()後の位置ではなく、元動画のどこで実際に分割したかを示す値を返す。
     * ConvertResult#adjustedTrimmingRangeList と型互換
     */
    override fun adjustedRangeList(ranges: List<RangeMs>): ITrimmingRangeList {
        return super.adjustedRangeList(durationMs, ranges)
    }
    fun setDurationMs(durationMs:Long) {
        this.durationMs = durationMs
    }
    fun addPosition(at:Long, pos:Long) {
//        actualSoughtMap[rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
        actualSoughtMap[at] = if(pos>=0) pos else at
    }

    fun clear() {
        actualSoughtMap.clear()
        durationMs = 0L
    }

    fun clone(): ActualSoughtMapImpl {
        return ActualSoughtMapImpl().also { copy ->
            copy.actualSoughtMap.putAll(actualSoughtMap)
            copy.durationMs = durationMs
        }
    }
}