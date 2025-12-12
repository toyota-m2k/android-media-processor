package io.github.toyota32k.media.lib.processor.track

import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.utils.RangeUs.Companion.formatAsUs
import io.github.toyota32k.media.lib.report.Report

abstract class AbstractBaseTrack(inPath:IInputMediaFile, val inputMetaData: MetaData, val maxDurationUs:Long, val bufferSource: IBufferSource, val video:Boolean) : ITrack {
    private val rawExtractor = inPath.openExtractor()
    override var presentationTimeUs:Long = 0L
    protected val buffer get() = bufferSource.buffer
    protected val bufferInfo get() = bufferSource.bufferInfo
    val extractor = rawExtractor.obj
    val trackIndex:Int = findTrackIdx(video)
    val inputTrackMediaFormat: MediaFormat = extractor.getTrackFormat(trackIndex)
    open val outputTrackMediaFormat: MediaFormat = inputTrackMediaFormat

    override val isAvailable: Boolean get() = trackIndex>=0
    lateinit var muxer: SyncMuxer
    val logger = UtLog("Track(${if(video) "video" else "audio"})", Processor.logger, this::class.java)

    init {
        logger.info("available = $isAvailable")
        if (isAvailable) {
            extractor.selectTrack(trackIndex)
        }
    }

    protected fun findTrackIdx(video: Boolean): Int {
        val type = if (video) "video/" else "audio/"
        for (idx in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(idx)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(type) == true) {
                return idx
            }
        }
        return -1
    }

    override fun setup(muxer: SyncMuxer) {
        if (isAvailable) {
            logger.debug()
            this.muxer = muxer
        }
    }

    override fun startRange(startFromUS:Long):Long {
        return if (isAvailable) {
            logger.info("Seek To ${startFromUS.formatAsUs()}")
            extractor.seekTo(startFromUS, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            extractor.sampleTime
        } else -1
    }

    override fun endRange() {
    }

    override fun finalize() {
    }

    override fun inputSummary(report: Report, metaData: MetaData) {
        report.updateInputSummary(extractor.getTrackFormat(trackIndex), metaData)
    }

    override fun outputSummary(report: Report) {
    }

    override fun close() {
        logger.debug()
        extractor.release()
    }
}