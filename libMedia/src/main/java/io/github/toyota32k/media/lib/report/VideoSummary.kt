package io.github.toyota32k.media.lib.report

import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.getBitRate
import io.github.toyota32k.media.lib.format.getFrameRate
import io.github.toyota32k.media.lib.format.getHeight
import io.github.toyota32k.media.lib.format.getIFrameInterval
import io.github.toyota32k.media.lib.format.getWidth
import io.github.toyota32k.media.lib.utils.UtLog
import java.util.logging.Logger

data class VideoSummary(
    val codec: Codec?,
    val profile: Profile?,
    val level: Level?,
    val width: Int,
    val height: Int,
    val bitRate: Int,
    val frameRate: Int,
    val iFrameInterval: Int,
    val colorFormat: ColorFormat?) {
    constructor(format: MediaFormat) : this(
        Codec.codecOf(format),
        Profile.fromValue(format),
        Level.fromValue(format),

        format.getWidth()?:-1,
        format.getHeight()?:-1,
        format.getBitRate()?:-1,
        format.getFrameRate()?:-1,
    format.getIFrameInterval()?:-1,
        ColorFormat.fromValue(format))

    private fun Int.format():String {
        return String.format("%,d", this)
    }

    fun dump(logger: UtLog, message:String) {
        logger.info(message)
        logger.info("Codec = ${codec?:"n/a"}")
        logger.info("Profile = ${profile?:"n/a"}")
        logger.info("Level = ${level?:"n/a"}")
        logger.info("Width = $width")
        logger.info("Height = $width")
        logger.info("Bit Rate = ${bitRate.format()} bps")
        logger.info("Frame Rate = ${frameRate.format()} fps")
        logger.info("iFrame Interval = $iFrameInterval sec")
        logger.info("Color Format = ${colorFormat?:"n/a"}")
    }
}
