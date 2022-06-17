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

    // デコーダー（入力）のEOSフラグ
    // ビデオトラックは、デコーダー入力のEOSが、そのまま、デコーダー出力（＝エンコーダー入力）のEOSになるが、
    // AudioTrack は、AudioChannelクラスでバッファリングするので、デコーダー入力のEOSを別に管理する必要がある。
    // BaseDecoder クラスは、入力のEOSで、decoderEos をセットするだけにしして、
    // BaseCodec#eos はサブクラス側でセットすることとする。
    protected var decoderEos:Boolean = false

    protected fun chainTo(
        formatChanged:((decodedFormat:MediaFormat)->Unit)?,
        dataConsumed:(index:Int, length:Int, end:Boolean, timeUs:Long)->Unit) : Boolean {
        if(decoderEos) return false
        var effected = false
        while(true) {
            val index = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
            when {
                index >= 0 -> {
                    logger.verbose {"output:$index size=${bufferInfo.size}" }
                    val eos = bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                    if(eos) {
                        logger.debug("found eos")
                        this.decoderEos = true
                    }
                    dataConsumed(index, bufferInfo.size, eos, bufferInfo.presentationTimeUs)
                    return true
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    logger.verbose { "no sufficient data yet" }
                    return effected
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    logger.debug("format changed")
                    formatChanged?.invoke(decoder.outputFormat)
                }
                else -> {
                    if(index == -3) {
                        logger.debug("BUFFERS_CHANGED ... ignorable.")
                    } else {
                        logger.error("unknown index ($index)")
                    }
                }
            }
            effected = true
        }
    }

    abstract fun chainTo(encoder:BaseEncoder):Boolean

}