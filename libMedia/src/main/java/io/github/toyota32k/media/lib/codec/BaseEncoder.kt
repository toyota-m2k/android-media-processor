package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.dump
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.track.Muxer

abstract class BaseEncoder(
    format: MediaFormat,
    val encoder:MediaCodec,
    report: Report,
    cancellation: ICancellation):BaseCodec(format,report,cancellation) {
//    val encoder:MediaCodec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    override val name: String get() = "Encoder($sampleType)"
    override val mediaCodec get() = encoder
    var writtenPresentationTimeUs:Long = 0L
        private set

    protected lateinit var chainedMuxer: Muxer
    fun chain(muxer:Muxer):Muxer {
        chainedMuxer = muxer
        return muxer
    }

    override fun configure() {
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    override fun consume() : Boolean {
        var effected = false
        while (true) {
            if(isCancelled) {
                return false
            }

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
                    chainedMuxer.setOutputFormat(sampleType, actualFormat)
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
                    chainedMuxer.writeSampleData(sampleType, encoder.getOutputBuffer(result)!!, bufferInfo)
                    if(bufferInfo.presentationTimeUs>0) {
                        writtenPresentationTimeUs = bufferInfo.presentationTimeUs
                    }
                    encoder.releaseOutputBuffer(result, false)

                    // TextureRender#drawImage (GLES20.glClear) でハングする不具合を回避する
                    // デコーダーがInputSurfaceに描画したあと、エンコーダーが読み出す前（OutputSurfaceに転送される前）に、
                    // 次のdrawImage()が呼ばれると、読み出し（or転送）処理と描画処理がコンフリクトして、デッドロックするのではないか、と推測。
                    // そこで、Encoderでは、データが取り出せる限り取り出して muxerに書き込む、という処理に変更してみた。
                    // 変更後何度も試してみたが、これでハングしなくなったように見えている。
                    if(eos) {
                        return true
                    }
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