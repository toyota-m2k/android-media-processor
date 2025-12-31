package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.types.RangeUs.Companion.us2ms
import io.github.toyota32k.utils.TimeSpan
import java.util.Locale

/**
 * 進捗報告用 i/f
 */
interface IProgress {
    val permyriad:Int       // progress in permyriad (0: not available)
        get() = if(total==0L) 0 else ((current * 10000L)/total).coerceIn(0L,10000L).toInt()
    val permillage:Int      // progress in permillage (0: not available)
        get() = permyriad/10
    val percentage:Int      // progress in percent (0: not available)
        get() = permyriad/100

    enum class ValueUnit {
        US, // us
        MS, // ms
        BYTES
    }

    val total:Long          // total duration in ValueUnit
    val current:Long        // current position in ValueUnit
    val remainingTime:Long  // expected remaining time in ms (-1: not available)

    val valueUnit:ValueUnit

    fun format():String {
        fun formatTimeMs(timeMs: Long, durationMs: Long): String {
            val v = TimeSpan(timeMs)
            val t = TimeSpan(durationMs)
            return when {
                t.hours > 0 -> "${v.formatH()}/${t.formatH()}"
                t.minutes > 0 -> "${v.formatM()}/${t.formatM()}"
                else -> "${v.formatS()}/${t.formatS()}"
            }
        }

        fun formatTimeUs(timeUs: Long, durationUs: Long): String {
            return formatTimeMs(timeUs / 1000L, durationUs / 1000L)
        }


        fun formatSize(bytes: Long, totalBytes: Long): String {
            fun format(n:Long): String= String.format(Locale.getDefault(), "%,d", n)
            return if (totalBytes > 1000_000_000) {
                // > 1GB
                val mc = bytes / (1000 * 1000)
                val mt = totalBytes / (1000 * 1000)
                "${format(mc)} / ${format(mt)} MB"
            } else if (totalBytes > 1000_000) {
                // " MB"
                val mc = bytes / 1000
                val mt = totalBytes / 1000
                "${format(mc)} / ${format(mt)} KB"
            } else {
                "${format(bytes)} / ${format(totalBytes)} Bytes"
            }
        }

        val progressText = when (valueUnit) {
            ValueUnit.US -> formatTimeUs(current, total)
            ValueUnit.MS -> formatTimeMs(current, total)
            ValueUnit.BYTES -> formatSize(current, total)
        }

        val remaining = if(remainingTime>0) {
            val r = TimeSpan(remainingTime)
            when {
                r.hours>0 -> r.formatH()
                r.minutes>0 -> r.formatM()
                else -> "${remainingTime/1000}\""
            }
        } else null

        val percent = "${permillage/10}.${permillage%10} %"

        return if(remaining!=null) {
            "$percent ($progressText) -- $remaining left."
        } else "$percent ($progressText)"
    }
}
