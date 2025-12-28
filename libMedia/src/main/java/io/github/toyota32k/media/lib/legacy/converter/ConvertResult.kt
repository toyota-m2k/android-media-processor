package io.github.toyota32k.media.lib.legacy.converter

import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.processor.contract.IActualSoughtMap
import io.github.toyota32k.media.lib.processor.contract.IConvertResult
import io.github.toyota32k.media.lib.processor.contract.ISoughtMap
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.types.RangeMs
import io.github.toyota32k.media.lib.types.RangeUs
import kotlinx.coroutines.CancellationException
import kotlin.text.appendLine

/**
 * Converterの結果（成功/失敗/キャンセル）を返すためのデータクラス
 */
data class ConvertResult(
    override val succeeded:Boolean,
    override val inputFile: IInputMediaFile?,
    override val outputFile: IOutputMediaFile?,
    val requestedRangeMs: RangeMs,
    val adjustedTrimmingRangeList: ITrimmingRangeList?,
    override val report:Report?,
    override val cancelled:Boolean,
    override val errorMessage:String?,
    override val exception:Throwable?)
    : IConvertResult {

    companion object {
        fun succeeded(inputFile: IInputMediaFile, outputFile: IOutputMediaFile, requestedRangeMs: RangeMs, adjustedTrimmingRangeList: ITrimmingRangeList, report:Report):ConvertResult {
            return ConvertResult(true, inputFile, outputFile, requestedRangeMs, adjustedTrimmingRangeList, report, false, null, null)
        }
        fun cancelled(inputFile: IInputMediaFile?):ConvertResult =
            ConvertResult(false, inputFile, null, RangeMs.Companion.empty, null, null,true, null, null)
        fun error(inputFile: IInputMediaFile?, exception:Throwable, errorMessage:String?=null):ConvertResult {
            return if(exception is CancellationException) cancelled(inputFile) else ConvertResult(false, inputFile, null, RangeMs.Companion.empty, null, null, false, errorMessage ?: exception.message, exception)
        }
    }
    @Deprecated("use soughtMap")
    override val actualSoughtMap : IActualSoughtMap?
        get() = adjustedTrimmingRangeList as? IActualSoughtMap

    val requestedRangeUs: RangeUs
        get() = RangeUs.Companion.fromMs(requestedRangeMs)

    override val soughtMap: ISoughtMap? = null

    // for debug log
    override fun toString(): String {
        return dump()
    }
}

// for debug log
fun IConvertResult.dump(): String {
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
