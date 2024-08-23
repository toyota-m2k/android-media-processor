package io.github.toyota32k.media.lib.track

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.AudioDecoder
import io.github.toyota32k.media.lib.codec.AudioEncoder
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.utils.UtLog

class AudioTrack
private constructor(extractor: Extractor, inputFormat:MediaFormat, outputFormat: MediaFormat, encoder: MediaCodec, trackIdx:Int, report:Report)
    : Track(extractor, /*inputFormat, outputFormat,*/ trackIdx, Muxer.SampleType.Audio /*, report*/) {
    override val decoder: AudioDecoder = AudioDecoder(inputFormat, report).apply { start() }
    override val encoder: AudioEncoder = AudioEncoder(outputFormat,encoder,report).apply { start() }

    companion object {
        fun create(inPath: AndroidFile, strategy: IAudioStrategy, report: Report): AudioTrack? {
            val extractor = Extractor(inPath)
            val trackIdx = findTrackIdx(extractor.extractor, "audio")
            if (trackIdx < 0) {
                UtLog("Track(Audio)", Converter.logger).info("no audio truck")
                return null
            }
            val inputFormat = getMediaFormat(extractor.extractor, trackIdx)
            val encoder = strategy.createEncoder()
            val outputFormat = strategy.createOutputFormat(inputFormat, encoder)

            report.updateAudioEncoder(encoder)
            report.updateInputSummary(inputFormat, null)
            report.updateOutputSummary(outputFormat)

            return AudioTrack(extractor, inputFormat, outputFormat, encoder, trackIdx,report)
        }
    }
}
