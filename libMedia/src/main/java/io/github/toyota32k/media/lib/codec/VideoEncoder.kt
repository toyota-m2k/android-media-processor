package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import io.github.toyota32k.media.lib.surface.InputSurface
import io.github.toyota32k.media.lib.track.Muxer

class VideoEncoder(format:MediaFormat):BaseEncoder(format) {
    override val sampleType = Muxer.SampleType.Video

    lateinit var inputSurface:InputSurface
    // Encoder側はMediaCodec.configureでsurfaceを設定しない
    // override val surface: Surface? get() = inputSurface.surface

    override fun start() {
        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = InputSurface(encoder.createInputSurface())
        inputSurface.makeCurrent()
        encoder.start()
    }

    override fun close() {
        if(!disposed) {
            inputSurface.release()
        }
        super.close()
    }
}