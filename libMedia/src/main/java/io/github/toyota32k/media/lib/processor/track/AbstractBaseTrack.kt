package io.github.toyota32k.media.lib.processor.track

import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.processor.contract.IBufferSource
import io.github.toyota32k.media.lib.processor.contract.ITrack
import io.github.toyota32k.media.lib.types.RangeUs.Companion.formatAsUs
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.utils.DurationEstimator

/**
 * ITrack の共通・基底実装
 */
abstract class AbstractBaseTrack(val inPath:IInputMediaFile, val inputMetaData: MetaData, val maxDurationUs:Long, val bufferSource: IBufferSource, val report: Report, val video:Boolean) : ITrack {
    private val rawExtractor = inPath.openExtractor()
    override var presentationTimeUs:Long = 0L
    protected var currentRangeStartTimeUs: Long = 0L                        // startRange()でシークした入力動画上の再生位置

    val durationEstimator = DurationEstimator()
    override var currentRangeStartPresentationTimeUs: Long = 0L

    protected val buffer get() = bufferSource.buffer
    protected val bufferInfo get() = bufferSource.bufferInfo
    val extractor = rawExtractor.obj
    val trackIndex:Int = findTrackIdx(video)
    val inputTrackMediaFormat: MediaFormat = extractor.getTrackFormat(trackIndex)
    open val outputTrackMediaFormat: MediaFormat = inputTrackMediaFormat

    override val isAvailable: Boolean get() = trackIndex>=0
    lateinit var muxer: SyncMuxer
    val logger = UtLog("T(${if(video) "V" else "A"})", Processor.logger, this::class.java)

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
            currentRangeStartPresentationTimeUs = presentationTimeUs
//            currentRangeStartPresentationTimeUs = durationEstimator.estimatedDurationUs
//            logger.debug("startTimeUs = ${currentRangeStartPresentationTimeUs.formatAsUs()} / estimated=${durationEstimator.estimatedDurationUs.formatAsUs()} (diff=${(currentRangeStartPresentationTimeUs - durationEstimator.estimatedDurationUs).formatAsUs()}")
            extractor.seekTo(startFromUS, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            extractor.sampleTime.apply {
                currentRangeStartTimeUs = this
            }
        } else -1
    }

    override fun endRange() {
    }

    override fun finalize() {
        report.updateInputSummary(inputTrackMediaFormat, inputMetaData)
        report.updateOutputSummary(outputTrackMediaFormat)
        report.updateInputFileInfo(inPath.getLength(), inputMetaData.duration?:-1)
    }

    override fun close() {
        logger.debug()
        extractor.release()
    }
}