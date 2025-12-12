package io.github.toyota32k.media.lib.processor.misc

import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.utils.RangeMs
import io.github.toyota32k.media.lib.utils.RangeUs
import io.github.toyota32k.media.lib.utils.RangeUs.Companion.formatAsUs
import io.github.toyota32k.media.lib.utils.RangeUs.Companion.ms2us
import kotlin.math.max
import kotlin.math.min

class EmptyRangeException : IllegalStateException("no range")

class RangeUsListBuilder(val throwOnAddError:Boolean = false) {
    private val logger = Processor.logger
    private val list: MutableList<RangeUs> = mutableListOf()
//    private var mTrimStartUs:Long = 0L
//    private var mTrimEndUs:Long = 0L

    fun clear() {
        list.clear()
    }

    private fun getRangeErrorString(startUs:Long, endUs:Long):String? {
        if (startUs<0) {
            return "startUs is negative: ${startUs.formatAsUs()}"
        }
        if (0<endUs && endUs!=Long.MAX_VALUE && endUs<=startUs) {
            return "endUs (${endUs.formatAsUs()}) must be greater than startUs (${startUs.formatAsUs()})"
        }

        if (list.isEmpty()) {
            return null // ok
        }

        val e =  list.last().endUs
        if (e<=0 || e==Long.MAX_VALUE) {
            return "previous range is not terminated and no more ranges can be added."
        }
        if (startUs<e) {
            return "trimming ranges must not be wrapped over."
        }
        return null
    }

    private fun checkListBeforeAdd(startUs:Long, endUs:Long): Boolean {
        val error = getRangeErrorString(startUs, endUs)
        if (error != null) {
            if (throwOnAddError) {
                throw IllegalArgumentException(error)
            } else {
                logger.error(error)
                return false
            }
        }
        return true
    }

    fun addRangeUs(startUs:Long, endUs:Long) = apply {
        if (checkListBeforeAdd(startUs, endUs)) {
            list.add(RangeUs(startUs, endUs))
        }
    }
    fun addRangeUs(range: RangeUs) = apply {
        if (checkListBeforeAdd(range.startUs, range.endUs)) {
            addRangeUs(range.startUs, range.endUs)
        }
    }
    fun addRangeMs(startMs:Long, endMs:Long) = apply {
        val startUs = startMs.ms2us()
        val endUs = endMs.ms2us()
        addRangeUs(startUs, endUs)
    }
    fun addRangeMs(range: RangeMs) = apply {
        addRangeMs(range.startMs, range.endMs)
    }

    /**
     * トリミング範囲を一括追加
     */
    fun addRangesMs(ranges:List<RangeMs>) = apply {
        ranges.forEach {
            addRangeMs(it)
        }
    }
    fun addRangesUs(ranges:List<RangeUs>) = apply {
        ranges.forEach {
            addRangeUs(it)
        }
    }

    /**
     * トリミング範囲をクリアして一括設定
     */
    fun setRangesMs(ranges:List<RangeMs>) = apply {
        list.clear()
        addRangesMs(ranges)
    }
    fun setRangesUs(ranges:List<RangeUs>) = apply {
        list.clear()
        addRangesUs(ranges)
    }

    /**
     * トリミング範囲をクリア
     */
    fun reset() = apply {
        list.clear()
    }

//    /**
//     * 指定された位置より前をカット
//     */
//    fun startFromMs(timeMs:Long) = apply {
//        mTrimStartUs = timeMs.ms2us()
//    }
//    fun startFromUs(timeUs:Long) = apply {
//        mTrimStartUs = timeUs
//    }
//    fun startFrom(time: Duration)
//            = startFromUs(time.inWholeMicroseconds)
//
//    /**
//     * 指定された位置より後をカット
//     */
//    fun endAtMs(timeMs:Long) = apply {
//        mTrimEndUs = timeMs.ms2us()
//    }
//    fun endAtUs(timeUs:Long) = apply {
//        mTrimEndUs = timeUs
//    }
//    fun endAt(time: Duration)
//            = endAtUs(time.inWholeMicroseconds)

    fun toRangeUsListWithClipUs(clipStartUs:Long=0L, clipEndUs:Long=0L):List<RangeUs> {
        val clipEndUs = if (clipEndUs<=0) Long.MAX_VALUE else clipEndUs
        return if (clipStartUs==0L && clipEndUs==Long.MAX_VALUE) {
            // trimStart/trimEndが設定されていない
            if (list.isEmpty()) {
                listOf(RangeUs.FULL)
            } else {
                list
            }
        } else if (list.isEmpty()) {
            // trimStart/trimEnd のみが設定されている
            list.toMutableList().apply {
                add(RangeUs(clipStartUs, clipEndUs))
            }
        } else {
            // trimStartとtrimEndが、RangeUss とともに指定されている --> マージ（intersect)
            // マージした結果が空になる可能性がある --> 空なら null を返す
            prepareRangeUs(list, clipStartUs, clipEndUs).takeIf { it.isNotEmpty() } ?: throw EmptyRangeException()
        }
    }

    private fun prepareRangeUs(rangeList: List<RangeUs>, startUs: Long, endUs: Long) : List<RangeUs> {
        return mutableListOf<RangeUs>().also { ranges ->
            if (rangeList.isEmpty()) {
                ranges.add(RangeUs(startUs, endUs))
            } else {
                for (range in rangeList) {
                    if (range.endUs <= startUs || endUs <= range.startUs) {
                        continue
                    }
                    val start = max(startUs, range.startUs)
                    val end = min(endUs, range.endUs)
                    ranges.add(RangeUs(start, end))
                }
            }
        }
    }
    
    fun toRangeUsList():List<RangeUs> {
        return toRangeUsListWithClipUs()
    }
    fun toRangeUsListWithClipMs(clipStartMs:Long=0L, clipEndMs:Long=Long.MAX_VALUE):List<RangeUs> {
        return toRangeUsListWithClipUs(clipStartMs.ms2us(), clipEndMs.ms2us())
    }
    fun toRangeUsListBeforeUs(timeUs:Long):List<RangeUs> {
        return toRangeUsListWithClipUs(0L, timeUs)
    }
    fun toRangeUsListBeforeMs(timeMs:Long):List<RangeUs> {
        return toRangeUsListBeforeUs(timeMs.ms2us())
    }
    fun toRangeUsListAfterUs(timeUs:Long):List<RangeUs> {
        return toRangeUsListWithClipUs(timeUs, Long.MAX_VALUE)
    }
    fun toRangeUsListAfterMs(timeMs:Long):List<RangeUs> {
        return toRangeUsListAfterUs(timeMs.ms2us())
    }
}




