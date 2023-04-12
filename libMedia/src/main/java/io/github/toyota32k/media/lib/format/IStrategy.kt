package io.github.toyota32k.media.lib.format

import android.media.MediaFormat
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.utils.UtLog
import java.lang.NullPointerException

interface IStrategy {
    fun createOutputFormat(inputFormat:MediaFormat):MediaFormat
    companion object {
        val logger = UtLog("Strategy", Converter.logger)
    }
}

interface IAudioStrategy : IStrategy
interface IVideoStrategy : IStrategy

/**
 * API29以降なら、defaultValue引数を取るgetInteger()が追加されているが、minTargetVersion = 29はキツいので、名前を変えて定義しておく。
 */
fun MediaFormat.getInt(name: String, defaultValue: Int): Int {
    return try {
        getInteger(name)
    } catch (_: Throwable) {/* no such field or field is null */
        defaultValue
    }
}

fun MediaFormat.getText(name: String, defaultValue: String): String {
    return try {
        getString(name) ?: defaultValue
    } catch (_: Throwable) {/* no such field or field is null */
        defaultValue
    }
}

fun MediaFormat.summary( msg:String="", logger:UtLog=IStrategy.logger) {
    logger.info("---------------------------------------------------")
    logger.info("MediaFormat Summary ... $msg")
    logger.info("- Type           ${getText(MediaFormat.KEY_MIME, "n/a")}")
    logger.info("- Width          ${getInt(MediaFormat.KEY_WIDTH, 0)}")
    logger.info("- Height         ${getInt(MediaFormat.KEY_HEIGHT, 0)}")
    logger.info("- BitRate        ${getInt(MediaFormat.KEY_BIT_RATE, 0)}")
    logger.info("- FrameRate      ${getInt(MediaFormat.KEY_FRAME_RATE, 0)}")
    logger.info("- iFrameInterval ${getInt(MediaFormat.KEY_I_FRAME_INTERVAL, 0)}")
    logger.info("- colorFormat    ${String.format("0x%x",getInt(MediaFormat.KEY_COLOR_FORMAT, 0))}")
    logger.info("---------------------------------------------------")
}

