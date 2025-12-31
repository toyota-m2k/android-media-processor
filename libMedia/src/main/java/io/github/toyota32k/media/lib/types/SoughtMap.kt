package io.github.toyota32k.media.lib.types

import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.processor.contract.ISoughtMap
import io.github.toyota32k.media.lib.processor.contract.SoughtPosition

/**
 * @param   durationUs   natural duration
 * @param   enabledRanges   再生位置の有効範囲リスト
 */
class SoughtMap(val durationUs:Long, enabledRanges:List<RangeUs>) : ISoughtMap {
    override val soughtPositionList = mutableListOf<SoughtPosition>()
    private val enabledRanges = enabledRanges.map {
        if (it.endUs>0 && it.endUs<Long.MAX_VALUE) { it } else RangeUs(it.startUs, durationUs)
    }

    fun put(requestUs:Long, actualUs:Long, presentationTimeUs:Long) {
        val sp = SoughtPosition(requestUs, actualUs, presentationTimeUs)
        Processor.logger.debug(sp.toString())
        soughtPositionList.add(sp)
    }

    override fun correctPositionUs(timeUs:Long):Long {
        enabledRanges.firstOrNull { it.startUs <= timeUs && timeUs <= it.endUs } ?: return -1
        var prev:SoughtPosition? = null
        for (s in soughtPositionList) {
            if (timeUs == s.requestUs) {
                // 要求位置がSoughtMapと完全一致
                return s.presentationTimeUs + (s.requestUs-s.actualUs).coerceAtLeast(0)
            }
            else if (timeUs < s.requestUs) {
                // 要求位置が１つ前の区間に含まれている
                break
            }
            prev = s
        }
        if (prev!=null) {
            return prev.presentationTimeUs + (timeUs - prev.actualUs).coerceAtLeast(0)
        } else {
            return 0
        }
    }
}