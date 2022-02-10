package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.converter.TrimmingRange

abstract class BaseDecoder(format: MediaFormat):BaseCodec(format) {
//    val inputBuffer: ByteBuffer?
    val decoder:MediaCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    var trimmingRange = TrimmingRange.Empty
    override val name: String get() = "Decoder($sampleType)"
    override val mediaCodec:MediaCodec get() = decoder

    protected fun chainTo(
        formatChanged:((decodedFormat:MediaFormat)->Unit)?,
        dataConsumed:(index:Int, length:Int, end:Boolean, timeUs:Long)->Unit) : Boolean {
        if(eos) return false
        var effected = false
        while(true) {
            val index = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
            when {
                index >= 0 -> {
                    // logger.debug("output:$index size=${bufferInfo.size}")
                    val eos = bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                    if(eos) {
                        this.eos = eos
                    }
                    dataConsumed(index, bufferInfo.size, eos, bufferInfo.presentationTimeUs)
                    return true
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // logger.debug("no sufficient data yet")
                    return effected
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    logger.debug("format changed")
                    formatChanged?.invoke(decoder.outputFormat)
                }
                else -> {
                    logger.error("unknown index ($index)")
                }
            }
            effected = true
        }
    }

    abstract fun chainTo(encoder:BaseEncoder):Boolean

}