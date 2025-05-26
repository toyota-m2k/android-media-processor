package io.github.toyota32k.media.lib.format

import android.media.MediaFormat

enum class Profile(val codec: Codec, val value:Int) {
    /**
     * AVC Baseline profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileBaseline(Codec.AVC, 0x01),

    /**
     * AVC Main profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileMain(Codec.AVC, 0x02),

    /**
     * AVC Extended profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileExtended(Codec.AVC, 0x04),

    /**
     * AVC High profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileHigh(Codec.AVC, 0x08),

    /**
     * AVC High 10 profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileHigh10(Codec.AVC, 0x10),

    /**
     * AVC High 4:2:2 profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileHigh422(Codec.AVC, 0x20),

    /**
     * AVC High 4:4:4 profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileHigh444(Codec.AVC, 0x40),

    /**
     * AVC Constrained Baseline profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileConstrainedBaseline(Codec.AVC, 0x10000),

    /**
     * AVC Constrained High profile.
     * See definition in
     * <a href="https://www.itu.int/rec/T-REC-H.264-201704-I">H.264 recommendation</a>,
     * Annex A.
     */
    AVCProfileConstrainedHigh(Codec.AVC,  0x80000),

    // HEVC
    HEVCProfileMain        (Codec.HEVC, 0x01),
    HEVCProfileMain10      (Codec.HEVC, 0x02),
    HEVCProfileMainStill   (Codec.HEVC, 0x04),
    HEVCProfileMain10HDR10 (Codec.HEVC, 0x1000),
    HEVCProfileMain10HDR10Plus (Codec.HEVC, 0x2000),


    // H263
    H263ProfileBaseline             (Codec.H263, 0x01),
    H263ProfileH320Coding           (Codec.H263, 0x02),
    H263ProfileBackwardCompatible   (Codec.H263, 0x04),
    H263ProfileISWV2                (Codec.H263, 0x08),
    H263ProfileISWV3                (Codec.H263, 0x10),
    H263ProfileHighCompression      (Codec.H263, 0x20),
    H263ProfileInternet             (Codec.H263, 0x40),
    H263ProfileInterlace            (Codec.H263, 0x80),
    H263ProfileHighLatency          (Codec.H263, 0x100),

    // MPEG4
    MPEG4ProfileSimple              (Codec.MPEG4, 0x01),
    MPEG4ProfileSimpleScalable      (Codec.MPEG4, 0x02),
    MPEG4ProfileCore                (Codec.MPEG4, 0x04),
    MPEG4ProfileMain                (Codec.MPEG4, 0x08),
    MPEG4ProfileNbit                (Codec.MPEG4, 0x10),
    MPEG4ProfileScalableTexture     (Codec.MPEG4, 0x20),
    MPEG4ProfileSimpleFace          (Codec.MPEG4, 0x40),
    MPEG4ProfileSimpleFBA           (Codec.MPEG4, 0x80),
    MPEG4ProfileBasicAnimated       (Codec.MPEG4, 0x100),
    MPEG4ProfileHybrid              (Codec.MPEG4, 0x200),
    MPEG4ProfileAdvancedRealTime    (Codec.MPEG4, 0x400),
    MPEG4ProfileCoreScalable        (Codec.MPEG4, 0x800),
    MPEG4ProfileAdvancedCoding      (Codec.MPEG4, 0x1000),
    MPEG4ProfileAdvancedCore        (Codec.MPEG4, 0x2000),
    MPEG4ProfileAdvancedScalable    (Codec.MPEG4, 0x4000),
    MPEG4ProfileAdvancedSimple      (Codec.MPEG4, 0x8000),

    MPEG2ProfileSimple              (Codec.MPEG2, 0x00),
    MPEG2ProfileMain                (Codec.MPEG2, 0x01),
    MPEG2Profile422                 (Codec.MPEG2, 0x02),
    MPEG2ProfileSNR                 (Codec.MPEG2, 0x03),
    MPEG2ProfileSpatial             (Codec.MPEG2, 0x04),
    MPEG2ProfileHigh                (Codec.MPEG2, 0x05),


    // VP8
    VP8ProfileMain                  (Codec.VP8, 0x01),

    // VP9
    /** VP9 Profile 0 4:2:0 8-bit */
    VP9Profile0 (Codec.VP9, 0x01),

    /** VP9 Profile 1 4:2:2 8-bit */
    VP9Profile1 (Codec.VP9, 0x02),

    /** VP9 Profile 2 4:2:0 10-bit */
    VP9Profile2 (Codec.VP9, 0x04),

    /** VP9 Profile 3 4:2:2 10-bit */
    VP9Profile3 (Codec.VP9, 0x08),

    // HDR profiles also support passing HDR metadata
    /** VP9 Profile 2 4:2:0 10-bit HDR */
    VP9Profile2HDR (Codec.VP9, 0x1000),

    /** VP9 Profile 3 4:2:2 10-bit HDR */
    VP9Profile3HDR (Codec.VP9, 0x2000),

    /** VP9 Profile 2 4:2:0 10-bit HDR10Plus */
    VP9Profile2HDR10Plus (Codec.VP9, 0x4000),

    /** VP9 Profile 3 4:2:2 10-bit HDR10Plus */
    VP9Profile3HDR10Plus (Codec.VP9, 0x8000),

    DolbyVisionProfileDvavPer (Codec.DolbyVision, 0x1),
    DolbyVisionProfileDvavPen (Codec.DolbyVision, 0x2),
    DolbyVisionProfileDvheDer (Codec.DolbyVision, 0x4),
    DolbyVisionProfileDvheDen (Codec.DolbyVision, 0x8),
    DolbyVisionProfileDvheDtr (Codec.DolbyVision, 0x10),
    DolbyVisionProfileDvheStn (Codec.DolbyVision, 0x20),
    DolbyVisionProfileDvheDth (Codec.DolbyVision, 0x40),
    DolbyVisionProfileDvheDtb (Codec.DolbyVision, 0x80),
    DolbyVisionProfileDvheSt  (Codec.DolbyVision, 0x100),
    DolbyVisionProfileDvavSe  (Codec.DolbyVision, 0x200),
    /** Dolby Vision AV1 profile */
    DolbyVisionProfileDvav110 (Codec.DolbyVision, 0x400),


    // Profiles and levels for AV1 Codec, corresponding to the definitions in
    // "AV1 Bitstream & Decoding Process Specification", Annex A
    // found at https://aomedia.org/av1-bitstream-and-decoding-process-specification/

    /**
     * AV1 Main profile 4:2:0 8-bit
     *
     * See definition in
     * <a href="https://aomedia.org/av1-bitstream-and-decoding-process-specification/">AV1 Specification</a>
     * Annex A.
     */
    AV1ProfileMain8   (Codec.AV1, 0x1),

    /**
     * AV1 Main profile 4:2:0 10-bit
     *
     * See definition in
     * <a href="https://aomedia.org/av1-bitstream-and-decoding-process-specification/">AV1 Specification</a>
     * Annex A.
     */
    AV1ProfileMain10  (Codec.AV1, 0x2),


    /** AV1 Main profile 4:2:0 10-bit with HDR10. */
    AV1ProfileMain10HDR10 (Codec.AV1, 0x1000),

    /** AV1 Main profile 4:2:0 10-bit with HDR10Plus. */
    AV1ProfileMain10HDR10Plus (Codec.AV1, 0x2000),


    // KEY_AAC_PROFILE
    AACObjectMain       (Codec.AAC, 1),
    AACObjectLC         (Codec.AAC, 2),
    AACObjectSSR        (Codec.AAC, 3),
    AACObjectLTP        (Codec.AAC, 4),
    AACObjectHE         (Codec.AAC, 5),
    AACObjectScalable   (Codec.AAC, 6),
    AACObjectERLC       (Codec.AAC, 17),
    AACObjectERScalable (Codec.AAC, 20),
    AACObjectLD         (Codec.AAC, 23),
    AACObjectHE_PS      (Codec.AAC, 29),
    AACObjectELD        (Codec.AAC, 39),
    AACObjectXHE        (Codec.AAC, 42),

    ;
    companion object {
        fun fromValue(codec: Codec, value:Int): Profile? {
            return entries.firstOrNull {
                it.codec == codec && it.value == value
            }
        }

        fun fromFormat(mediaFormat: MediaFormat): Profile? {
            val codec = Codec.fromFormat(mediaFormat) ?: return null
            val profile = if(codec.media==Media.Video)  { mediaFormat.profile } else { mediaFormat.aacProfile } ?: return null
            return fromValue(codec, profile)
        }
    }
}