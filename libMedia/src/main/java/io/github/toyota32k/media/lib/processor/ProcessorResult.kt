package io.github.toyota32k.media.lib.processor

import io.github.toyota32k.media.lib.converter.IActualSoughtMap
import io.github.toyota32k.media.lib.converter.IConvertResult
import io.github.toyota32k.media.lib.converter.IOutputMediaFile
import io.github.toyota32k.media.lib.utils.RangeMs
import io.github.toyota32k.media.lib.report.Report
import kotlin.coroutines.cancellation.CancellationException

data class ProcessorResult(override val succeeded:Boolean, override val outputFile: IOutputMediaFile?, override val requestedRangeMs: RangeMs, override val actualSoughtMap: IActualSoughtMap?, override val exception:Throwable?, override val errorMessage: String?, override val report: Report?=null):IConvertResult {
    companion object {
        val cancelled = error(CancellationException())
        fun success(r:Processor.Result) = ProcessorResult(true, r.outputFile, r.requestedRangeUs.toRangeMs(), r.actualSoughtMap, null, null, r.report)
        fun error(e:Throwable, msg:String?=null) = ProcessorResult(false, null, RangeMs.empty, null, e, msg)
    }
}
