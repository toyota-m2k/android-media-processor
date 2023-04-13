package io.github.toyota32k.media.lib.track

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer.OutputFormat
import io.github.toyota32k.media.lib.codec.AudioDecoder
import io.github.toyota32k.media.lib.codec.AudioEncoder
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.format.DefaultAudioStrategy
import io.github.toyota32k.media.lib.format.IAudioStrategy
import io.github.toyota32k.media.lib.utils.UtLog

class AudioTrack
private constructor(extractor: Extractor, inputFormat:MediaFormat, outputFormat: MediaFormat, encoder: MediaCodec, trackIdx:Int)
    : Track(extractor, inputFormat, outputFormat, trackIdx, Muxer.SampleType.Audio) {
    override val decoder: AudioDecoder = AudioDecoder(inputFormat).apply { start() }
    override val encoder: AudioEncoder = AudioEncoder(outputFormat,encoder).apply { start() }

    companion object {
        fun create(inPath: AndroidFile, strategy: IAudioStrategy): AudioTrack? {
            val extractor = Extractor(inPath)
            val trackIdx = findTrackIdx(extractor.extractor, "audio")
            if (trackIdx < 0) {
                UtLog("Track(Audio)", Converter.logger).info("no audio truck")
                return null
            }
            val inputFormat = getMediaFormat(extractor.extractor, trackIdx)
            val output = strategy.createEncoder(inputFormat)
            return AudioTrack(extractor, inputFormat, output.format, output.codec, trackIdx)
        }
    }
}
