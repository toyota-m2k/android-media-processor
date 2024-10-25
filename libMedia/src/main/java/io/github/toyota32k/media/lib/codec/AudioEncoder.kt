package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.track.Muxer

class AudioEncoder(format: MediaFormat, encoder: MediaCodec,report: Report, cancellation: ICancellation):BaseEncoder(format,encoder,report,cancellation)  {
    override val sampleType = Muxer.SampleType.Audio
}
