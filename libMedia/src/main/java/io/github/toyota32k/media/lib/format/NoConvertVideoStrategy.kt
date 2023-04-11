package io.github.toyota32k.media.lib.format

import android.media.MediaCodecInfo
import android.media.MediaFormat

object NoConvertVideoStrategy : IVideoStrategy {
    override fun createOutputFormat(inputFormat: MediaFormat): MediaFormat {
        val width = inputFormat.getInt(MediaFormat.KEY_WIDTH,1280)
        val height = inputFormat.getInt(MediaFormat.KEY_HEIGHT,720)
        val mimeType = inputFormat.getText(MediaFormat.KEY_MIME, "video/avc")
        val format = MediaFormat.createVideoFormat(mimeType, width, height)

        val inputBitRate = inputFormat.getInt(MediaFormat.KEY_BIT_RATE, HD720VideoStrategy.MAX_BITRATE)
        val inputFrameRate = inputFormat.getInt(MediaFormat.KEY_FRAME_RATE, HD720VideoStrategy.MAX_FRAME_RATE)
        val iFrameInterval = inputFormat.getInt(MediaFormat.KEY_I_FRAME_INTERVAL, HD720VideoStrategy.DEFAULT_I_FRAME_INTERVAL)

        format.setInteger(MediaFormat.KEY_BIT_RATE, inputBitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, inputFrameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        return format
    }
}