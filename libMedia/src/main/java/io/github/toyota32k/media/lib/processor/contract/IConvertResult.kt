package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.report.Report

interface IResultBase {
    val succeeded: Boolean
    val exception: Throwable?
    val errorMessage: String?
    val cancelled: Boolean get() = exception is kotlin.coroutines.cancellation.CancellationException || (!succeeded && exception == null)
    val hasError: Boolean get() = exception != null && !cancelled
}

interface IProcessorResult : IResultBase {
    val report: Report?
    val outputFile: IOutputMediaFile?
    fun derive(output: IOutputMediaFile?): IProcessorResult
}

interface IConvertResult : IProcessorResult {
    val inputFile: IInputMediaFile?
    val soughtMap: ISoughtMap?
}

interface IConcatResult : IProcessorResult {
    interface ISubResult {
        val inputFile: IInputMediaFile
        val soughtMap: ISoughtMap?
    }
    val subResults: List<ISubResult>
}

// for debug log
fun IProcessorResult.dump(): String {
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
