package io.github.toyota32k.media.lib.converter


interface IReporter {
    val formattedBefore:String
    val formattedAfter : String
}
abstract class ReporterBase<T>():IReporter {
    var before:T? = null
    var after: T? = null
    abstract fun format(v:T?):String
    override val formattedBefore:String
        get() = format(before)
    override val formattedAfter : String
        get() = format(after)
}
class IntBeforeAfter() : ReporterBase<Int>() {
    override fun format(v: Int?): String {
        return if(v!=null) "$v" else "n/a"
    }
}

class HexIntBeforeAfter() : ReporterBase<Int>() {
    override fun format(v: Int?): String {
        return v?.toString(16) ?: "n/a"
    }
}

class StringBeforeAfter() : ReporterBase<String>() {
    override fun format(v: String?): String {
        return v ?: "n/a"
    }
}

class VideoReport() {
    val type = StringBeforeAfter()
    val profile = HexIntBeforeAfter()
    val level = HexIntBeforeAfter()
    val width = IntBeforeAfter()
    val height = IntBeforeAfter()
    val bitRate = IntBeforeAfter()
    val frameRate = IntBeforeAfter()
    val iFrameInterval = IntBeforeAfter()
    val colorFormat = HexIntBeforeAfter()
}

class AudioReport() {
    val type = StringBeforeAfter()
    val profile = HexIntBeforeAfter()
    val bitRate = IntBeforeAfter()
    val sampleRate = IntBeforeAfter()
    val channel = IntBeforeAfter()
}

class Report {
    val video = VideoReport()
    val audio = AudioReport()
}