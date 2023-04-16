package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodecInfo

object PresetVideoStrategies {
    val HD720SizeCriteria =
        VideoStrategy.SizeCriteria(VideoStrategy.HD720_S_SIZE, VideoStrategy.HD720_L_SIZE)
    val FHD1080SizeCriteria =
        VideoStrategy.SizeCriteria(VideoStrategy.FHD1080_S_SIZE, VideoStrategy.FHD1080_L_SIZE)

    // AVC - H.264
    // HD-720p

    object AVC720LowProfile : VideoStrategy(
        "video/avc",
        MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
        MediaCodecInfo.CodecProfileLevel.AVCLevel13,
        arrayOf(MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline),
        HD720SizeCriteria,
        MaxDefault(768*1000),
        MaxDefault(30, 24),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )

    object AVC720Profile : VideoStrategy(
        "video/avc",
        MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
        MediaCodecInfo.CodecProfileLevel.AVCLevel31,
        arrayOf(MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline),
        HD720SizeCriteria,
        MaxDefault(4*1000*1000, 3*1000*1000),
        MaxDefault(30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )
    object AVC720HighProfile : VideoStrategy(
        "video/avc",
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
        MediaCodecInfo.CodecProfileLevel.AVCLevel32,
        arrayOf(MediaCodecInfo.CodecProfileLevel.AVCProfileMain,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline),
        HD720SizeCriteria,
        MaxDefault(20*1000*1000, 10*1000*1000),
        MaxDefault(60, 30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )


    // AVC - H.264
    // FullHD-1080p
    object AVC1080Profile : VideoStrategy(
        "video/avc",
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
        MediaCodecInfo.CodecProfileLevel.AVCLevel4,
        arrayOf(MediaCodecInfo.CodecProfileLevel.AVCProfileMain),
        FHD1080SizeCriteria,
        MaxDefault(4*1000*1000, 2*1000*1000),
        MaxDefault(30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )

    // HEVC - H.265
    // FullHD 1080p

    object AVC1080HighProfile : VideoStrategy(
        "video/avc",
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10,
        MediaCodecInfo.CodecProfileLevel.AVCLevel4,
        arrayOf(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh, MediaCodecInfo.CodecProfileLevel.AVCProfileMain),
        FHD1080SizeCriteria,
        MaxDefault(20*1000*1000, 10*1000*1000),
        MaxDefault(60, 30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )

    object HEVC1080Profile : VideoStrategy(
        "video/hevc",
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31,
        null,
        FHD1080SizeCriteria,
        MaxDefault(10*1000*1000, 4*1000*1000),
        MaxDefault(30),
        MinDefault(1),
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    )
}