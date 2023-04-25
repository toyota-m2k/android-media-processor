package io.github.toyota32k.media.lib.format

import android.media.MediaCodecInfo
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
enum class BitRateMode(val value:Int) {
    CQ(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ),             // Constant Quality
    VBR(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR),           // Variable bitrate mode
    CBR(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR),           // Constant bitrate mode
    CBR_FD(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD),     // Constant bitrate mode with frame drops
    ;
    companion object {
        fun fromValue(value:Int):BitRateMode? {
            return BitRateMode.values().firstOrNull {
                it.value == value
            }
        }
    }
}