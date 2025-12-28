package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.legacy.converter.ITrimmingRangeList
import io.github.toyota32k.media.lib.legacy.converter.TrimmingRangeList
import io.github.toyota32k.media.lib.types.RangeMs
import io.github.toyota32k.media.lib.types.RangeUs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.ms2us
import io.github.toyota32k.media.lib.types.RangeUs.Companion.us2ms

interface IActualSoughtMap {
    /**
     * map の有効範囲
     * requestedRangeUs
     */
    val outlineRangeUs: RangeUs

    /**
     * 切り取り位置の補正
     * splitAtMs や startMs で指定した位置と実際に切り取られた位置（キーフレーム位置）は異なる可能性がある。
     */
    fun correctPositionUs(timeUs:Long):Long

    fun correctPositionMs(timeMs:Long):Long {
        return correctPositionUs(timeMs.ms2us()).us2ms()
    }

    fun exportToMap(map: MutableMap<Long, Long>)
    val entries: Set<Map.Entry<Long, Long>>

    /**
     * trim() に使った Array<RangeMs> を一括補正する。
     * trim()後の位置ではなく、元動画のどこで実際に分割したかを示す値を返す。
     * ConvertResult#adjustedTrimmingRangeList と型互換
     */
    fun adjustedRangeList(durationMs:Long, ranges:List<RangeMs>) : ITrimmingRangeList {
        return TrimmingRangeList().apply {
            for (range in ranges) {
                if (range.endMs > 0) {
                    addRange(correctPositionUs(range.startMs.ms2us()), correctPositionUs(range.endMs.ms2us()))
                } else {
                    addRange(correctPositionUs(range.startMs.ms2us()), correctPositionUs(durationMs.ms2us()))
                }
            }
        }
    }

    fun adjustedRangeList(ranges:List<RangeMs>) : ITrimmingRangeList
}