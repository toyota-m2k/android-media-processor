package io.github.toyota32k.media.lib.types

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.processor.contract.ISoughtMap
import io.github.toyota32k.media.lib.processor.contract.SoughtPosition
import io.github.toyota32k.media.lib.types.RangeUs.Companion.formatAsUs

/**
 * @param   requestUs   要求された位置 (us) ... ソース動画ファイル上の位置
 * @param   actualUs    実際の位置 (us)    ... ソース動画ファイル上の位置
 * @param   naturalDurationUs   出力位置   ... 実際にmuxerに書き込んだときの naturalDuration
 */
class SoughtMap(val durationUs:Long, enabledRanges:List<RangeUs>) : ISoughtMap {
    override val soughtPositionList = mutableListOf<SoughtPosition>()
    private val enabledRanges = enabledRanges.map {
        if (it.endUs>0 && it.endUs<Long.MAX_VALUE) { it } else RangeUs(it.startUs, durationUs)
    }

    fun put(requestUs:Long, actualUs:Long, presentationTimeUs:Long) {
//        val sp = SoughtPosition(requestUs, actualUs, presentationTimeUs).apply {
//            Processor.logger.debug("put: $this")
//        }
//        soughtPositionList.add(sp)
        soughtPositionList.add(SoughtPosition(requestUs, actualUs, presentationTimeUs))
    }

    override fun correctPositionUs(timeUs:Long):Long {
        enabledRanges.firstOrNull { it.startUs <= timeUs && timeUs <= it.endUs } ?: return -1
        var prev:SoughtPosition? = null
        for (s in soughtPositionList) {
//            if (timeUs == s.requestUs) {
//                return s.presentationTimeUs + (s.requestUs-s.actualUs).coerceAtLeast(0)
//            }
            if (timeUs <= s.requestUs) {
                break
            }
            prev = s
        }
        if (prev!=null) {
//            return prev.presentationTimeUs + ((timeUs-prev.requestUs) + (prev.requestUs-prev.actualUs)).coerceAtLeast(0)
            Processor.logger.debug("presentationTime=${prev.presentationTimeUs.formatAsUs()} (delta=${(timeUs - prev.actualUs).formatAsUs()}) ofs=${(timeUs-prev.requestUs).formatAsUs()} corr=${(prev.requestUs-prev.actualUs).formatAsUs()} ... $prev")
            return prev.presentationTimeUs + (timeUs - prev.actualUs).coerceAtLeast(0)
        } else {
            return 0
        }
    }
}