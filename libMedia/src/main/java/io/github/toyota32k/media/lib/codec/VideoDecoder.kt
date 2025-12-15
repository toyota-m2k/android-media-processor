package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.processor.contract.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.internals.surface.OutputSurface
import io.github.toyota32k.media.lib.internals.surface.RenderOption
import io.github.toyota32k.media.lib.legacy.track.Muxer

class VideoDecoder(strategy: IVideoStrategy, format: MediaFormat, decoder: MediaCodec, renderOption: RenderOption, report: Report, cancellation: ICancellation)
    :BaseDecoder(strategy, format,decoder,report,cancellation)  {
    private lateinit var outputSurface:OutputSurface
    private val renderOption = renderOption
    override val sampleType = Muxer.SampleType.Video
    override val logger = UtLog("Decoder(Video)", Converter.logger)
    override fun configure() {
        outputSurface = OutputSurface(renderOption)
        mediaCodec.configure(mediaFormat, outputSurface.surface, null, 0)
    }

    override fun onDataConsumed(index: Int, length: Int, end: Boolean) {
        val render = length>0 /*&& trimmingRangeList.isValidPosition(timeUs)*/
        decoder.releaseOutputBuffer(index, render)
        val videoEncoder = chainedEncoder as? VideoEncoder
        if(videoEncoder!=null) {
            if (render) {
                if (end) {
                    logger.info("render end of data.")
                }
                outputSurface.awaitNewImage()
                outputSurface.drawImage()
                videoEncoder.inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                videoEncoder.inputSurface.swapBuffers()
            }
            if (end) {
                logger.info("signal end of input stream to encoder.")
                eos = true
                videoEncoder.encoder.signalEndOfInputStream()
            }
        }
    }

    override fun close() {
        if(!disposed) {
            outputSurface.release()
        }
        super.close()
    }

    // Debug Note
    // BaseDecoder#consume() は、Video/Audio 共用のため、ブレークポイントを仕掛けると、VideoとAudioの両方で止まってしまうので不便。
    // そんな時は、BaseDecoder から、fun consume() の実装をここにコピーして実行すれば、
    // Videoは、こちらの consume()、Audioは、BaseDecoderのconsume()が呼ばれるので、分離してデバッグできる。

//    override fun consume():Boolean {
//        var effected = false
//        if (!eos) {
//            while (true) {
//                if(isCancelled) {
//                    return false
//                }
//                val index = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
//                when {
//                    index >= 0 -> {
//                        logger.verbose { "output:$index size=${bufferInfo.size}" }
//                        effected = true
//                        val eos = bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
//                        if (eos) {
//                            logger.debug("found eos")
//                            onDecoderEos()
//                        }
//                        // サブクラス側で releaseOutputBuffer()する
//                        onDataConsumed(index, bufferInfo.size, eos)
//                        break
//                    }
//
//                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
//                        logger.verbose { "no sufficient data yet" }
//                        break
//                    }
//
//                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
//                        logger.debug("format changed")
//                        effected = true
//                        onFormatChanged(decoder.outputFormat)
//                        decoder.outputFormat.dump(logger, "OutputFormat Changed")
//                    }
//
//                    else -> {
//                        if (index == -3) {
//                            logger.debug("BUFFERS_CHANGED ... ignorable.")
//                        } else {
//                            logger.error("unknown index ($index)")
//                        }
//                    }
//                }
//            }
//        }
//        return afterComsumed() || effected
//    }
}