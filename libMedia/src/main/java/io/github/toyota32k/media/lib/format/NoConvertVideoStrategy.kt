package io.github.toyota32k.media.lib.format

import android.media.MediaCodecInfo
import android.media.MediaFormat

object NoConvertVideoStrategy : IVideoStrategy {
    override fun createOutputFormat(inputFormat: MediaFormat): MediaFormat {
//        inputFormat.summary("NoConvert: input = output")
//        return inputFormat
        val width = inputFormat.getInt(MediaFormat.KEY_WIDTH,1280)
        val height = inputFormat.getInt(MediaFormat.KEY_HEIGHT,720)
        val mimeType = inputFormat.getText(MediaFormat.KEY_MIME, "video/avc")
        val format = MediaFormat.createVideoFormat(mimeType, width, height)

        val inputBitRate = inputFormat.getInt(MediaFormat.KEY_BIT_RATE, 0)
        val inputFrameRate = inputFormat.getInt(MediaFormat.KEY_FRAME_RATE, HD720VideoStrategy.MAX_FRAME_RATE)
        val iFrameInterval = inputFormat.getInt(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
        val colorFormat = inputFormat.getInt(MediaFormat.KEY_COLOR_FORMAT, 0)

        format.setInteger(MediaFormat.KEY_BIT_RATE, inputBitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, inputFrameRate)       // 必須 ... これがないとエラーになる
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval) // 必須 ... これがないとエラーになる
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
//        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        inputFormat.summary("NoConvert: input")
        format.summary("NoConvert: output")
        return format
    }
}