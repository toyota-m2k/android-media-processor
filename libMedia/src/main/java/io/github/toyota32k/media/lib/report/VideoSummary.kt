package io.github.toyota32k.media.lib.report

import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.BitRateMode
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.getBitRate
import io.github.toyota32k.media.lib.format.getFrameRate
import io.github.toyota32k.media.lib.format.getHeight
import io.github.toyota32k.media.lib.format.getIFrameInterval
import io.github.toyota32k.media.lib.format.getMaxBitRate
import io.github.toyota32k.media.lib.format.getWidth
import java.lang.StringBuilder
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
    constructor(format: MediaFormat) : this(
        Codec.fromFormat(format),
        Profile.fromFormat(format),
        Level.fromFormat(format),

        width = format.getWidth()?:-1,
        height = format.getHeight()?:-1,
        bitRate = format.getBitRate()?:-1,
        maxBitRate = format.getMaxBitRate()?:-1,
        bitRateMode = BitRateMode.fromFormat(format),
        frameRate = format.getFrameRate()?:-1,
        iFrameInterval = format.getIFrameInterval()?:-1,
        colorFormat = ColorFormat.fromFormat(format))

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
            IAttributes.KeyValue("Max Bit Rate","${maxBitRate.format()} bps"),
            IAttributes.KeyValue("Bit Rate Mode", "${bitRateMode ?: "n/a"}"),
            IAttributes.KeyValue("Frame Rate","${frameRate.format()} fps"),
            IAttributes.KeyValue("iFrame Interval", "$iFrameInterval sec"),
            IAttributes.KeyValue("Color Format", "${colorFormat?:"n/a"}"),
        )
    }
}
