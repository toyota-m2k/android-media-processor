package io.github.toyota32k.media.lib.converter

import io.github.toyota32k.utils.TimeSpan

/**
 * 進捗報告用 i/f
 */
@Suppress("unused")
interface IProgress {
    val permyriad:Int       // progress in permyriad (0: not available)
        get() = if(total==0L) 0 else ((current * 10000L)/total).coerceIn(0L,10000L).toInt()
    val permillage:Int      // progress in permillage (0: not available)
        get() = permyriad/10
    val percentage:Int      // progress in percent (0: not available)
        get() = permyriad/100

    val total:Long          // total duration in us
    val current:Long        // current position in us
    val remainingTime:Long  // expected remaining time in ms (-1: not available)
}

/**
 * 〇〇% (current/total) -- remaining HH:MM:SS
 * の形式にフォーマットする
 */
fun IProgress.format():String {
    fun formatTime(time:Long, duration:Long) : String {
        val v = TimeSpan(time/1000)
        val t = TimeSpan(duration/1000)
        return when {
            t.hours>0 -> v.formatH()
            t.minutes>0 -> v.formatM()
            else -> v.formatS()
        }
    }

    val remaining = if(remainingTime>0) {
        val r = TimeSpan(remainingTime)
        when {
            r.hours>0 -> r.formatH()
            r.minutes>0 -> r.formatM()
            else -> "${remainingTime/1000}\""
        }
    } else null

    fun formatPercent(permillage:Int):String {
        return "${permillage/10}.${permillage%10} %"
    }
    return if(remaining!=null) {
        "${formatPercent(permillage)} (${formatTime(current, total)}/${formatTime(total,total)}) -- $remaining left."
    } else "${formatPercent(permillage)} (${formatTime(current, total)}/${formatTime(total,total)})"
}
