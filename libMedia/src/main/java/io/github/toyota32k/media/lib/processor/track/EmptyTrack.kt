package io.github.toyota32k.media.lib.processor.track

import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.utils.RangeUs
import io.github.toyota32k.media.lib.report.Report

object EmptyTrack : ITrack {
    override val isAvailable: Boolean = false
    override val done: Boolean = true
    override val presentationTimeUs: Long = 0L
    override fun setup(muxer: SyncMuxer) {}
    override fun startRange(startFromUS: Long): Long = startFromUS
    override fun endRange() {}
    override fun finalize() {}
    override fun readAndWrite(rangeUs: RangeUs): Boolean = false
    override fun inputSummary(report: Report, metaData: MetaData) {}
    override fun outputSummary(report: Report) {}
    override fun close() {}
}