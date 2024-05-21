package io.github.toyota32k.media.lib.converter

import kotlinx.coroutines.CancellationException

/**
 * Converterの結果（成功/失敗/キャンセル）を返すためのデータクラス
 */
data class ConvertResult(val succeeded:Boolean, val adjustedTrimmingRangeList: ITrimmingRangeList?, val cancelled:Boolean, val errorMessage:String?, val exception:Throwable?) {
    companion object {
        fun succeeded(adjustedTrimmingRangeList: ITrimmingRangeList):ConvertResult {
            return ConvertResult(true, adjustedTrimmingRangeList, false, null, null)
        }
        val cancelled:ConvertResult
            get() = ConvertResult(false, null, true, null, null)
        fun error(exception:Throwable):ConvertResult {
            return if(exception is CancellationException) cancelled else ConvertResult(false, null, false, exception.message,exception)
        }
    }
}