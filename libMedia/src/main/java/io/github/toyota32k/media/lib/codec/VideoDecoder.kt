package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.surface.OutputSurface
import io.github.toyota32k.media.lib.track.Muxer
import kotlin.compareTo

class VideoDecoder(format: MediaFormat, decoder: MediaCodec, report: Report, cancellation: ICancellation):BaseDecoder(format,decoder,report,cancellation)  {
    private lateinit var outputSurface:OutputSurface
    override val sampleType = Muxer.SampleType.Video
    override fun configure() {
        outputSurface = OutputSurface()
        mediaCodec.configure(mediaFormat, outputSurface.surface, null, 0)
    }

    override fun onFormatChanged(format: MediaFormat) {
        // nothing to do.
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
}