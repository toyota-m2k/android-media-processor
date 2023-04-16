package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodecInfo

object PresetAudioStrategies {
    object AACDefault : AudioStrategy(
        mimeType = "audio/mp4a-latm",
        profile = MediaCodecInfo.CodecProfileLevel.AACObjectLC,
        fallbackProfiles = null,
        sampleRate = MaxDefault(96*1000, 48*1000),
        channelCount = MaxDefault(2,1),
        bitRatePerChannel = MaxDefault(192*1000, 64*1000)
    )
    object AACLowHEv2 : AudioStrategy(
        mimeType = "audio/mp4a-latm",
        profile = MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS,
        fallbackProfiles = null,
        sampleRate = MaxDefault(96*1000, 44100),
        channelCount = MaxDefault(2,1),
        bitRatePerChannel = MaxDefault(32*1000, 16*1000)
    )
}