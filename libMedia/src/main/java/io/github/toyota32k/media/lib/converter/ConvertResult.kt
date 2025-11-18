package io.github.toyota32k.media.lib.converter

import io.github.toyota32k.media.lib.report.Report
import kotlinx.coroutines.CancellationException
import kotlin.text.appendLine

interface IResultBase {
    val succeeded:Boolean
    val exception:Throwable?
    val errorMessage: String?
    val cancelled : Boolean get() = exception is kotlin.coroutines.cancellation.CancellationException || (!succeeded && exception==null)
    val hasError: Boolean get() = exception != null && !cancelled
}

interface IConvertResult : IResultBase {
    val outputFile: IOutputMediaFile?
    val actualSoughtMap: IActualSoughtMap?
    val requestedRangeMs: RangeMs
}

/**
 * Converterの結果（成功/失敗/キャンセル）を返すためのデータクラス
 */
data class ConvertResult(
    override val succeeded:Boolean,
    override val outputFile: IOutputMediaFile?,
    override val requestedRangeMs: RangeMs,
    val adjustedTrimmingRangeList: ITrimmingRangeList?,
    val report:Report?,
    override val cancelled:Boolean,
    override val errorMessage:String?,
    override val exception:Throwable?)
    : IConvertResult {

    companion object {
        fun succeeded(outputFile:IOutputMediaFile, requestedRangeMs:RangeMs, adjustedTrimmingRangeList: ITrimmingRangeList, report:Report):ConvertResult {
            return ConvertResult(true, outputFile, requestedRangeMs, adjustedTrimmingRangeList, report, false, null, null)
        }
        val cancelled:ConvertResult
            get() = ConvertResult(false, null,RangeMs.empty, null, null,true, null, null)
        fun error(exception:Throwable, errorMessage:String?=null):ConvertResult {
            return if(exception is CancellationException) cancelled else ConvertResult(false, null, RangeMs.empty, null, null, false, errorMessage ?: exception.message, exception)
        }
    }
    override val actualSoughtMap : IActualSoughtMap?
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