package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.track.Muxer

class AudioEncoder(format: MediaFormat, encoder: MediaCodec):BaseEncoder(format,encoder)  {
    override val sampleType = Muxer.SampleType.Audio
}
