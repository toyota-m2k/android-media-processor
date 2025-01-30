package io.github.toyota32k.media.lib.track

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.AudioDecoder
import io.github.toyota32k.media.lib.codec.AudioEncoder
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.getMime
import io.github.toyota32k.media.lib.format.getSampleRate
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.AudioStrategy
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.utils.UtLog

class AudioTrack
private constructor(extractor: Extractor, inputFormat:MediaFormat, decoder: MediaCodec, outputFormat: MediaFormat, encoder: MediaCodec, report:Report, cancellation: ICancellation)
    : Track(extractor, /*inputFormat, outputFormat,*/ Muxer.SampleType.Audio, cancellation) {
    override val decoder: AudioDecoder = AudioDecoder(inputFormat, decoder, report, cancellation).apply { start() }
    override val encoder: AudioEncoder = AudioEncoder(outputFormat,encoder, report, cancellation)   // encoder はまだ start() しない。デコーダーがINFO_OUTPUT_FORMAT_CHANGEDを受け取ってから start する。

    companion object {
        fun create(inPath: IInputMediaFile, strategy: IAudioStrategy, report: Report, cancellation: ICancellation): AudioTrack? {
            if(strategy is AudioStrategy && strategy.codec== Codec.InvalidAudio) return null
            val extractor = Extractor.create(inPath, Muxer.SampleType.Audio, cancellation)
            if(extractor == null) {
                UtLog("Track(Audio)", Converter.logger).info("no audio truck")
                return null
            }
            val inputFormat = extractor.getMediaFormat()
            val decoder = MediaCodec.createDecoderByType(inputFormat.getMime()!!)
            val encoder = strategy.createEncoder()
            val outputFormat = strategy.createOutputFormat(inputFormat, encoder)

            report.updateAudioDecoder(decoder)
            report.updateAudioEncoder(encoder)
            report.updateInputSummary(inputFormat, null)
            report.updateOutputSummary(outputFormat)

            return AudioTrack(extractor, inputFormat, decoder, outputFormat, encoder, report, cancellation)
        }
    }
}
