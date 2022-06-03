package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.track.Muxer

abstract class BaseEncoder(format: MediaFormat):BaseCodec(format) {
    val encoder:MediaCodec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
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
                    logger.verbose("no sufficient data yet.")
                    return effected
                }
                result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    logger.debug("input format changed.")
                    val actualFormat = encoder.outputFormat
                    muxer.setOutputFormat(sampleType, actualFormat)
                }
                result >= 0 -> {
                    if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        logger.debug("found end of stream.")
                        eos = true
                        bufferInfo.set(0, 0, 0, bufferInfo.flags)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) { // SPS or PPS, which should be passed by MediaFormat.
                        logger.debug("codec config.")
                        encoder.releaseOutputBuffer(result, false)
                        continue
                    }
                    logger.verbose("output:$result size=${bufferInfo.size}")
                    muxer.writeSampleData(sampleType, encoder.getOutputBuffer(result)!!, bufferInfo)
                    if(bufferInfo.presentationTimeUs>0) {
                        writtenPresentationTimeUs = bufferInfo.presentationTimeUs
                    }
                    encoder.releaseOutputBuffer(result, false)
                    if(eos) {
                        break
                    }
                }
                else -> {}
            }
            effected = true
        }
        return effected
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