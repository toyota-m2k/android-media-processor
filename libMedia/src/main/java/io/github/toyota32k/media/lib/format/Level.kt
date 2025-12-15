package io.github.toyota32k.media.lib.format

import android.media.MediaFormat

enum class Level(val codec: Codec, val value:Int) {
    AVCLevel1(Codec.AVC, 0x01),
    AVCLevel1b(Codec.AVC, 0x02),
    AVCLevel11(Codec.AVC, 0x04),
    AVCLevel12(Codec.AVC, 0x08),
    AVCLevel13(Codec.AVC, 0x10),
    AVCLevel2(Codec.AVC, 0x20),
    AVCLevel21(Codec.AVC, 0x40),
    AVCLevel22(Codec.AVC, 0x80),
    AVCLevel3(Codec.AVC, 0x100),
    AVCLevel31(Codec.AVC, 0x200),
    AVCLevel32(Codec.AVC, 0x400),
    AVCLevel4(Codec.AVC, 0x800),
    AVCLevel41(Codec.AVC, 0x1000),
    AVCLevel42(Codec.AVC, 0x2000),
    AVCLevel5(Codec.AVC, 0x4000),
    AVCLevel51(Codec.AVC, 0x8000),
    AVCLevel52(Codec.AVC, 0x10000),
    AVCLevel6(Codec.AVC, 0x20000),
    AVCLevel61(Codec.AVC, 0x40000),
    AVCLevel62(Codec.AVC, 0x80000),

    HEVCMainTierLevel1(Codec.HEVC, 0x1),
    HEVCHighTierLevel1(Codec.HEVC, 0x2),
    HEVCMainTierLevel2(Codec.HEVC, 0x4),
    HEVCHighTierLevel2(Codec.HEVC, 0x8),
    HEVCMainTierLevel21(Codec.HEVC, 0x10),
    HEVCHighTierLevel21(Codec.HEVC, 0x20),
    HEVCMainTierLevel3(Codec.HEVC, 0x40),
    HEVCHighTierLevel3(Codec.HEVC, 0x80),
    HEVCMainTierLevel31(Codec.HEVC, 0x100),
    HEVCHighTierLevel31(Codec.HEVC, 0x200),
    HEVCMainTierLevel4(Codec.HEVC, 0x400),
    HEVCHighTierLevel4(Codec.HEVC, 0x800),
    HEVCMainTierLevel41(Codec.HEVC, 0x1000),
    HEVCHighTierLevel41(Codec.HEVC, 0x2000),
    HEVCMainTierLevel5(Codec.HEVC, 0x4000),
    HEVCHighTierLevel5(Codec.HEVC, 0x8000),
    HEVCMainTierLevel51(Codec.HEVC, 0x10000),
    HEVCHighTierLevel51(Codec.HEVC, 0x20000),
    HEVCMainTierLevel52(Codec.HEVC, 0x40000),
    HEVCHighTierLevel52(Codec.HEVC, 0x80000),
    HEVCMainTierLevel6(Codec.HEVC, 0x100000),
    HEVCHighTierLevel6(Codec.HEVC, 0x200000),
    HEVCMainTierLevel61(Codec.HEVC, 0x400000),
    HEVCHighTierLevel61(Codec.HEVC, 0x800000),
    HEVCMainTierLevel62(Codec.HEVC, 0x1000000),
    HEVCHighTierLevel62(Codec.HEVC, 0x2000000),

    H263Level10(Codec.H263, 0x01),
    H263Level20(Codec.H263, 0x02),
    H263Level30(Codec.H263, 0x04),
    H263Level40(Codec.H263, 0x08),
    H263Level45(Codec.H263, 0x10),
    H263Level50(Codec.H263, 0x20),
    H263Level60(Codec.H263, 0x40),
    H263Level70(Codec.H263, 0x80),

    MPEG4Level0(Codec.MPEG4, 0x01),
    MPEG4Level0b(Codec.MPEG4, 0x02),
    MPEG4Level1(Codec.MPEG4, 0x04),
    MPEG4Level2(Codec.MPEG4, 0x08),
    MPEG4Level3(Codec.MPEG4, 0x10),
    MPEG4Level3b(Codec.MPEG4, 0x18),
    MPEG4Level4(Codec.MPEG4, 0x20),
    MPEG4Level4a(Codec.MPEG4, 0x40),
    MPEG4Level5(Codec.MPEG4, 0x80),
    MPEG4Level6(Codec.MPEG4, 0x100),

    MPEG2LevelLL(Codec.MPEG2, 0x00),
    MPEG2LevelML(Codec.MPEG2, 0x01),
    MPEG2LevelH14(Codec.MPEG2, 0x02),
    MPEG2LevelHL(Codec.MPEG2, 0x03),
    MPEG2LevelHP(Codec.MPEG2, 0x04),

    VP8Level_Version0(Codec.VP8, 0x01),
    VP8Level_Version1(Codec.VP8, 0x02),
    VP8Level_Version2(Codec.VP8, 0x04),
    VP8Level_Version3(Codec.VP8, 0x08),

    VP9Level1(Codec.VP9, 0x1),
    VP9Level11(Codec.VP9, 0x2),
    VP9Level2(Codec.VP9, 0x4),
    VP9Level21(Codec.VP9, 0x8),
    VP9Level3(Codec.VP9, 0x10),
    VP9Level31(Codec.VP9, 0x20),
    VP9Level4(Codec.VP9, 0x40),
    VP9Level41(Codec.VP9, 0x80),
    VP9Level5(Codec.VP9, 0x100),
    VP9Level51(Codec.VP9, 0x200),
    VP9Level52(Codec.VP9, 0x400),
    VP9Level6(Codec.VP9, 0x800),
    VP9Level61(Codec.VP9, 0x1000),
    VP9Level62(Codec.VP9, 0x2000),

    DolbyVisionLevelHd24(Codec.DolbyVision, 0x1),
    DolbyVisionLevelHd30(Codec.DolbyVision, 0x2),
    DolbyVisionLevelFhd24(Codec.DolbyVision, 0x4),
    DolbyVisionLevelFhd30(Codec.DolbyVision, 0x8),
    DolbyVisionLevelFhd60(Codec.DolbyVision, 0x10),
    DolbyVisionLevelUhd24(Codec.DolbyVision, 0x20),
    DolbyVisionLevelUhd30(Codec.DolbyVision, 0x40),
    DolbyVisionLevelUhd48(Codec.DolbyVision, 0x80),
    DolbyVisionLevelUhd60(Codec.DolbyVision, 0x100),
    DolbyVisionLevelUhd120(Codec.DolbyVision, 0x200),
    DolbyVisionLevel8k30(Codec.DolbyVision, 0x400),
    DolbyVisionLevel8k60(Codec.DolbyVision, 0x800),

    AV1Level2(Codec.AV1, 0x1),
    AV1Level21(Codec.AV1, 0x2),
    AV1Level22(Codec.AV1, 0x4),
    AV1Level23(Codec.AV1, 0x8),
    AV1Level3(Codec.AV1, 0x10),
    AV1Level31(Codec.AV1, 0x20),
    AV1Level32(Codec.AV1, 0x40),
    AV1Level33(Codec.AV1, 0x80),
    AV1Level4(Codec.AV1, 0x100),
    AV1Level41(Codec.AV1, 0x200),
    AV1Level42(Codec.AV1, 0x400),
    AV1Level43(Codec.AV1, 0x800),
    AV1Level5(Codec.AV1, 0x1000),
    AV1Level51(Codec.AV1, 0x2000),
    AV1Level52(Codec.AV1, 0x4000),
    AV1Level53(Codec.AV1, 0x8000),
    AV1Level6(Codec.AV1, 0x10000),
    AV1Level61(Codec.AV1, 0x20000),
    AV1Level62(Codec.AV1, 0x40000),
    AV1Level63(Codec.AV1, 0x80000),
    AV1Level7(Codec.AV1, 0x100000),
    AV1Level71(Codec.AV1, 0x200000),
    AV1Level72(Codec.AV1, 0x400000),
    AV1Level73(Codec.AV1, 0x800000),

    ;

    companion object {
        fun fromValue(codec: Codec, value:Int):Level? {
            return entries.firstOrNull {
                it.codec == codec && it.value == value
            }
        }

        fun fromFormat(format: MediaFormat?):Level? {
            val codec = Codec.fromFormat(format) ?: return null
            val level = format?.level ?: return null
            return fromValue(codec, level)
        }
    }
}