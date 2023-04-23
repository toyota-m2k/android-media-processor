package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.track.Muxer

class AudioEncoder(format: MediaFormat, encoder: MediaCodec,report: Report):BaseEncoder(format,encoder,report)  {
    override val sampleType = Muxer.SampleType.Audio
}
