package io.github.toyota32k.media.lib.converter

import kotlinx.coroutines.CancellationException

/**
 * Converterの結果（成功/失敗/キャンセル）を返すためのデータクラス
 */
data class ConvertResult(val succeeded:Boolean, val cancelled:Boolean, val errorMessage:String?, val exception:Throwable?) {
    constructor() : this(true, false, null, null)
    companion object {
        val succeeded:ConvertResult
            get() = ConvertResult()
        val cancelled:ConvertResult
            get() = ConvertResult(false, true, null, null)
        fun error(exception:Throwable):ConvertResult {
            return if(exception is CancellationException) cancelled else ConvertResult(false, false, exception.message,exception)
        }
    }
}