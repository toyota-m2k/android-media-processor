package io.github.toyota32k.media.lib.converter

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