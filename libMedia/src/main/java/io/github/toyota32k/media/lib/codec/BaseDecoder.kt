package io.github.toyota32k.media.lib.codec

import android.icu.util.Output
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import io.github.toyota32k.media.lib.surface.OutputSurface

abstract class BaseDecoder(format: MediaFormat):BaseCodec(format) {
//    val inputBuffer: ByteBuffer?
    val decoder:MediaCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    override val mediaCodec:MediaCodec get() = decoder

    protected fun chainTo(
        formatChanged:((decodedFormat:MediaFormat)->Unit)?,
        dataConsumed:(index:Int, length:Int, end:Boolean)->Unit) : Boolean {
        var effected = false
        while(true) {
            val result = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
            if (result >= 0) {
                dataConsumed(result, bufferInfo.size, bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)
                return true
            }
            else if (result == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return effected
            }
            else if (result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                formatChanged?.invoke(decoder.outputFormat)
            }
            effected = true
        }
    }

    abstract fun chainTo(encoder:BaseEncoder):Boolean

}