package io.github.toyota32k.media.lib.strategy

import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.Profile

object PresetAudioStrategies {
    object NoAudio : AudioStrategy(
        codec = Codec.InvalidAudio,
        profile = Profile.AACObjectLC,
        fallbackProfiles = null,
        sampleRate = MaxDefault(96*1000, 48*1000),
        channelCount = 0,
        bitRatePerChannel = MaxDefault(192*1000, 64*1000)
    )
    object AACDefault : AudioStrategy(
        codec = Codec.AAC,
        profile = Profile.AACObjectLC,
        fallbackProfiles = null,
        sampleRate = MaxDefault(96*1000, 48*1000),
        channelCount = 0,
        bitRatePerChannel = MaxDefault(192*1000, 64*1000)
    )
    object AACMono : AudioStrategy(     // not work properly ...
        codec = Codec.AAC,
        profile = Profile.AACObjectLC,
        fallbackProfiles = null,
        sampleRate = MaxDefault(96*1000, 48*1000),
        channelCount = 1,
        bitRatePerChannel = MaxDefault(192*1000, 64*1000)
    )
    @Suppress("unused")
    object AACLowHEv2 : AudioStrategy(
        codec = Codec.AAC,
        profile = Profile.AACObjectHE_PS,
        fallbackProfiles = null,
        sampleRate = MaxDefault(96*1000, 44100),
        channelCount = 0,
        bitRatePerChannel = MaxDefault(32*1000, 16*1000)
    )
}