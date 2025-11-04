package io.github.toyota32k.media.lib.converter

import io.github.toyota32k.media.lib.report.Report
import kotlinx.coroutines.CancellationException
import kotlin.text.appendLine

/**
 * Converterの結果（成功/失敗/キャンセル）を返すためのデータクラス
 */
data class ConvertResult(val succeeded:Boolean, val adjustedTrimmingRangeList: ITrimmingRangeList?, val report:Report?, val cancelled:Boolean, val errorMessage:String?, val exception:Throwable?) {
    companion object {
        fun succeeded(adjustedTrimmingRangeList: ITrimmingRangeList, report:Report):ConvertResult {
            return ConvertResult(true, adjustedTrimmingRangeList, report, false, null, null)
        }
        val cancelled:ConvertResult
            get() = ConvertResult(false, null, null, true, null, null)
        fun error(exception:Throwable, errorMessage:String?=null):ConvertResult {
            return if(exception is CancellationException) cancelled else ConvertResult(false, null, null, false, errorMessage ?: exception.message, exception)
        }
    }
    val actualSoughtMap : IActualSoughtMap?
        get() = adjustedTrimmingRangeList as? IActualSoughtMap


    // for debug log
    override fun toString(): String {
        return StringBuilder().apply {
            append("Convert Result: ")
            when {
                succeeded -> {
                    appendLine("Succeeded")
                    if (report != null) {
                        appendLine(report.toString())
                    }
                }
                cancelled -> {
                    appendLine("Cancelled")
                }
                exception != null -> {
                    appendLine("Failed")
                    appendLine(exception.toString())
                }
                errorMessage != null -> {
                    appendLine("Failed")
                    appendLine(errorMessage)
                }
                else -> {
                    appendLine("Failed")
                    appendLine("Unknown Error")
                }
            }
        }.toString()
    }
}