package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.dump
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.track.Muxer

abstract class BaseEncoder(format: MediaFormat, val encoder:MediaCodec, report: Report):BaseCodec(format,report) {
//    val encoder:MediaCodec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    override val name: String get() = "Encoder($sampleType)"
    override val mediaCodec get() = encoder
    var writtenPresentationTimeUs:Long = 0L
        private set

    override fun configure() {
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    fun chainTo(muxer:Muxer) : Boolean {
        var effected = false
        while (true) {
            val result: Int = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
            when {
                result == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    logger.verbose { "no sufficient data yet." }
                    return effected
                }
                result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    logger.debug("input format changed.")
                    effected = true
                    val actualFormat = encoder.outputFormat
                    muxer.setOutputFormat(sampleType, actualFormat)
                    logger.info("Actual Format: $actualFormat")
                    actualFormat.dump(logger, "OutputFormat Changed")
                }
                result >= 0 -> {
                    effected = true
                    if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        logger.debug("found end of stream.")
                        eos = true
                        bufferInfo.set(0, 0, 0, bufferInfo.flags)
                    }
                    else if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) { // SPS or PPS, which should be passed by MediaFormat.
                        logger.debug("codec config.")
                        encoder.releaseOutputBuffer(result, false)
                        continue
                    }
                    logger.verbose {"output:$result size=${bufferInfo.size}"}
                    muxer.writeSampleData(sampleType, encoder.getOutputBuffer(result)!!, bufferInfo)
                    if(bufferInfo.presentationTimeUs>0) {
                        writtenPresentationTimeUs = bufferInfo.presentationTimeUs
                    }
                    encoder.releaseOutputBuffer(result, false)
                    return true
                }
                else -> {}
            }
        }
    }

    fun forceEos(muxer:Muxer):Boolean {
        return if(!eos) {
            logger.debug("forced to set eos.")
            eos = true
            muxer.complete(sampleType)
            true
        } else false
    }
}