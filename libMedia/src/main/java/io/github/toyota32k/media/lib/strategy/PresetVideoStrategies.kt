package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.BitRateMode
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.surface.RenderOption

object PresetVideoStrategies {
    val HD720SizeCriteria =
        VideoStrategy.SizeCriteria(VideoStrategy.HD720_S_SIZE, VideoStrategy.HD720_L_SIZE)
    val FHD1080SizeCriteria =
        VideoStrategy.SizeCriteria(VideoStrategy.FHD1080_S_SIZE, VideoStrategy.FHD1080_L_SIZE)
    val UHD2160SizeCriteria =
        VideoStrategy.SizeCriteria(VideoStrategy.UHD2160_S_SIZE, VideoStrategy.UHD2160_L_SIZE)


    fun noLevelProfiles(vararg elements: Profile): Array<ProfileLv> {
        return elements.map { ProfileLv(it) }.toTypedArray()
    }

    // AVC - H.264
    // HD-720p

    object AVC720LowProfile : VideoStrategy(
        codec = Codec.AVC,
        profile = Profile.AVCProfileMain,
        level = Level.AVCLevel41,
        fallbackProfiles = arrayOf(ProfileLv(Profile.AVCProfileBaseline,Level.AVCLevel41)),
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
        level = Level.AVCLevel41,
        fallbackProfiles = arrayOf(ProfileLv(Profile.AVCProfileBaseline,Level.AVCLevel41)),
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
        level = null, // Level.AVCLevel4,
        fallbackProfiles =  noLevelProfiles(Profile.AVCProfileHigh, Profile.AVCProfileMain, Profile.AVCProfileBaseline),
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
        level = Level.AVCLevel41,
        fallbackProfiles = arrayOf(ProfileLv(Profile.AVCProfileMain,Level.AVCLevel41), ProfileLv(Profile.AVCProfileBaseline,Level.AVCLevel41)),
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
        level = null,
        fallbackProfiles =  noLevelProfiles(Profile.AVCProfileHigh, Profile.AVCProfileMain),
        sizeCriteria = FHD1080SizeCriteria,
        bitRate = MaxDefault(20*1000*1000, 10*1000*1000),
        frameRate = MaxDefault(60, 30),
        iFrameInterval = MinDefault(1),
        colorFormat = ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    )

    private fun IVideoStrategy.toHdrStrategy(profile: Profile?=null, level:Level?=null): IVideoStrategy {
        return when (codec) {
            Codec.HEVC -> derived(
                profile = profile ?: Profile.HEVCProfileMain10,
                level = level,
                fallbackProfiles = noLevelProfiles(Profile.HEVCProfileMain10, Profile.HEVCProfileMain10HDR10, Profile.HEVCProfileMain10HDR10Plus, Profile.HEVCProfileMain)
            )

            Codec.VP9 -> derived(
                profile = profile ?: Profile.VP9Profile2HDR,
                level = level,
                fallbackProfiles = noLevelProfiles(Profile.VP9Profile2HDR, Profile.VP9Profile1)
            )

            Codec.AV1 -> derived(
                profile = profile ?: Profile.AV1ProfileMain10HDR10,
                level = level,
                fallbackProfiles = noLevelProfiles(Profile.AV1ProfileMain10HDR10, Profile.AV1ProfileMain10, Profile.AV1ProfileMain8)
            )

            else -> throw IllegalArgumentException("HDR is not supported for codec: $codec")
        }
    }

    //------------------------------------------------------------------------------
    // HEVC - H.265
    // FullHD 1080p
    object HEVC1080LowProfile : VideoStrategy(
        codec = Codec.HEVC,
        profile = Profile.HEVCProfileMain,
        level = Level.HEVCMainTierLevel4,
        fallbackProfiles = null,
        sizeCriteria = FHD1080SizeCriteria,
        bitRate = MaxDefault(4*1000*1000, 2*1000*1000),
        frameRate = MaxDefault(30),
        iFrameInterval =MinDefault(1),
        colorFormat =ColorFormat.COLOR_FormatSurface,
        bitRateMode = BitRateMode.VBR,
    ), IHDRSupport {
        override fun hdr(profile: Profile?, level:Level?): IVideoStrategy
            = toHdrStrategy(profile, level)
    }

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
    ), IHDRSupport {
        override fun hdr(profile: Profile?, level:Level?): IVideoStrategy
                = toHdrStrategy(profile, level)
    }

    object HEVC1080HighProfile : VideoStrategy(
        codec = Codec.HEVC,
        profile = Profile.HEVCProfileMain,
        level = Level.HEVCMainTierLevel4,
        fallbackProfiles = null,
        sizeCriteria =FHD1080SizeCriteria,
        bitRate =MaxDefault(30*1000*1000, 15*1000*1000),
        frameRate =MaxDefault(30),
        iFrameInterval =MinDefault(1),
        colorFormat =ColorFormat.COLOR_FormatSurface,
        bitRateMode = null,
    ), IHDRSupport {
        override fun hdr(profile: Profile?, level:Level?): IVideoStrategy
                = toHdrStrategy(profile, level)
    }

    // HEVC - H.265
    // HD 720p

    object HEVC720Profile : VideoStrategy(
        codec = Codec.HEVC,
        profile = Profile.HEVCProfileMain,
        level = Level.HEVCMainTierLevel4,
        fallbackProfiles = null,
        sizeCriteria = HD720SizeCriteria,
        bitRate = MaxDefault(2500*1000, 1500*1000),
        frameRate = MaxDefault(30),
        iFrameInterval = MinDefault(1),
        colorFormat = ColorFormat.COLOR_FormatSurface,
        bitRateMode = BitRateMode.VBR,
    ), IHDRSupport {
        override fun hdr(profile: Profile?, level:Level?): IVideoStrategy
                = toHdrStrategy(profile, level)
    }

    object HEVC720LowProfile : VideoStrategy(
        codec = Codec.HEVC,
        profile = Profile.HEVCProfileMain,
        level = Level.HEVCMainTierLevel4,
        fallbackProfiles = null,
        sizeCriteria = HD720SizeCriteria,
        bitRate = MaxDefault(1500*1000, 1000*1000),
        frameRate = MaxDefault(30),
        iFrameInterval = MinDefault(1),
        colorFormat = ColorFormat.COLOR_FormatSurface,
        bitRateMode = BitRateMode.VBR,
    ), IHDRSupport {
        override fun hdr(profile: Profile?, level:Level?): IVideoStrategy
                = toHdrStrategy(profile, level)
    }

    // HEVC - H.265
    // UHD 4K

    object HEVC4KProfile : VideoStrategy(
        codec = Codec.HEVC,
        profile = Profile.HEVCProfileMain,
        level = Level.HEVCMainTierLevel4,
        fallbackProfiles = null,
        sizeCriteria = UHD2160SizeCriteria,
        bitRate = MaxDefault(85*1000*1000, 50*1000*1000),
        frameRate = MaxDefault(30),
        iFrameInterval =MinDefault(1),
        colorFormat =ColorFormat.COLOR_FormatSurface,
        bitRateMode = BitRateMode.VBR,
    ), IHDRSupport {
        override fun hdr(profile: Profile?, level:Level?): IVideoStrategy
                = toHdrStrategy(profile, level)
    }
    object HEVC4KLowProfile : VideoStrategy(
        codec = Codec.HEVC,
        profile = Profile.HEVCProfileMain,
        level = Level.HEVCMainTierLevel4,
        fallbackProfiles = null,
        sizeCriteria = UHD2160SizeCriteria,
        bitRate = MaxDefault(50*1000*1000, 30*1000*1000),
        frameRate = MaxDefault(30),
        iFrameInterval =MinDefault(1),
        colorFormat =ColorFormat.COLOR_FormatSurface,
        bitRateMode = BitRateMode.VBR,
    ), IHDRSupport {
        override fun hdr(profile: Profile?, level:Level?): IVideoStrategy
                = toHdrStrategy(profile, level)
    }

    /**
     * 空の VideoStrategy の実装
     * Re-Encoding しないことを明示する場合に使用する。
     */
    object InvalidStrategy : VideoStrategy(
        Codec.Invalid,
        Profile.Invalid,
        null,
        null,
        sizeCriteria = VideoStrategy.SizeCriteria(0,0),
        bitRate = MaxDefault(0),
        frameRate = MaxDefault(0),
        iFrameInterval =MinDefault(1),
        ColorFormat.COLOR_FormatSurface,
        null,
        EncoderType.DEFAULT,
    )
}