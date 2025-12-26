package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.legacy.converter.ITrimmingRangeList
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.types.RangeMs
import io.github.toyota32k.media.lib.types.RangeUs

interface IResultBase {
    val succeeded:Boolean
    val exception:Throwable?
    val errorMessage: String?
    val cancelled : Boolean get() = exception is kotlin.coroutines.cancellation.CancellationException || (!succeeded && exception==null)
    val hasError: Boolean get() = exception != null && !cancelled
}

interface IConvertResult : IResultBase {
    val inputFile: IInputMediaFile?
    val outputFile: IOutputMediaFile?
    val actualSoughtMap: IActualSoughtMap?
//    val requestedRangeMs: RangeMs
//    val requestedRangeUs: RangeUs
    val report:Report?
}

