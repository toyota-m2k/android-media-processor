package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.dump
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report

abstract class BaseDecoder(
    format: MediaFormat,
    val decoder:MediaCodec,
    report: Report,
    cancellation: ICancellation):BaseCodec(format,report,cancellation) {
//    val inputBuffer: ByteBuffer?
//    lateinit var trimmingRangeList : ITrimmingRangeList
    override val name: String get() = "Decoder($sampleType)"
    override val mediaCodec:MediaCodec get() = decoder

    protected lateinit var chainedEncoder:BaseEncoder
    fun chain(encoder:BaseEncoder): BaseEncoder {
        chainedEncoder = encoder
        return encoder
    }

    // デコーダー（入力）のEOSフラグ
    // ビデオトラックは、デコーダー入力のEOSが、そのまま、デコーダー出力（＝エンコーダー入力）のEOSになるが、
    // AudioTrack は、AudioChannelクラスでバッファリングするので、デコーダー入力のEOSを別に管理する必要がある。
    // BaseDecoder クラスは、入力のEOSで、decoderEos をセットするだけにしして、
    // BaseCodec#eos はサブクラス側でセットすることとする。
//    protected var decoderEos:Boolean = false

    protected abstract fun onFormatChanged(format:MediaFormat)
    protected abstract fun onDataConsumed(index:Int, length:Int, end:Boolean)
    protected open fun onDecoderEos() {
        logger.debug("onDecoderEos")
        eos = true
    }

    override fun consume():Boolean {
        var effected = false
        if (!eos) {
            while (true) {
                if(isCancelled) {
                    return false
                }
                val index = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
                when {
                    index >= 0 -> {
                        logger.verbose { "output:$index size=${bufferInfo.size}" }
                        effected = true
                        val eos = bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (eos) {
                            logger.debug("found eos")
                            onDecoderEos()
                        }
                        // サブクラス側で releaseOutputBuffer()する
                        onDataConsumed(index, bufferInfo.size, eos)
                        break
                    }

                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        logger.verbose { "no sufficient data yet" }
                        break
                    }

                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        logger.debug("format changed")
                        effected = true
                        onFormatChanged(decoder.outputFormat)
                        decoder.outputFormat.dump(logger, "OutputFormat Changed")
                    }

                    else -> {
                        if (index == -3) {
                            logger.debug("BUFFERS_CHANGED ... ignorable.")
                        } else {
                            logger.error("unknown index ($index)")
                        }
                    }
                }
            }
        }
        return chainedEncoder.consume() || effected
    }

}