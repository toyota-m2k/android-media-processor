package io.github.toyota32k.media.lib.format

import android.media.MediaFormat
import io.github.toyota32k.utils.UtLog

fun MediaFormat.safeGetStringOrNull(key:String):String? {
    return try {
        getString(key)
    } catch (_:Throwable) {
        null
    }
}

fun MediaFormat.safeGetString(key:String, defValue:String):String {
    return safeGetStringOrNull(key) ?: defValue
}

fun MediaFormat.safeGetIntegerOrNull(key:String):Int? {
    return try {
        getInteger(key)
    } catch (_:Throwable) {
        null
    }
}

fun MediaFormat.safeGetInteger(key:String, defValue:Int):Int {
    return safeGetIntegerOrNull(key) ?: defValue
}

fun MediaFormat.getMime():String? = safeGetStringOrNull(MediaFormat.KEY_MIME)
fun MediaFormat.getProfile():Int? = safeGetIntegerOrNull(MediaFormat.KEY_PROFILE)
fun MediaFormat.getLevel():Int? = safeGetIntegerOrNull(MediaFormat.KEY_LEVEL)

fun MediaFormat.getWidth():Int? = safeGetIntegerOrNull(MediaFormat.KEY_WIDTH)
fun MediaFormat.getHeight():Int? = safeGetIntegerOrNull(MediaFormat.KEY_HEIGHT)
fun MediaFormat.getBitRate():Int? = safeGetIntegerOrNull(MediaFormat.KEY_BIT_RATE)
fun MediaFormat.getFrameRate():Int? = safeGetIntegerOrNull(MediaFormat.KEY_FRAME_RATE)
fun MediaFormat.getIFrameInterval():Int? = safeGetIntegerOrNull(MediaFormat.KEY_I_FRAME_INTERVAL)
fun MediaFormat.getColorFormat():Int? = safeGetIntegerOrNull(MediaFormat.KEY_COLOR_FORMAT)
fun MediaFormat.getMaxBitRate():Int? = safeGetIntegerOrNull("max-bitrate")
fun MediaFormat.getBitRateMode():Int? = safeGetIntegerOrNull(MediaFormat.KEY_BITRATE_MODE)

fun MediaFormat.getAacProfile():Int? = safeGetIntegerOrNull(MediaFormat.KEY_AAC_PROFILE) ?: safeGetIntegerOrNull(MediaFormat.KEY_PROFILE)   // profile として保持されるケースがある模様
fun MediaFormat.getSampleRate():Int? = safeGetIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
fun MediaFormat.getChannelCount():Int? = safeGetIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)

fun MediaFormat.dump(logger: UtLog, message:String) {
    logger.info("### $message")
    val codec = Codec.fromFormat(this)
    if(codec == null) {
        logger.error("unknown codec. (${getMime()})")
        return
    }
    logger.info("- $codec")
    val profile = Profile.fromFormat(this)
    if(profile == null) {
        logger.error("unknown profile.")
        return
    }
    if(codec.media.isVideo()) {
        val level = Level.fromFormat(this)
        logger.info("- $profile@${level?:"-"}")
        logger.info("- Width          ${getWidth()?:"n/a"}")
        logger.info("- Height         ${getHeight()?:"n/a"}")
        logger.info("- BitRate        ${getBitRate()?:"n/a"}")
        logger.info("- FrameRate      ${getFrameRate()?:"n/a"}")
        logger.info("- iFrameInterval ${getIFrameInterval()?:"n/a"}")
        logger.info("- colorFormat    ${ColorFormat.fromFormat(this)?:"n/a"}")
    } else {
        logger.info("  $profile")
        logger.info("- SampleRate     ${getSampleRate()}")
        logger.info("- Channels       ${getChannelCount()?:"n/a"}")
        logger.info("- BitRate        ${getBitRate()?:"n/a"}")
    }
}