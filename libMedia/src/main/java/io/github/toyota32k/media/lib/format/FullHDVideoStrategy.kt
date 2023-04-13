package io.github.toyota32k.media.lib.format

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Size
import kotlin.math.min
import kotlin.math.roundToInt

object FullHDVideoStrategy : IStrategy {
    const val MAX_BITRATE = 2*1000*1000     // 2Mbps
    const val MAX_FRAME_RATE = 30
    const val DEFAULT_I_FRAME_INTERVAL = 1
    const val MIME_TYPE = "video/avc"
    const val SIZE_L = 1920f
    const val SIZE_S = 1080f

    fun calcFullHDSize(width:Int, height:Int) : Size {
        var r = if (width > height) { // 横長
            min(SIZE_L / width, SIZE_S / height)
        } else { // 縦長
            min(SIZE_S / width, SIZE_L / height)
        }
        if (r > 1) { // 拡大はしない
            r = 1f
        }
        return Size((width * r).roundToInt(), (height * r).roundToInt())
    }

    override fun createEncoder(inputFormat: MediaFormat): CodecAndFormat {
        val encoder = MediaCodec.createEncoderByType(HD720VideoStrategy.MIME_TYPE);
        val format = createOutputFormat(inputFormat, encoder)
        return CodecAndFormat(encoder, format)
    }

    private fun createOutputFormat(inputFormat: MediaFormat, encoder: MediaCodec): MediaFormat {
        val width = inputFormat.getInt(MediaFormat.KEY_WIDTH,SIZE_L.toInt())
        val height = inputFormat.getInt(MediaFormat.KEY_HEIGHT,SIZE_S.toInt())
        val size = calcFullHDSize(width, height)
        val format = MediaFormat.createVideoFormat(MIME_TYPE /*video/avc*/, size.width, size.height) // From Nexus 4 Camera in 720p
        val profile = SupportedProfile.getRegularProfileOfAVC(encoder)
        if(profile!=null) {
            format.setInteger(MediaFormat.KEY_PROFILE, profile.profile)
            format.setInteger(MediaFormat.KEY_LEVEL, profile.level)
        }

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