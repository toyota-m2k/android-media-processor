package io.github.toyota32k.media.lib.converter

import io.github.toyota32k.media.lib.processor.misc.RangeUs.Companion.ms2us
import io.github.toyota32k.media.lib.processor.misc.RangeUs.Companion.us2ms
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

class EmptyRangeException : IllegalStateException("no range")

class TrimmingRangeList(originalList:List<TrimmingRange>?=null) : ITrimmingRangeList {
    override val list: MutableList<TrimmingRange> = originalList?.toMutableList() ?: mutableListOf()
    override val isEmpty: Boolean
        get() = list.isEmpty()
    override var naturalDurationUs: Long = -1L

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

    override fun clear() {
        list.clear()
    }
    override fun addRange(startUs:Long, endUs:Long) {
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

    private var mTrimmedDuration = -1L

    override val trimmedDurationUs: Long get() {
        if (mTrimmedDuration<0) {
            mTrimmedDuration = if (list.isEmpty()) {
                naturalDurationUs
            } else {
                list.fold(0L) { acc, t -> acc + t.durationUs }
            }
        }
        return mTrimmedDuration
    }

    class Builder(val mTrimmingRangeList: TrimmingRangeList = TrimmingRangeList()) {
        constructor(rangeList: List<TrimmingRange>) : this(TrimmingRangeList(rangeList))

        var mTrimStartUs:Long = 0L
        var mTrimEndUs:Long = 0L

        /**
         * トリミング範囲を追加
         * @param startMs 開始位置 (ms)
         * @param endMs 終了位置 (ms)   0なら最後まで
         */
        fun addRangeMs(startMs:Long, endMs:Long) = apply {
            mTrimmingRangeList.addRange(startMs.ms2us(), endMs.ms2us())
        }

        /**
         * トリミング範囲を追加
         * @param startMs 開始位置 (us)
         * @param endMs 終了位置 (us)   0なら最後まで
         */
        fun addRangeUs(startUs:Long, endUs:Long) = apply {
            mTrimmingRangeList.addRange(startUs, endUs)
        }

        /**
         * トリミング範囲を追加
         * @param start 開始位置 (Duration) nullなら先頭から
         * @param end 終了位置 (Duration) nullなら最後まで
         */
        fun addRange(start:Duration?, end:Duration?)
                = addRangeMs(start?.inWholeMilliseconds ?: 0L, end?.inWholeMilliseconds ?: 0L)

        /**
         * トリミング範囲を追加
         * @param range トリミング範囲 (ms)
         */
        fun addRangeMs(range: RangeMs)
            = addRangeMs(range.startMs, range.endMs)

        fun addRangeUs(range: TrimmingRange)
                = addRangeUs(range.startUs, range.endUs)

        /**
         * トリミング範囲を一括追加
         */
        fun addRangesMs(ranges:List<RangeMs>) = apply {
            ranges.forEach {
                addRangeMs(it)
            }
        }
        fun addRangesUs(ranges:List<TrimmingRange>) = apply {
            ranges.forEach {
                addRangeUs(it)
            }
        }

        /**
         * トリミング範囲をクリアして一括設定
         */
        fun setRangesMs(ranges:List<RangeMs>) = apply {
            mTrimmingRangeList.clear()
            addRangesMs(ranges)
        }
        fun setRangesUs(ranges:List<TrimmingRange>) = apply {
            mTrimmingRangeList.clear()
            addRangesUs(ranges)
        }

        /**
         * トリミング範囲をクリア
         */
        fun reset() = apply {
            mTrimmingRangeList.clear()
        }

        /**
         * 指定された位置より前をカット
         */
        fun startFromMs(timeMs:Long) = apply {
            mTrimStartUs = timeMs.ms2us()
        }
        fun startFromUs(timeUs:Long) = apply {
            mTrimStartUs = timeUs
        }

        /**
         * 指定された位置より前をカット
         */
        fun startFrom(time: Duration)
                = startFromMs(time.inWholeMilliseconds)

        /**
         * 指定された位置より後をカット
         */
        fun endAtMs(timeMs:Long) = apply {
            mTrimEndUs = timeMs.ms2us()
        }
        fun endAtUs(timeUs:Long) = apply {
            mTrimEndUs = timeUs
        }
        /**
         * 指定された位置より後をカット
         */
        fun endTo(time: Duration)
                = endAtMs(time.inWholeMilliseconds)

        private fun prepareTrimmingRanges(rangeList: List<TrimmingRange>, startUs: Long, endUs: Long) : List<TrimmingRange> {
            return mutableListOf<TrimmingRange>().also { ranges ->
                if (rangeList.isEmpty()) {
                    ranges.add(TrimmingRange(startUs, endUs))
                } else {
                    for (range in rangeList) {
                        if (range.endUs <= startUs || endUs <= range.startUs) {
                            continue
                        }
                        val start = max(startUs, range.startUs)
                        val end = min(endUs, range.endUs)
                        ranges.add(TrimmingRange(start, end))
                    }
                }
            }
        }

        @Throws(EmptyRangeException::class)
        fun build(): TrimmingRangeList {
            val ranges = if (mTrimStartUs==0L && mTrimEndUs==0L) {
                // trimStart/trimEndが設定されていない
                mTrimmingRangeList.list
            } else if (mTrimmingRangeList.isEmpty) {
                // trimStart/trimEnd のみが設定されている
                mTrimmingRangeList.list.toMutableList().apply {
                    add(TrimmingRange(mTrimStartUs, mTrimEndUs))
                }
            } else {
                // trimStartとtrimEndが、trimmingRanges とともに指定されている --> マージ（intersect)
                // マージした結果が空になる可能性がある --> 空なら null を返す
                prepareTrimmingRanges(mTrimmingRangeList.list, mTrimStartUs, mTrimEndUs).takeIf { it.isNotEmpty() } ?: throw EmptyRangeException()
            }
            return TrimmingRangeList(ranges)
        }

        fun tryBuild(): ITrimmingRangeList? {
            return try {
                build()
            } catch(_:EmptyRangeException) {
                null
            }
        }

        fun canBuild():Boolean {
            return tryBuild() != null
        }
    }

    companion object {
        fun List<TrimmingRange>.toRangeMsList(): List<RangeMs> {
            return this.map { range ->
                RangeMs(range.startUs.us2ms(), range.endUs.us2ms())
            }
        }
        fun List<RangeMs>.toRangeUsList(): List<TrimmingRange> {
            return this.map { range ->
                TrimmingRange(range.startMs.ms2us(), range.endMs.us2ms())
            }
        }
        fun List<RangeMs>.toTrimmingRangeList(): TrimmingRangeList {
            return TrimmingRangeList(this.toRangeUsList())
        }
    }
}




