package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodecInfo
import io.github.toyota32k.media.lib.format.Codec
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
        Codec.AVC,
        Profile.AVCProfileMain,
        Level.AVCLevel13,
        arrayOf(Profile.AVCProfileBaseline),
        HD720SizeCriteria,
        MaxDefault(768*1000),
        MaxDefault(30, 24),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )

    object AVC720Profile : VideoStrategy(
        Codec.AVC,
        Profile.AVCProfileMain,
        Level.AVCLevel31,
        arrayOf(Profile.AVCProfileBaseline),
        HD720SizeCriteria,
        MaxDefault(4*1000*1000, 3*1000*1000),
        MaxDefault(30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )
    object AVC720HighProfile : VideoStrategy(
        Codec.AVC,
        Profile.AVCProfileHigh,
        Level.AVCLevel32,
        arrayOf(Profile.AVCProfileMain, Profile.AVCProfileBaseline),
        HD720SizeCriteria,
        MaxDefault(20*1000*1000, 10*1000*1000),
        MaxDefault(60, 30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )


    // AVC - H.264
    // FullHD-1080p
    object AVC1080Profile : VideoStrategy(
        Codec.AVC,
        Profile.AVCProfileHigh,
        Level.AVCLevel4,
        arrayOf(Profile.AVCProfileMain),
        FHD1080SizeCriteria,
        MaxDefault(4*1000*1000, 2*1000*1000),
        MaxDefault(30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )

    object AVC1080HighProfile : VideoStrategy(
        Codec.AVC,
        Profile.AVCProfileHigh10,
        Level.AVCLevel4,
        arrayOf(Profile.AVCProfileHigh, Profile.AVCProfileMain),
        FHD1080SizeCriteria,
        MaxDefault(20*1000*1000, 10*1000*1000),
        MaxDefault(60, 30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )

    // HEVC - H.265
    // FullHD 1080p

    object HEVC1080Profile : VideoStrategy(
        Codec.HEVC,
        Profile.HEVCProfileMain,
        Level.HEVCMainTierLevel4,
        null,
        FHD1080SizeCriteria,
        MaxDefault(10*1000*1000, 4*1000*1000),
        MaxDefault(30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )

    object HEVC1080HighProfile : VideoStrategy(
        Codec.HEVC,
        Profile.HEVCProfileMain10,
        Level.HEVCHighTierLevel5,
        arrayOf(Profile.HEVCProfileMain),
        FHD1080SizeCriteria,
        MaxDefault(30*1000*1000, 15*1000*1000),
        MaxDefault(30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )
}