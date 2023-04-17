package io.github.toyota32k.media.lib.converter

open class BeforeAfter<T>(val before:T?, val after:T?)
class IntBeforeAfter(before:Int?, after:Int?) : BeforeAfter<Int>(before,after) {

}

data class VideoReport(
    val type:BeforeAfter<String>,
    val profile: BeforeAfter<Int>,
    val level: BeforeAfter<Int>,
    val width: BeforeAfter<Int>,
    val height: BeforeAfter<Int>,
    val bitRate: BeforeAfter<Int>,
    val frameRate: BeforeAfter<Int>,
    val iFrameInterval: BeforeAfter<Int>,
    val colorFormat: BeforeAfter<Int>,
) {
}