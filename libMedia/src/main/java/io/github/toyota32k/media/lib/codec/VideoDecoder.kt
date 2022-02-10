package io.github.toyota32k.media.lib.codec

import android.media.MediaFormat
import io.github.toyota32k.media.lib.surface.OutputSurface
import io.github.toyota32k.media.lib.track.Muxer

class VideoDecoder(format: MediaFormat):BaseDecoder(format)  {
    private lateinit var outputSurface:OutputSurface
    override val sampleType = Muxer.SampleType.Video
    override fun configure() {
        outputSurface = OutputSurface()
        mediaCodec.configure(mediaFormat, outputSurface.surface, null, 0)
    }

    override fun chainTo(encoder: BaseEncoder) :Boolean {
        return chainTo(null) { index, length, end, timeUs ->
            val render = length>0 && trimmingRange.contains(timeUs)
            decoder.releaseOutputBuffer(index, render)
            if(render && encoder is VideoEncoder) {
                outputSurface.awaitNewImage()
                outputSurface.drawImage()
                encoder.inputSurface.setPresentationTime(bufferInfo.presentationTimeUs*1000)
                encoder.inputSurface.swapBuffers()
            }
            if(end) {
                encoder.encoder.signalEndOfInputStream()
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