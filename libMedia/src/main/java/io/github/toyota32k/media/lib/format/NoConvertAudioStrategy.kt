package io.github.toyota32k.media.lib.format

import android.media.MediaCodecInfo
import android.media.MediaFormat

object NoConvertAudioStrategy : IAudioStrategy {
    override fun createOutputFormat(inputFormat: MediaFormat): MediaFormat {
        return inputFormat
    }
}