package io.github.toyota32k.media.lib.format

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Size
import java.lang.NullPointerException
import kotlin.math.min
import kotlin.math.roundToInt

object HD720VideoStrategy : IVideoStrategy {
    const val MAX_BITRATE = 705000
    const val MAX_FRAME_RATE = 30
    const val DEFAULT_I_FRAME_INTERVAL = 1

    fun calcHD720Size(width:Int, height:Int) : Size {
        var r = if (width > height) { // 横長
            min(1280f / width, 720f / height)
        } else { // 縦長
            min(720f / width, 1280f / height)
        }
        if (r > 1) { // 拡大はしない
            r = 1f
        }
        return Size((width * r).roundToInt(), (height * r).roundToInt())
    }

    override fun createOutputFormat(inputFormat:MediaFormat):MediaFormat {
        val width = inputFormat.getInt(MediaFormat.KEY_WIDTH,1280)
        val height = inputFormat.getInt(MediaFormat.KEY_HEIGHT,720)
        val size = calcHD720Size(width, height)
        val format = MediaFormat.createVideoFormat("video/avc", size.width, size.height) // From Nexus 4 Camera in 720p

        // オリジナルのビットレートより大きいビットレートにならないように。
        val inputBitRate = inputFormat.getInt(MediaFormat.KEY_BIT_RATE, MAX_BITRATE)
        val outputBitRate = if (inputBitRate > 0) min(inputBitRate, MAX_BITRATE) else MAX_BITRATE   // avi (divx/xvid) のときに、prop.Bitrate==0になっていて、トランスコードに失敗することがあった。#5812:2

        // Frame Rate
        val inputFrameRate = inputFormat.getInt(MediaFormat.KEY_FRAME_RATE, MAX_FRAME_RATE)
        val outputFrameRate = min(inputFrameRate, MAX_FRAME_RATE)

        val iFrameInterval = inputFormat.getInt(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_I_FRAME_INTERVAL)

        // From Nexus 4 Camera in 720p
        format.setInteger(MediaFormat.KEY_BIT_RATE, outputBitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, outputFrameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

        inputFormat.summary("input")
        format.summary("output")

        return format
    }
}