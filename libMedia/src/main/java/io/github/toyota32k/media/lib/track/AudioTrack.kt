package io.github.toyota32k.media.lib.track

import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.AudioDecoder
import io.github.toyota32k.media.lib.codec.AudioEncoder
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.format.DefaultAudioStrategy
import io.github.toyota32k.media.lib.format.IAudioStrategy
import io.github.toyota32k.media.lib.misc.AndroidFile
import io.github.toyota32k.media.lib.utils.UtLog

class AudioTrack
private constructor(extractor: Extractor, inputFormat:MediaFormat, strategy: IAudioStrategy, trackIdx:Int)
    : Track(extractor, inputFormat, DefaultAudioStrategy.createAudioFormat(inputFormat, strategy), trackIdx, Muxer.SampleType.Audio) {
    override val decoder: AudioDecoder = AudioDecoder(inputFormat).apply { start() }
    override val encoder: AudioEncoder = AudioEncoder(outputFormat).apply { start() }

    companion object {
        fun create(inPath: AndroidFile, strategy: IAudioStrategy): AudioTrack? {
            val extractor = Extractor(inPath)
            val trackIdx = findTrackIdx(extractor.extractor, "audio")
            if (trackIdx < 0) {
                UtLog("Track(Audio)", null, "io.github.toyota32k.").info("no audio truck")
                return null
            }
            val inputFormat = getMediaFormat(extractor.extractor, trackIdx)
            return AudioTrack(extractor, inputFormat, strategy, trackIdx)
        }
    }
}
