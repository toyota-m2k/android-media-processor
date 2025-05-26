package io.github.toyota32k.media.lib.report

import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.BitRateMode
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.bitRate
import io.github.toyota32k.media.lib.format.frameRate
import io.github.toyota32k.media.lib.format.height
import io.github.toyota32k.media.lib.format.iFrameInterval
import io.github.toyota32k.media.lib.format.maxBitRate
import io.github.toyota32k.media.lib.format.width
import java.util.Locale

data class VideoSummary(
    val codec: Codec?,
    val profile: Profile?,
    val level: Level?,
    val width: Int,
    val height: Int,
    val bitRate: Int,
    val maxBitRate:Int,
    val bitRateMode: BitRateMode?,
    val frameRate: Int,
    val iFrameInterval: Int,
    val colorFormat: ColorFormat?) : IAttributes {
    constructor(org:VideoSummary?, format: MediaFormat, metaData: MetaData?) : this(
        org?.codec ?: Codec.fromFormat(format),
        org?.profile ?: Profile.fromFormat(format),
        org?.level ?: Level.fromFormat(format),

        width = format.width?: org?.width ?:-1,
        height = format.height?: org?.height ?:-1,
        bitRate = format.bitRate?: metaData?.bitRate ?: org?.bitRate ?: -1,
        maxBitRate = format.maxBitRate?: org?.maxBitRate ?:-1,
        bitRateMode = BitRateMode.fromFormat(format) ?: org?.bitRateMode,
        frameRate = format.frameRate?: metaData?.frameRate ?: org?.frameRate ?: -1,
        iFrameInterval = format.iFrameInterval?: org?.iFrameInterval ?: -1,
        colorFormat = ColorFormat.fromFormat(format) ?: org?.colorFormat)

    private fun Int.format():String {
        return if(this<0) "n/a" else String.format(Locale.US, "%,d", this)
    }

//    fun dump(logger: UtLog, message:String) {
//        logger.info(message)
//        logger.info("Codec = ${codec?:"n/a"}")
//        logger.info("Profile = ${profile?:"n/a"}")
//        logger.info("Level = ${level?:"n/a"}")
//        logger.info("Width = $width")
//        logger.info("Height = $width")
//        logger.info("Bit Rate = ${bitRate.format()} bps")
//        logger.info("Frame Rate = ${frameRate.format()} fps")
//        logger.info("iFrame Interval = $iFrameInterval sec")
//        logger.info("Color Format = ${colorFormat?:"n/a"}")
//    }

    override fun toString(): String {
        return format(StringBuilder(), "- ").toString()
    }

    override val title: String
        get() = "Video Summary"
    override val subAttributes: List<IAttributes>
        get() = emptyList()

    override fun toList(): List<IAttributes.KeyValue> {
        return listOf(
            IAttributes.KeyValue("Codec", "${codec?:"n/a"}"),
            IAttributes.KeyValue("Profile", "${profile?:"n/a"}"),
            IAttributes.KeyValue("Level", "${level?:"n/a"}"),
            IAttributes.KeyValue("Width", "$width"),
            IAttributes.KeyValue("Height", "$height"),
            IAttributes.KeyValue("Bit Rate", "${bitRate.format()} bps"),
//            IAttributes.KeyValue("Max Bit Rate","${maxBitRate.format()} bps"),     そもそも MediaFormat に存在しない情報
            IAttributes.KeyValue("Bit Rate Mode", "${bitRateMode ?: "n/a"}"),
            IAttributes.KeyValue("Frame Rate","${frameRate.format()} fps"),
            IAttributes.KeyValue("iFrame Interval", "${iFrameInterval.format()} sec"),
            IAttributes.KeyValue("Color Format", "${colorFormat?:"n/a"}"),
        )
    }
}
