package io.github.toyota32k.media.lib.report

import io.github.toyota32k.media.lib.utils.TimeSpan
import io.github.toyota32k.media.lib.utils.UtLog

data class Summary(
    var size:Long = 0L,
    var duration:Long = 0L, //MS
    var videoSummary: VideoSummary? = null,
    var audioSummary: AudioSummary? = null,
) {
    private fun stringInKb(size: Long): String {
        return String.format("%,d KB", size / 1000L)
    }

//    fun dump(logger: UtLog, message:String) {
//        logger.info(message)
//        logger.info("File Size = ${stringInKb(size)}")
//        videoSummary?.dump(logger, ">>> Video") ?: logger.info("no video.")
//        audioSummary?.dump(logger, ">>> Audio") ?: logger.info("no audio.")
//    }

    override fun toString(): String {
        val d = TimeSpan(duration)
        return StringBuilder()
            .appendLine(" - File Size = ${stringInKb(size)}")
            .appendLine(" - Duration = ${d.formatH()}")
            .append(videoSummary.toString())
            .append(audioSummary.toString())
            .toString()
    }
}
