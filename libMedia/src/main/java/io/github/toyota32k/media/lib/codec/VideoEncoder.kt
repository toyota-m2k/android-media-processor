package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.dump
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.surface.InputSurface
import io.github.toyota32k.media.lib.track.Muxer

class VideoEncoder(format:MediaFormat, encoder:MediaCodec, report: Report, cancellation: ICancellation):BaseEncoder(format, encoder, report,cancellation) {
    override val sampleType = Muxer.SampleType.Video

    lateinit var inputSurface:InputSurface
    // Encoder側はMediaCodec.configureでsurfaceを設定しない
    // override val surface: Surface? get() = inputSurface.surface

    override fun configure() {
        mediaFormat.dump(logger, "VideoEncoder.configure")
        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = InputSurface(encoder.createInputSurface())
        inputSurface.makeCurrent()
    }

    override fun close() {
        if(!disposed) {
            inputSurface.release()
        }
        super.close()
    }
}