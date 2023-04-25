package io.github.toyota32k.media.lib.codec

import android.media.MediaFormat
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.surface.OutputSurface
import io.github.toyota32k.media.lib.track.Muxer

class VideoDecoder(format: MediaFormat,report: Report):BaseDecoder(format,report)  {
    private lateinit var outputSurface:OutputSurface
    override val sampleType = Muxer.SampleType.Video
    override fun configure() {
        outputSurface = OutputSurface()
        mediaCodec.configure(mediaFormat, outputSurface.surface, null, 0)
    }

    override fun chainTo(encoder: BaseEncoder) :Boolean {
        return chainTo(
            formatChanged = null,
            dataConsumed = { index, length, end ->
                val render = length>0 /*&& trimmingRangeList.isValidPosition(timeUs)*/
                decoder.releaseOutputBuffer(index, render)
                if(render && encoder is VideoEncoder) {
                    if(end) {
                        logger.info("render end of data.")
                    }
                    outputSurface.awaitNewImage()
                    outputSurface.drawImage()
                    encoder.inputSurface.setPresentationTime(bufferInfo.presentationTimeUs*1000)
                    encoder.inputSurface.swapBuffers()
                }
                if(end) {
                    logger.info("signal end of input stream to encoder.")
                    eos = true
                    encoder.encoder.signalEndOfInputStream()
                }
            }
        )
    }

    override fun close() {
        if(!disposed) {
            outputSurface.release()
        }
        super.close()
    }
}