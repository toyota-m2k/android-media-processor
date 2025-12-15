package io.github.toyota32k.media.lib.processor.contract

import android.util.Log
import io.github.toyota32k.logger.UtLog

interface IFormattable {
    fun format(sb:StringBuilder=StringBuilder()):StringBuilder
    companion object {
        fun StringBuilder.format(obj:IFormattable):StringBuilder {
            return obj.format(this)
        }
        fun UtLog.dump(obj:IFormattable, level:Int = Log.DEBUG) {
            print(level, obj.format().toString())
        }
    }
}

fun Int.format3digits():String {
    return "%,d".format(this)
}
fun Long.format3digits():String {
    return "%,d".format(this)
}

