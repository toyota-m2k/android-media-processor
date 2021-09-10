package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.audio.AudioChannel
import io.github.toyota32k.media.lib.track.Muxer

class AudioEncoder(format: MediaFormat):BaseEncoder(format)  {
    override val sampleType = Muxer.SampleType.Audio
}
