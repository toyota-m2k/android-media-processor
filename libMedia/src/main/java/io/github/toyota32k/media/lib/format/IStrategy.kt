package io.github.toyota32k.media.lib.format

import android.media.MediaFormat
import java.lang.NullPointerException

interface IStrategy {
    fun createOutputFormat(inputFormat:MediaFormat):MediaFormat
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

