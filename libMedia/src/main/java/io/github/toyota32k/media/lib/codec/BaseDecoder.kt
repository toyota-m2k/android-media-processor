package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.misc.TrimmingRange

abstract class BaseDecoder(format: MediaFormat):BaseCodec(format) {
//    val inputBuffer: ByteBuffer?
    val decoder:MediaCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    var trimmingRange = TrimmingRange.Empty
    override val name: String get() = "Decoder($sampleType)"
    override val mediaCodec:MediaCodec get() = decoder

    protected fun chainTo(
        formatChanged:((decodedFormat:MediaFormat)->Unit)?,
        dataConsumed:(index:Int, length:Int, end:Boolean, timeUs:Long)->Unit) : Boolean {
        var effected = false
        while(true) {
            val index = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
            if (index >= 0) {
//                logger.debug("output:$index size=${bufferInfo.size}")
                dataConsumed(index, bufferInfo.size, bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0, bufferInfo.presentationTimeUs)
                return true
            }
            else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                logger.debug("no sufficient data yet")
                return effected
            }
            else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                logger.debug("format changed")
                formatChanged?.invoke(decoder.outputFormat)
            }
            effected = true
        }
    }

    abstract fun chainTo(encoder:BaseEncoder):Boolean

}