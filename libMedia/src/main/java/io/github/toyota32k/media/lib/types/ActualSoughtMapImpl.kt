package io.github.toyota32k.media.lib.types

import io.github.toyota32k.media.lib.legacy.converter.ITrimmingRangeList
import io.github.toyota32k.media.lib.processor.contract.IActualSoughtMap
import io.github.toyota32k.media.lib.types.RangeUs.Companion.us2ms

class ActualSoughtMapImpl(val durationUs:Long, override val outlineRangeUs: RangeUs) : IActualSoughtMap {
    // 位置補正情報
    private var actualSoughtMap = mutableMapOf<Long,Long>()
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
        return super.adjustedRangeList(durationUs.us2ms(), ranges)
    }
    fun addPosition(atUs:Long, posUs:Long) {
//        actualSoughtMap[rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
        actualSoughtMap[atUs] = if(posUs>=0) posUs else atUs
    }

    fun clear() {
        actualSoughtMap.clear()
    }

    fun clone(): ActualSoughtMapImpl {
        return ActualSoughtMapImpl(durationUs, outlineRangeUs).also { copy ->
            copy.actualSoughtMap.putAll(actualSoughtMap)
        }
    }
}