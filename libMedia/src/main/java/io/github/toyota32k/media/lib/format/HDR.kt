package io.github.toyota32k.media.lib.format

import android.media.MediaFormat
import android.os.Build
import java.nio.ByteBuffer

class HDR {
    enum class ColorStandard(val value: Int) {
        // BT.709 color chromaticity coordinates with KR = 0.2126, KB = 0.0722.
        BT709(MediaFormat.COLOR_STANDARD_BT709),        // 1

        // BT.601 625 color chromaticity coordinates with KR = 0.299, KB = 0.114.
        BT601_PAL(MediaFormat.COLOR_STANDARD_BT601_PAL),    // 2

        // BT.601 525 color chromaticity coordinates with KR = 0.299, KB = 0.114.
        BT601_NTSC(MediaFormat.COLOR_STANDARD_BT601_NTSC),  // 4

        // BT.2020 color chromaticity coordinates with KR = 0.2627, KB = 0.0593.
        BT2020(MediaFormat.COLOR_STANDARD_BT2020),          // 6

        ;

        companion object {
            fun from(value: Int): ColorStandard? {
                return entries.firstOrNull { it.value == value }
            }
            fun fromFormat(format: MediaFormat): ColorStandard? {
                if(!format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) return null
                val v = format.getInteger(MediaFormat.KEY_COLOR_STANDARD)
                return from(v)
            }
        }
    }

    enum class ColorRange(val value: Int) {
        // The YUV values are in the full range of [0, 255].
        FULL(MediaFormat.COLOR_RANGE_FULL),    // 1

        // The YUV values are in the limited range of [16, 235] for Y and [16, 240] for U and V.
        LIMITED(MediaFormat.COLOR_RANGE_LIMITED),  // 2
        ;
        companion object {
            fun from(value: Int): ColorRange? {
                return entries.firstOrNull { it.value == value }
            }
            fun fromFormat(format: MediaFormat): ColorRange? {
                if(!format.containsKey(MediaFormat.KEY_COLOR_RANGE)) return null
                val v = format.getInteger(MediaFormat.KEY_COLOR_RANGE)
                return from(v)
            }
        }
    }

    enum class ColorTransfer(val value: Int) {
        // Linear transfer characteristic curve.
        LINEAR(MediaFormat.COLOR_TRANSFER_LINEAR),          // 1

        // SMPTE 170M transfer characteristic curve used by BT.601/BT.709/BT.2020. This is the curve used by most non-HDR video content.
        SDR_VIDEO(MediaFormat.COLOR_TRANSFER_SDR_VIDEO),    // 3

        // SMPTE ST 2084 transfer function. This is used by some HDR video content.
        ST2084(MediaFormat.COLOR_TRANSFER_ST2084),      // 6

        // ARIB STD-B67 hybrid-log-gamma transfer function. This is used by some HDR video content.
        HLG(MediaFormat.COLOR_TRANSFER_HLG),            // 7: Hybrid Log-Gamma

        ;
        companion object {
            fun from(value: Int): ColorTransfer? {
                return entries.firstOrNull { it.value == value }
            }
            fun fromFormat(format: MediaFormat): ColorTransfer? {
                if(!format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) return null
                val v = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                return from(v)
            }
        }
    }

    data class Info(
        val colorStandard: ColorStandard?,
        val colorRange: ColorRange?,
        val colorTransfer: ColorTransfer?,
        val hdrStaticInfo: ByteBuffer? = null,
        val hdr10PlusInfo: ByteBuffer? = null
    ) {
        companion object {
            private fun getHdr10PlusInfo(format: MediaFormat): ByteBuffer? {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    format.getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO)
                } else {
                    null
                }
            }
            fun fromFormat(format: MediaFormat): Info {
                return Info(
                    colorStandard = ColorStandard.fromFormat(format),
                    colorRange = ColorRange.fromFormat(format),
                    colorTransfer = ColorTransfer.fromFormat(format),
                    hdrStaticInfo = format.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO),
                    hdr10PlusInfo = getHdr10PlusInfo(format)
                )
            }
        }
        val isHDR:Boolean
            get() = colorTransfer==ColorTransfer.ST2084 || colorTransfer==ColorTransfer.HLG
    }
}