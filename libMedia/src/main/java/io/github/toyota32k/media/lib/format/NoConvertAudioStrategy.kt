package io.github.toyota32k.media.lib.format

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import io.github.toyota32k.media.lib.misc.MediaConstants

object NoConvertAudioStrategy : IAudioStrategy {
    override fun createEncoder(inputFormat: MediaFormat): CodecAndFormat {
        val mime = inputFormat.getText(MediaFormat.KEY_MIME, MediaConstants.MIMETYPE_AUDIO_AAC)
        val encoder = MediaCodec.createEncoderByType(mime)
        return CodecAndFormat(encoder, inputFormat)
    }
}