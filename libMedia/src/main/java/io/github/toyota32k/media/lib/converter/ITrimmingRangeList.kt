package io.github.toyota32k.media.lib.converter

/**
 * トリミング範囲のリスト
 */
interface ITrimmingRangeList {
    val list: List<TrimmingRange>
    val isEmpty:Boolean
    val isNotEmpty:Boolean get() = !isEmpty

    val naturalDurationUs: Long

    fun addRange(startUs:Long, endUs:Long)

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
            return TrimmingRangeListImpl()
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
interface ITrimmingRangeKeeper : ITrimmingRangeList {
    var limitDurationUs: Long
    /**
     * トリミング前のポジションをトリミング後のポジションに変換
     */
    fun getPositionInTrimmedDuration(positionUs:Long):Long

    fun positionState(positionUs: Long, ):PositionState

    fun getNextValidPosition(positionUs: Long):TrimmingRange?
}