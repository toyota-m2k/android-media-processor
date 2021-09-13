package io.github.toyota32k.media.lib.codec

import android.media.MediaFormat
import android.view.Surface
import io.github.toyota32k.media.lib.surface.OutputSurface
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.utils.Chronos

class VideoDecoder(format: MediaFormat):BaseDecoder(format)  {
    lateinit var outputSurface:OutputSurface
    override val sampleType = Muxer.SampleType.Video
    override fun configure() {
        outputSurface = OutputSurface()
        mediaCodec.configure(mediaFormat, outputSurface.surface, null, 0)
    }

    override fun chainTo(encoder: BaseEncoder) :Boolean {
        return chainTo(null) { index, length, end->
            if(end) {
                eos = true
                encoder.encoder.signalEndOfInputStream()
                decoder.releaseOutputBuffer(index, false)
            } else {
                decoder.releaseOutputBuffer(index, length>0)
                if(length>0 && encoder is VideoEncoder) {
                    outputSurface.awaitNewImage()
                    outputSurface.drawImage()
                    encoder.inputSurface.setPresentationTime(bufferInfo.presentationTimeUs*1000)
                    encoder.inputSurface.swapBuffers()
                }
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