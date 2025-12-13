package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaExtractor
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.utils.RangeUs

class NoReEncodeTrack(inPath:IInputMediaFile, inputMetaData:MetaData, maxDurationUs:Long, bufferSource: IBufferSource, video:Boolean)
    : AbstractBaseTrack(inPath, inputMetaData, maxDurationUs, bufferSource, video) {
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
        val startTime = extractor.sampleTime
        var consumed = 0L
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
                bufferInfo.presentationTimeUs = presentationTimeUs //sampleTimeUs - rangeUs.start
                bufferInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(video, buffer, bufferInfo)
                extractor.advance()
                consumed = extractor.sampleTime - startTime
                presentationTimeUs += consumed
            }
        }
        return consumed != 0L
    }
}