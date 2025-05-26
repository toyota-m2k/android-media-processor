package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.format.dump
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.surface.InputSurface
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.utils.UtLog

class VideoEncoder(strategy: IVideoStrategy, format:MediaFormat, encoder:MediaCodec, report: Report, cancellation: ICancellation)
    :BaseEncoder(strategy, format, encoder, report, cancellation) {
    override val sampleType = Muxer.SampleType.Video
    override val logger = UtLog("Encoder(Video)", Converter.logger)
    lateinit var inputSurface:InputSurface
    // Encoder側はMediaCodec.configureでsurfaceを設定しない
    // override val surface: Surface? get() = inputSurface.surface
    private val audioStrategy: IAudioStrategy get() = strategy as IAudioStrategy

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