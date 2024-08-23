package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodecInfo
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.Profile

object PresetVideoStrategies {
    val HD720SizeCriteria =
        VideoStrategy.SizeCriteria(VideoStrategy.HD720_S_SIZE, VideoStrategy.HD720_L_SIZE)
    val FHD1080SizeCriteria =
        VideoStrategy.SizeCriteria(VideoStrategy.FHD1080_S_SIZE, VideoStrategy.FHD1080_L_SIZE)

    // AVC - H.264
    // HD-720p

    object AVC720LowProfile : VideoStrategy(
        codec = Codec.AVC,
        profile = Profile.AVCProfileMain,
        level = Level.AVCLevel13,
        fallbackProfiles = arrayOf(Profile.AVCProfileBaseline),
        sizeCriteria = HD720SizeCriteria,
        bitRate = MaxDefault(768*1000),
        frameRate = MaxDefault(30, 24),
        iFrameInterval = MinDefault(1),
        colorFormat = ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    )

    object AVC720Profile : VideoStrategy(
        codec = Codec.AVC,
        profile = Profile.AVCProfileMain,
        level = Level.AVCLevel31,
        fallbackProfiles = arrayOf(Profile.AVCProfileBaseline),
        sizeCriteria = HD720SizeCriteria,
        bitRate = MaxDefault(4*1000*1000, 3*1000*1000),
        frameRate = MaxDefault(30),
        iFrameInterval = MinDefault(1),
        colorFormat = ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    )
    object AVC720HighProfile : VideoStrategy(
        codec = Codec.AVC,
        profile = Profile.AVCProfileHigh,
        level = Level.AVCLevel32,
        fallbackProfiles = arrayOf(Profile.AVCProfileMain, Profile.AVCProfileBaseline),
        sizeCriteria = HD720SizeCriteria,
        bitRate = MaxDefault(20*1000*1000, 10*1000*1000),
        frameRate = MaxDefault(60, 30),
        iFrameInterval = MinDefault(1),
        colorFormat = ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    )


    // AVC - H.264
    // FullHD-1080p
    object AVC1080Profile : VideoStrategy(
        codec = Codec.AVC,
        profile = Profile.AVCProfileHigh,
        level = Level.AVCLevel4,
        fallbackProfiles = arrayOf(Profile.AVCProfileMain),
        sizeCriteria = FHD1080SizeCriteria,
        bitRate = MaxDefault(4*1000*1000, 2*1000*1000),
        frameRate = MaxDefault(30),
        iFrameInterval = MinDefault(1),
        colorFormat = ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    )

    object AVC1080HighProfile : VideoStrategy(
        codec = Codec.AVC,
        profile = Profile.AVCProfileHigh10,
        level = Level.AVCLevel4,
        fallbackProfiles =  arrayOf(Profile.AVCProfileHigh, Profile.AVCProfileMain),
        sizeCriteria = FHD1080SizeCriteria,
        bitRate = MaxDefault(20*1000*1000, 10*1000*1000),
        frameRate = MaxDefault(60, 30),
        iFrameInterval = MinDefault(1),
        colorFormat = ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    )

    // HEVC - H.265
    // FullHD 1080p

    object HEVC1080Profile : VideoStrategy(
        codec = Codec.HEVC,
        profile = Profile.HEVCProfileMain,
        level = Level.HEVCMainTierLevel4,
        fallbackProfiles = null,
        sizeCriteria = FHD1080SizeCriteria,
        bitRate = MaxDefault(10*1000*1000, 4*1000*1000),
        frameRate = MaxDefault(30),
        iFrameInterval =MinDefault(1),
        colorFormat =ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    )

    object HEVC1080HighProfile : VideoStrategy(
        codec = Codec.HEVC,
        profile = Profile.HEVCProfileMain10,
        level = Level.HEVCHighTierLevel5,
        fallbackProfiles =arrayOf(Profile.HEVCProfileMain),
        sizeCriteria =FHD1080SizeCriteria,
        bitRate =MaxDefault(30*1000*1000, 15*1000*1000),
        frameRate =MaxDefault(30),
        iFrameInterval =MinDefault(1),
        colorFormat =ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    )
}