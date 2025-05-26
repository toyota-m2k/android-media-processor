package io.github.toyota32k.media.lib.format

import android.media.MediaFormat
import android.os.Build
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

//fun MediaFormat.getMime():String? = safeGetStringOrNull(MediaFormat.KEY_MIME)
//fun MediaFormat.getProfile():Int? = safeGetIntegerOrNull(MediaFormat.KEY_PROFILE)
//fun MediaFormat.getLevel():Int? = safeGetIntegerOrNull(MediaFormat.KEY_LEVEL)
//
//fun MediaFormat.getWidth():Int? = safeGetIntegerOrNull(MediaFormat.KEY_WIDTH)
//fun MediaFormat.getHeight():Int? = safeGetIntegerOrNull(MediaFormat.KEY_HEIGHT)
//fun MediaFormat.getBitRate():Int? = safeGetIntegerOrNull(MediaFormat.KEY_BIT_RATE)
//fun MediaFormat.getFrameRate():Int? = safeGetIntegerOrNull(MediaFormat.KEY_FRAME_RATE)
//fun MediaFormat.getIFrameInterval():Int? = safeGetIntegerOrNull(MediaFormat.KEY_I_FRAME_INTERVAL)
//fun MediaFormat.getColorFormat():Int? = safeGetIntegerOrNull(MediaFormat.KEY_COLOR_FORMAT)
//fun MediaFormat.getMaxBitRate():Int? = safeGetIntegerOrNull("max-bitrate")
//fun MediaFormat.getBitRateMode():Int? = safeGetIntegerOrNull(MediaFormat.KEY_BITRATE_MODE)
//
//fun MediaFormat.getAacProfile():Int? = safeGetIntegerOrNull(MediaFormat.KEY_AAC_PROFILE) ?: safeGetIntegerOrNull(MediaFormat.KEY_PROFILE)   // profile として保持されるケースがある模様
//fun MediaFormat.getSampleRate():Int? = safeGetIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
//fun MediaFormat.getChannelCount():Int? = safeGetIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)

fun MediaFormat.putMime(mime:String): MediaFormat {
    setString(MediaFormat.KEY_MIME, mime)
    return this
}
fun MediaFormat.putProfile(v:Int): MediaFormat {
    setInteger(MediaFormat.KEY_PROFILE, v)
    return this
}
fun MediaFormat.putLevel(v:Int): MediaFormat {
    setInteger(MediaFormat.KEY_LEVEL, v)
    return this
}
fun MediaFormat.putWidth(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_WIDTH, v)
    return this
}
fun MediaFormat.putHeight(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_HEIGHT,v)
    return this
}
fun MediaFormat.putBitRate(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_BIT_RATE,v)
    return this
}
fun MediaFormat.putFrameRate(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_FRAME_RATE,v)
    return this
}
fun MediaFormat.putIFrameInterval(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,v)
    return this
}
fun MediaFormat.putColorFormat(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_COLOR_FORMAT,v)
    return this
}
fun MediaFormat.putMaxBitRate(v:Int):MediaFormat {
    setInteger("max-bitrate",v)
    return this
}
fun MediaFormat.putBitRateMode(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_BITRATE_MODE,v)
    return this
}

//fun MediaFormat.putAacProfile(v:Int):MediaFormat { setInteger(MediaFormat.KEY_AAC_PROFILE,v) ?: safeGetIntegerOrNull(MediaFormat.KEY_PROFILE)   // profile として保持されるケースがある模様
fun MediaFormat.putSampleRate(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_SAMPLE_RATE,v)
    return this
}
fun MediaFormat.putChannelCount(v:Int):MediaFormat {
    setInteger(MediaFormat.KEY_CHANNEL_COUNT,v)
    return this
}

fun MediaFormat.setIntegerOrNull(key:String, value:Int?) {
    if(value == null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            removeKey(key)
        } else {
            // MediaFormat.setString(key,value)
            //  If value is null, it sets a null value that behaves similarly to a missing key.
            //  This could be used prior to API level android os.Build.VERSION_CODES#Q to effectively
            //  remove a key.
            setString(key, null)    // removeKey の代用
        }
    } else {
        setInteger(key, value)
    }
}

var MediaFormat.mime:String?
    get() = safeGetStringOrNull(MediaFormat.KEY_MIME)
    set(v) = setString(MediaFormat.KEY_MIME, v)
var MediaFormat.profile:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_PROFILE)
    set(v) = setIntegerOrNull(MediaFormat.KEY_PROFILE, v)
var MediaFormat.level:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_LEVEL)
    set(v) = setIntegerOrNull(MediaFormat.KEY_LEVEL, v)
var MediaFormat.width:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_WIDTH)
    set(v) = setIntegerOrNull(MediaFormat.KEY_WIDTH, v)
var MediaFormat.height:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_HEIGHT)
    set(v) = setIntegerOrNull(MediaFormat.KEY_HEIGHT, v)
var MediaFormat.bitRate:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_BIT_RATE)
    set(v) = setIntegerOrNull(MediaFormat.KEY_BIT_RATE, v)
var MediaFormat.frameRate:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_FRAME_RATE)
    set(v) = setIntegerOrNull(MediaFormat.KEY_FRAME_RATE, v)
var MediaFormat.iFrameInterval:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_I_FRAME_INTERVAL)
    set(v) = setIntegerOrNull(MediaFormat.KEY_I_FRAME_INTERVAL, v)
var MediaFormat.colorFormat:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_COLOR_FORMAT)
    set(v) = setIntegerOrNull(MediaFormat.KEY_COLOR_FORMAT, v)
val MediaFormat.maxBitRate:Int?
    get() = safeGetIntegerOrNull("max-bitrate")
var MediaFormat.bitRateMode:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_BITRATE_MODE)
    set(v) = setIntegerOrNull(MediaFormat.KEY_BITRATE_MODE, v)
val MediaFormat.aacProfile:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_AAC_PROFILE) ?: safeGetIntegerOrNull(MediaFormat.KEY_PROFILE)   // profile として保持されるケースがある模様
var MediaFormat.sampleRate:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
    set(v) = setIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE, v)
var MediaFormat.channelCount:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
    set(v) = setIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT, v)
val MediaFormat.duration:Int?
    get() = safeGetIntegerOrNull(MediaFormat.KEY_DURATION)

fun MediaFormat.dump(logger: UtLog, message:String) {
    logger.info("### $message")
    val codec = Codec.fromFormat(this)
    if(codec == null) {
        logger.error("unknown codec. (${mime})")
        return
    }
    logger.info("- $codec")
    val profile = Profile.fromFormat(this)
    if(profile == null) {
        logger.error("unknown profile.")
//        return
    }
    if(codec.media.isVideo()) {
        val level = Level.fromFormat(this)
        logger.info("- $profile@${level?:"-"}")
        logger.info("- Width          ${width?:"n/a"}")
        logger.info("- Height         ${height?:"n/a"}")
        logger.info("- BitRate        ${bitRate?:"n/a"}")
        logger.info("- FrameRate      ${frameRate?:"n/a"}")
        logger.info("- iFrameInterval ${iFrameInterval?:"n/a"}")
        logger.info("- colorFormat    ${ColorFormat.fromFormat(this)?:"n/a"}")
        logger.info("- duration       ${duration?:"n/a"}")
    } else {
        logger.info("  $profile")
        logger.info("- SampleRate     ${sampleRate?:"n/a"}")
        logger.info("- Channels       ${channelCount?:"n/a"}")
        logger.info("- BitRate        ${bitRate?:"n/a"}")
        logger.info("- duration       ${duration?:"n/a"}")
    }
}