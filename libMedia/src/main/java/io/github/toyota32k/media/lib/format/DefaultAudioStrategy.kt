package io.github.toyota32k.media.lib.format

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import io.github.toyota32k.media.lib.misc.MediaConstants

object DefaultAudioStrategy : IAudioStrategy {
    const val DEFAULT_SAMPLE_RATE:Int = 48 // 48KHz
    const val DEFAULT_BITRATE = 96000

//    val defaultFormat:MediaFormat
//        get() = MediaFormat.createAudioFormat(MediaConstants.MIMETYPE_AUDIO_AAC, DEFAULT_SAMPLE_RATE, 1).apply {
//            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
//            setInteger(MediaFormat.KEY_BIT_RATE, DEFAULT_BITRATE)
//        }

    override fun createEncoder(inputFormat: MediaFormat): CodecAndFormat {
        val encoder = MediaCodec.createEncoderByType(MediaConstants.MIMETYPE_AUDIO_AAC)
        val format = createOutputFormat(inputFormat)
        return CodecAndFormat(encoder, format)
    }

    private fun createOutputFormat(inputFormat: MediaFormat): MediaFormat {
        // Use original sample rate, as resampling is not supported yet.
        val sampleRate = inputFormat.getInt(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
        val channels = inputFormat.getInt(MediaFormat.KEY_CHANNEL_COUNT, 1)
        val bitRate = inputFormat.getInt(MediaFormat.KEY_BIT_RATE, DEFAULT_BITRATE)
        val format = MediaFormat.createAudioFormat(MediaConstants.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        return format
    }

//    fun createAudioFormat(inputFormat: MediaFormat?, strategy: IAudioStrategy):MediaFormat {
//        return if(inputFormat!=null) strategy.createOutputFormat(inputFormat) else defaultFormat
//    }
}