package io.github.toyota32k.media.lib.converter

interface ITrimmingRangeList {
    val isEmpty:Boolean
    val isNotEmpty:Boolean get() = !isEmpty

    /**
     * 開区間を naturalDurationで閉じる
     */
    fun closeBy(naturalDurationUs: Long)

    /**
     * トリミング後の再生時間
     */
    val trimmedDurationUs:Long

    /**
     * トリミング前のポジションをトリミング後のポジションに変換
     */
    fun getPositionInTrimmedDuration(positionUs:Long):Long

    enum class PositionState {
        VALID,
        OUT_OF_RANGE,
        END,
    }

    fun positionState(positionUs: Long):PositionState

//    fun isEnd(positionUs: Long):Boolean
//
//    fun isValidPosition(positionUs: Long):Boolean

    fun getNextValidPosition(positionUs: Long):TrimmingRange?

    companion object {
        fun empty(): ITrimmingRangeList {
            return TrimmingRangeListImpl()
        }
    }
}

