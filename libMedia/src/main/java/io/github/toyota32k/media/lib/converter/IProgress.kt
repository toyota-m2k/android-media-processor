package io.github.toyota32k.media.lib.converter

/**
 * 進捗報告用 i/f
 */
@Suppress("unused")
interface IProgress {
    val percentage:Int      // progress in percent (0: not available)
    val total:Long          // total duration in us
    val current:Long        // current position in us
    val remainingTime:Long  // expected remaining time in ms (-1: not available)
}