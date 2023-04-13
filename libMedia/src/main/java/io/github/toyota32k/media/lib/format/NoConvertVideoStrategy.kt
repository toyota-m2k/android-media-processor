package io.github.toyota32k.media.lib.format

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.IStrategy.Companion.logger

object NoConvertVideoStrategy : IVideoStrategy {
//    fun getProfile(mimeType:String) {
//        val profiles = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info->
//            info.isEncoder && info.supportedTypes?.find { it.equals(mimeType, true) }!=null }
//        profiles.forEach {
//            logger.info("$it")
//        }
//        val caps = profiles.mapNotNull { try {it.getCapabilitiesForType(mimeType)} catch (_:Throwable) {null} }
//        caps.forEach {
//            logger.info("$it")
//        }
//    }

    override fun createEncoder(inputFormat: MediaFormat): CodecAndFormat {
        val mime = inputFormat.getText(MediaFormat.KEY_MIME, FullHDVideoStrategy.MIME_TYPE)
        val encoder = MediaCodec.createEncoderByType(mime)
        val width = inputFormat.getInt(MediaFormat.KEY_WIDTH,1920)
        val height = inputFormat.getInt(MediaFormat.KEY_HEIGHT,1080)
        val format = MediaFormat.createVideoFormat(mime, width, height)

//        val profile = inputFormat.getInt(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain)
//        val level = inputFormat.getInt(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel42)
//        if(SupportedProfile.isSupported(encoder.codecInfo.getCapabilitiesForType(mime), profile, level)) {
//            format.setInteger(MediaFormat.KEY_PROFILE, profile)
//            format.setInteger(MediaFormat.KEY_LEVEL, level)
//        } else {
//            SupportedProfile.getRegularProfileOfAVC(encoder)?.also {
//                format.setInteger(MediaFormat.KEY_PROFILE, it.profile)
//                format.setInteger(MediaFormat.KEY_LEVEL, it.level)
//            }
//        }
        SupportedProfile.getRegularProfileOfAVC(encoder)?.also {
            format.setInteger(MediaFormat.KEY_PROFILE, it.profile)
            format.setInteger(MediaFormat.KEY_LEVEL, it.level)
        }

        val inputBitRate = inputFormat.getInt(MediaFormat.KEY_BIT_RATE, FullHDVideoStrategy.MAX_BITRATE)
        val inputFrameRate = inputFormat.getInt(MediaFormat.KEY_FRAME_RATE, FullHDVideoStrategy.MAX_FRAME_RATE)
        val iFrameInterval = inputFormat.getInt(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        val colorFormat = inputFormat.getInt(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, inputBitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, inputFrameRate)       // 必須 ... これがないとエラーになる
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval) // 必須 ... これがないとエラーになる
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        return CodecAndFormat(encoder, format)
    }
}