package io.github.toyota32k.media.lib.format

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi

enum class BitRateMode(val value:Int) {
    CQ(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ),             // Constant Quality
    VBR(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR),           // Variable bitrate mode
    CBR(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR),           // Constant bitrate mode
    @RequiresApi(Build.VERSION_CODES.S)
    CBR_FD(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD),     // Constant bitrate mode with frame drops
    ;
    companion object {
        fun fromValue(value:Int):BitRateMode? {
            return entries.firstOrNull {
                it.value == value
            }
        }

        fun fromFormat(format:MediaFormat?):BitRateMode? {
            val mode = format?.bitRateMode?:return null
            return fromValue(mode)
        }
    }
}