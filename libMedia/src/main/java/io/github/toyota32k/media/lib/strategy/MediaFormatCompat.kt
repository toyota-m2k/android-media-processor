package io.github.toyota32k.media.lib.strategy

import android.media.MediaFormat
import io.github.toyota32k.media.lib.utils.UtLog

data class MediaFormatCompat(val format:MediaFormat) {
    fun getInteger(name:String, defaultValue:Int):Int {
        return try {
            format.getInteger(name)
        } catch (_: Throwable) {/* no such field or field is null */
            defaultValue
        }
    }

    fun getIntegerOrNull(name:String):Int? {
        return try {
            format.getInteger(name)
        } catch (_: Throwable) {/* no such field or field is null */
            null
        }
    }

    fun getString(name: String, defaultValue: String): String {
        return try {
            format.getString(name) ?: defaultValue
        } catch (_: Throwable) {/* no such field or field is null */
            defaultValue
        }
    }

    fun getStringOrNull(name:String):String? {
        return try {
            format.getString(name)
        } catch (_: Throwable) {/* no such field or field is null */
            null
        }
    }

    fun getIntAsHexText(name: String, defaultValue: Int): String {
        return String.format("0x%x",getInteger(name, defaultValue))
    }

    fun summary(msg:String="", logger: UtLog =IStrategy.logger) {
        logger.info("---------------------------------------------------")
        logger.info("MediaFormat Summary ... $msg")
        logger.info("- Type           ${getString(MediaFormat.KEY_MIME, "n/a")}")
        logger.info("- Profile        ${getIntAsHexText(MediaFormat.KEY_PROFILE, 0)}")
        logger.info("- ProfileLevel   ${getIntAsHexText(MediaFormat.KEY_LEVEL, 0)}")
        logger.info("- Width          ${getInteger(MediaFormat.KEY_WIDTH, 0)}")
        logger.info("- Height         ${getInteger(MediaFormat.KEY_HEIGHT, 0)}")
        logger.info("- BitRate        ${getInteger(MediaFormat.KEY_BIT_RATE, 0)}")
        logger.info("- FrameRate      ${getInteger(MediaFormat.KEY_FRAME_RATE, 0)}")
        logger.info("- iFrameInterval ${getInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)}")
        logger.info("- colorFormat    ${getIntAsHexText(MediaFormat.KEY_COLOR_FORMAT, 0)}")
        logger.info("---------------------------------------------------")
    }

    fun getMime():String? = getStringOrNull(MediaFormat.KEY_MIME)
    fun getProfile():Int? = getIntegerOrNull(MediaFormat.KEY_PROFILE)
    fun getLevel():Int? = getIntegerOrNull(MediaFormat.KEY_LEVEL)

    fun getWidth():Int? = getIntegerOrNull(MediaFormat.KEY_WIDTH)
    fun getHeight():Int? = getIntegerOrNull(MediaFormat.KEY_HEIGHT)
    fun getBitRate():Int? = getIntegerOrNull(MediaFormat.KEY_BIT_RATE)
    fun getFrameRate():Int? = getIntegerOrNull(MediaFormat.KEY_FRAME_RATE)
    fun getIFrameInterval():Int? = getIntegerOrNull(MediaFormat.KEY_I_FRAME_INTERVAL)
    fun getColorFormat():Int? = getIntegerOrNull(MediaFormat.KEY_COLOR_FORMAT)

    fun getWidth(def:Int):Int = getWidth() ?: def
    fun getHeight(def:Int):Int = getHeight() ?: def
    fun getBitRate(def:Int):Int = getBitRate() ?: def
    fun getFrameRate(def:Int):Int = getFrameRate() ?: def
    fun getIFrameInterval(def:Int):Int = getIFrameInterval() ?: def
    fun getColorFormat(def:Int):Int = getColorFormat() ?: def

    fun getAacProfile():Int? = getIntegerOrNull(MediaFormat.KEY_AAC_PROFILE)
    fun getSampleRate():Int? = getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
    fun getChannelCount():Int? = getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
    fun getSampleRate(def:Int):Int = getSampleRate() ?: def
    fun getChannelCount(def:Int):Int = getChannelCount() ?: def

}