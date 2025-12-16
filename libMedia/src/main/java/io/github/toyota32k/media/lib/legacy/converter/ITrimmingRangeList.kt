package io.github.toyota32k.media.lib.legacy.converter

import io.github.toyota32k.media.lib.processor.contract.IActualSoughtMap
import io.github.toyota32k.media.lib.types.RangeMs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.ms2us
import io.github.toyota32k.media.lib.types.RangeUs.Companion.us2ms


/**
 * トリミング範囲のリスト
 */
interface ITrimmingRangeList {
    val list: List<TrimmingRange>
    val isEmpty:Boolean
    val isNotEmpty:Boolean get() = !isEmpty

    val naturalDurationUs: Long

    fun addRange(startUs:Long, endUs:Long)

    fun clear()

    /**
     * 開区間を naturalDurationで閉じる
     */
    fun closeBy(naturalDurationUs: Long)

    /**
     * トリミング後の再生時間
     */
    val trimmedDurationUs:Long

    companion object {
        fun empty(): ITrimmingRangeList {
            return TrimmingRangeList()
        }
    }
}

enum class PositionState {
    VALID,
    OUT_OF_RANGE,
    END,
}

/**
 * トリミング範囲を管理するインターフェース
 */
interface ITrimmingRangeKeeper : ITrimmingRangeList, IActualSoughtMap {
    var limitDurationUs: Long

    /**
     * トリミング前のポジションをトリミング後のポジションに変換
     */
    fun getPositionInTrimmedDuration(positionUs:Long):Long

    fun positionState(positionUs: Long, ):PositionState

    fun getNextValidPosition(positionUs: Long):TrimmingRange?
}