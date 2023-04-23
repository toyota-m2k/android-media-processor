package io.github.toyota32k.media.lib.format

import android.media.MediaFormat

enum class ColorFormat(val value:Int) {
    // from MediaCodecConstants
    /** @deprecated Use {@link #COLOR_Format24bitBGR888}. */
    COLOR_FormatMonochrome              (1),
    /** @deprecated Use {@link #COLOR_Format24bitBGR888}. */
    COLOR_Format8bitRGB332              (2),
    /** @deprecated Use {@link #COLOR_Format24bitBGR888}. */
    COLOR_Format12bitRGB444             (3),
    /** @deprecated Use {@link #COLOR_Format32bitABGR8888}. */
    COLOR_Format16bitARGB4444           (4),
    /** @deprecated Use {@link #COLOR_Format32bitABGR8888}. */
    COLOR_Format16bitARGB1555           (5),

    /**
     * 16 bits per pixel RGB color format, with 5-bit red & blue and 6-bit green component.
     * <p>
     * Using 16-bit little-endian representation, colors stored as Red 15:11, Green 10:5, Blue 4:0.
     * <pre>
     *            byte                   byte
     *  <--------- i --------> | <------ i + 1 ------>
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |     BLUE     |      GREEN      |     RED      |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     *  0           4  5     7   0     2  3           7
     * bit
     * </pre>
     *
     * This format corresponds to {@link android.graphics.PixelFormat#RGB_565} and
     * {@link android.graphics.ImageFormat#RGB_565}.
     */
    COLOR_Format16bitRGB565             (6),
    /** @deprecated Use {@link #COLOR_Format16bitRGB565}. */
    COLOR_Format16bitBGR565             (7),
    /** @deprecated Use {@link #COLOR_Format24bitBGR888}. */
    COLOR_Format18bitRGB666             (8),
    /** @deprecated Use {@link #COLOR_Format32bitABGR8888}. */
    COLOR_Format18bitARGB1665           (9),
    /** @deprecated Use {@link #COLOR_Format32bitABGR8888}. */
    COLOR_Format19bitARGB1666           (10),

    /** @deprecated Use {@link #COLOR_Format24bitBGR888} or {@link #COLOR_FormatRGBFlexible}. */
    COLOR_Format24bitRGB888             (11),

    /**
     * 24 bits per pixel RGB color format, with 8-bit red, green & blue components.
     * <p>
     * Using 24-bit little-endian representation, colors stored as Red 7:0, Green 15:8, Blue 23:16.
     * <pre>
     *         byte              byte             byte
     *  <------ i -----> | <---- i+1 ----> | <---- i+2 ----->
     * +-----------------+-----------------+-----------------+
     * |       RED       |      GREEN      |       BLUE      |
     * +-----------------+-----------------+-----------------+
     * </pre>
     *
     * This format corresponds to {@link android.graphics.PixelFormat#RGB_888}, and can also be
     * represented as a flexible format by {@link #COLOR_FormatRGBFlexible}.
     */
    COLOR_Format24bitBGR888             (12),
    /** @deprecated Use {@link #COLOR_Format32bitABGR8888}. */
    COLOR_Format24bitARGB1887           (13),
    /** @deprecated Use {@link #COLOR_Format32bitABGR8888}. */
    COLOR_Format25bitARGB1888           (14),

    /**
     * @deprecated Use {@link #COLOR_Format32bitABGR8888} Or {@link #COLOR_FormatRGBAFlexible}.
     */
    COLOR_Format32bitBGRA8888           (15),
    /**
     * @deprecated Use {@link #COLOR_Format32bitABGR8888} Or {@link #COLOR_FormatRGBAFlexible}.
     */
    COLOR_Format32bitARGB8888           (16),
    /** @deprecated Use {@link #COLOR_FormatYUV420Flexible}. */
    COLOR_FormatYUV411Planar            (17),
    /** @deprecated Use {@link #COLOR_FormatYUV420Flexible}. */
    COLOR_FormatYUV411PackedPlanar      (18),
    /** @deprecated Use {@link #COLOR_FormatYUV420Flexible}. */
    COLOR_FormatYUV420Planar            (19),
    /** @deprecated Use {@link #COLOR_FormatYUV420Flexible}. */
    COLOR_FormatYUV420PackedPlanar      (20),
    /** @deprecated Use {@link #COLOR_FormatYUV420Flexible}. */
    COLOR_FormatYUV420SemiPlanar        (21),

    /** @deprecated Use {@link #COLOR_FormatYUV422Flexible}. */
    COLOR_FormatYUV422Planar            (22),
    /** @deprecated Use {@link #COLOR_FormatYUV422Flexible}. */
    COLOR_FormatYUV422PackedPlanar      (23),
    /** @deprecated Use {@link #COLOR_FormatYUV422Flexible}. */
    COLOR_FormatYUV422SemiPlanar        (24),

    /** @deprecated Use {@link #COLOR_FormatYUV422Flexible}. */
    COLOR_FormatYCbYCr                  (25),
    /** @deprecated Use {@link #COLOR_FormatYUV422Flexible}. */
    COLOR_FormatYCrYCb                  (26),
    /** @deprecated Use {@link #COLOR_FormatYUV422Flexible}. */
    COLOR_FormatCbYCrY                  (27),
    /** @deprecated Use {@link #COLOR_FormatYUV422Flexible}. */
    COLOR_FormatCrYCbY                  (28),

    /** @deprecated Use {@link #COLOR_FormatYUV444Flexible}. */
    COLOR_FormatYUV444Interleaved       (29),

    /**
     * SMIA 8-bit Bayer format.
     * Each byte represents the top 8-bits of a 10-bit signal.
     */
    COLOR_FormatRawBayer8bit            (30),
    /**
     * SMIA 10-bit Bayer format.
     */
    COLOR_FormatRawBayer10bit           (31),

    /**
     * SMIA 8-bit compressed Bayer format.
     * Each byte represents a sample from the 10-bit signal that is compressed into 8-bits
     * using DPCM/PCM compression, as defined by the SMIA Functional Specification.
     */
    COLOR_FormatRawBayer8bitcompressed  (32),

    /** @deprecated Use {@link #COLOR_FormatL8}. */
    COLOR_FormatL2                      (33),
    /** @deprecated Use {@link #COLOR_FormatL8}. */
    COLOR_FormatL4                      (34),

    /**
     * 8 bits per pixel Y color format.
     * <p>
     * Each byte contains a single pixel.
     * This format corresponds to {@link android.graphics.PixelFormat#L_8}.
     */
    COLOR_FormatL8                      (35),

    /**
     * 16 bits per pixel, little-endian Y color format.
     * <p>
     * <pre>
     *            byte                   byte
     *  <--------- i --------> | <------ i + 1 ------>
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                       Y                       |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     *  0                    7   0                    7
     * bit
     * </pre>
     */
    COLOR_FormatL16                     (36),
    /** @deprecated Use {@link #COLOR_FormatL16}. */
    COLOR_FormatL24                     (37),

    /**
     * 32 bits per pixel, little-endian Y color format.
     * <p>
     * <pre>
     *         byte              byte             byte              byte
     *  <------ i -----> | <---- i+1 ----> | <---- i+2 ----> | <---- i+3 ----->
     * +-----------------+-----------------+-----------------+-----------------+
     * |                                   Y                                   |
     * +-----------------+-----------------+-----------------+-----------------+
     *  0               7 0               7 0               7 0               7
     * bit
     * </pre>
     *
     * @deprecated Use {@link #COLOR_FormatL16}.
     */
    COLOR_FormatL32                     (38),

    /** @deprecated Use {@link #COLOR_FormatYUV420Flexible}. */
    COLOR_FormatYUV420PackedSemiPlanar  (39),
    /** @deprecated Use {@link #COLOR_FormatYUV422Flexible}. */
    COLOR_FormatYUV422PackedSemiPlanar  (40),

    /** @deprecated Use {@link #COLOR_Format24bitBGR888}. */
    COLOR_Format18BitBGR666             (41),

    /** @deprecated Use {@link #COLOR_Format32bitABGR8888}. */
    COLOR_Format24BitARGB6666           (42),
    /** @deprecated Use {@link #COLOR_Format32bitABGR8888}. */
    COLOR_Format24BitABGR6666           (43),

    /**
     * P010 is 10-bit-per component 4:2:0 YCbCr semiplanar format.
     * <p>
     * This format uses 24 allocated bits per pixel with 15 bits of
     * data per pixel. Chroma planes are subsampled by 2 both
     * horizontally and vertically. Each chroma and luma component
     * has 16 allocated bits in little-endian configuration with 10
     * MSB of actual data.
     *
     * <pre>
     *            byte                   byte
     *  <--------- i --------> | <------ i + 1 ------>
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |     UNUSED      |      Y/Cb/Cr                |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     *  0               5 6   7 0                    7
     * bit
     * </pre>
     *
     * Use this format with {@link Image}. This format corresponds
     * to {@link android.graphics.ImageFormat#YCBCR_P010}.
     * <p>
     */
    COLOR_FormatYUVP010                 (54),

    /** @deprecated Use {@link #COLOR_FormatYUV420Flexible}. */
    COLOR_TI_FormatYUV420PackedSemiPlanar (0x7f000100),
    // COLOR_FormatSurface indicates that the data will be a GraphicBuffer metadata reference.
    // Note: in OMX this is called OMX_COLOR_FormatAndroidOpaque.
    COLOR_FormatSurface                   (0x7F000789),

    /**
     * 64 bits per pixel RGBA color format, with 16-bit signed
     * floating point red, green, blue, and alpha components.
     * <p>
     *
     * <pre>
     *         byte              byte             byte              byte
     *  <-- i -->|<- i+1 ->|<- i+2 ->|<- i+3 ->|<- i+4 ->|<- i+5 ->|<- i+6 ->|<- i+7 ->
     * +---------+---------+-------------------+---------+---------+---------+---------+
     * |        RED        |       GREEN       |       BLUE        |       ALPHA       |
     * +---------+---------+-------------------+---------+---------+---------+---------+
     *  0       7 0       7 0       7 0       7 0       7 0       7 0       7 0       7
     * </pre>
     *
     * This corresponds to {@link android.graphics.PixelFormat#RGBA_F16}.
     */
    COLOR_Format64bitABGRFloat            (0x7F000F16),

    /**
     * 32 bits per pixel RGBA color format, with 8-bit red, green, blue, and alpha components.
     * <p>
     * Using 32-bit little-endian representation, colors stored as Red 7:0, Green 15:8,
     * Blue 23:16, and Alpha 31:24.
     * <pre>
     *         byte              byte             byte              byte
     *  <------ i -----> | <---- i+1 ----> | <---- i+2 ----> | <---- i+3 ----->
     * +-----------------+-----------------+-----------------+-----------------+
     * |       RED       |      GREEN      |       BLUE      |      ALPHA      |
     * +-----------------+-----------------+-----------------+-----------------+
     * </pre>
     *
     * This corresponds to {@link android.graphics.PixelFormat#RGBA_8888}.
     */
    COLOR_Format32bitABGR8888             (0x7F00A000),

    /**
     * 32 bits per pixel RGBA color format, with 10-bit red, green,
     * blue, and 2-bit alpha components.
     * <p>
     * Using 32-bit little-endian representation, colors stored as
     * Red 9:0, Green 19:10, Blue 29:20, and Alpha 31:30.
     * <pre>
     *         byte              byte             byte              byte
     *  <------ i -----> | <---- i+1 ----> | <---- i+2 ----> | <---- i+3 ----->
     * +-----------------+---+-------------+-------+---------+-----------+-----+
     * |       RED           |      GREEN          |       BLUE          |ALPHA|
     * +-----------------+---+-------------+-------+---------+-----------+-----+
     *  0               7 0 1 2           7 0     3 4       7 0         5 6   7
     * </pre>
     *
     * This corresponds to {@link android.graphics.PixelFormat#RGBA_1010102}.
     */
    COLOR_Format32bitABGR2101010          (0x7F00AAA2),

    /**
     * Flexible 12 bits per pixel, subsampled YUV color format with 8-bit chroma and luma
     * components.
     * <p>
     * Chroma planes are subsampled by 2 both horizontally and vertically.
     * Use this format with {@link Image}.
     * This format corresponds to {@link android.graphics.ImageFormat#YUV_420_888},
     * and can represent the {@link #COLOR_FormatYUV411Planar},
     * {@link #COLOR_FormatYUV411PackedPlanar}, {@link #COLOR_FormatYUV420Planar},
     * {@link #COLOR_FormatYUV420PackedPlanar}, {@link #COLOR_FormatYUV420SemiPlanar}
     * and {@link #COLOR_FormatYUV420PackedSemiPlanar} formats.
     *
     * @see Image#getFormat
     */
    COLOR_FormatYUV420Flexible            (0x7F420888),

    /**
     * Flexible 16 bits per pixel, subsampled YUV color format with 8-bit chroma and luma
     * components.
     * <p>
     * Chroma planes are horizontally subsampled by 2. Use this format with {@link Image}.
     * This format corresponds to {@link android.graphics.ImageFormat#YUV_422_888},
     * and can represent the {@link #COLOR_FormatYCbYCr}, {@link #COLOR_FormatYCrYCb},
     * {@link #COLOR_FormatCbYCrY}, {@link #COLOR_FormatCrYCbY},
     * {@link #COLOR_FormatYUV422Planar}, {@link #COLOR_FormatYUV422PackedPlanar},
     * {@link #COLOR_FormatYUV422SemiPlanar} and {@link #COLOR_FormatYUV422PackedSemiPlanar}
     * formats.
     *
     * @see Image#getFormat
     */
    COLOR_FormatYUV422Flexible            (0x7F422888),

    /**
     * Flexible 24 bits per pixel YUV color format with 8-bit chroma and luma
     * components.
     * <p>
     * Chroma planes are not subsampled. Use this format with {@link Image}.
     * This format corresponds to {@link android.graphics.ImageFormat#YUV_444_888},
     * and can represent the {@link #COLOR_FormatYUV444Interleaved} format.
     * @see Image#getFormat
     */
    COLOR_FormatYUV444Flexible            (0x7F444888),

    /**
     * Flexible 24 bits per pixel RGB color format with 8-bit red, green and blue
     * components.
     * <p>
     * Use this format with {@link Image}. This format corresponds to
     * {@link android.graphics.ImageFormat#FLEX_RGB_888}, and can represent
     * {@link #COLOR_Format24bitBGR888} and {@link #COLOR_Format24bitRGB888} formats.
     * @see Image#getFormat()
     */
    COLOR_FormatRGBFlexible               (0x7F36B888),

    /**
     * Flexible 32 bits per pixel RGBA color format with 8-bit red, green, blue, and alpha
     * components.
     * <p>
     * Use this format with {@link Image}. This format corresponds to
     * {@link android.graphics.ImageFormat#FLEX_RGBA_8888}, and can represent
     * {@link #COLOR_Format32bitBGRA8888}, {@link #COLOR_Format32bitABGR8888} and
     * {@link #COLOR_Format32bitARGB8888} formats.
     *
     * @see Image#getFormat()
     */
    COLOR_FormatRGBAFlexible              (0x7F36A888),

    /** @deprecated Use {@link #COLOR_FormatYUV420Flexible}. */
    COLOR_QCOM_FormatYUV420SemiPlanar     (0x7fa30c00),
    ;
    companion object {
        fun fromValue(value:Int):ColorFormat? {
            return values().firstOrNull {
                it.value == value
            }
        }
        fun fromValue(format:MediaFormat):ColorFormat? {
            val cf = format.getColorFormat() ?: return null
            return fromValue(cf)
        }
    }
}