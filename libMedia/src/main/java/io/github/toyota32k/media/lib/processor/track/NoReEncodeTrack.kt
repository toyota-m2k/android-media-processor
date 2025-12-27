package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaExtractor
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.processor.contract.IBufferSource
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.types.RangeUs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.formatAsUs

class NoReEncodeTrack(inPath:IInputMediaFile, inputMetaData:MetaData, maxDurationUs:Long, bufferSource: IBufferSource, report: Report, video:Boolean)
    : AbstractBaseTrack(inPath, inputMetaData, maxDurationUs, bufferSource, report,video) {
    override var done:Boolean = !isAvailable
        private set

    override fun setup(muxer: SyncMuxer) {
        super.setup(muxer)
        muxer.setOutputFormat(video, inputTrackMediaFormat)
    }


    override fun startRange(startFromUS: Long): Long {
        done = false
        return super.startRange(startFromUS)
    }

    override fun readAndWrite(rangeUs: RangeUs) : Boolean {
        if (!isAvailable) return false   // トラックが存在しない場合は常にEOSとして扱う
        var effected = false
        val startTime = extractor.sampleTime
        val endUs = rangeUs.actualEndUs(inputMetaData.durationUs)
        val maxDuration = if (maxDurationUs<=0 ) Long.MAX_VALUE else maxDurationUs
        if (startTime == -1L || endUs <= startTime || presentationTimeUs >= maxDuration) {
            done = true
        } else {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) {
                done = true
            } else {
                effected = true
                bufferInfo.presentationTimeUs = presentationTimeUs //sampleTimeUs - rangeUs.start
                bufferInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(video, buffer, bufferInfo)
                presentationTimeUs = currentRangeStartPresentationTimeUs + extractor.sampleTime - currentRangeStartTimeUs
                durationEstimator.update(presentationTimeUs, bufferInfo.size.toLong())
//                logger.debug("presentationTime=${presentationTimeUs.formatAsUs()} / estimated=${durationEstimator.estimatedDurationUs.formatAsUs()}")

//                presentationTimeUs = durationEstimator.estimatedDurationUs
                extractor.advance()
            }
        }
        return effected
    }
}