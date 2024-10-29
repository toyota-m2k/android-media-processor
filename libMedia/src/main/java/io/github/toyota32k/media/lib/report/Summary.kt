package io.github.toyota32k.media.lib.report

import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.getBitRate
import io.github.toyota32k.media.lib.utils.TimeSpan
import java.util.Locale

data class Summary (
    var size:Long = 0L,
    var duration:Long = 0L, //MS
    var videoSummary: VideoSummary? = null,
    var audioSummary: AudioSummary? = null,
) : IAttributes {
    private fun stringInKb(size: Long): String {
        return if(size<0) {
            "n/a"
        } else {
            String.format(Locale.US, "%,d KB", size / 1000L)
        }
    }

    override var title: String = "Summary"
    override val subAttributes: List<IAttributes?>
        get() = listOf(videoSummary, audioSummary)

    override fun toList(): List<IAttributes.KeyValue> {
        return listOf(
            IAttributes.KeyValue("File Size", stringInKb(size)),
            IAttributes.KeyValue("Duration", TimeSpan(duration).formatH()),
        )
    }

//    fun dump(logger: UtLog, message:String) {
//        logger.info(message)
//        logger.info("File Size = ${stringInKb(size)}")
//        videoSummary?.dump(logger, ">>> Video") ?: logger.info("no video.")
//        audioSummary?.dump(logger, ">>> Audio") ?: logger.info("no audio.")
//    }

    override fun toString(): String {
        return format(StringBuilder(), "- ").toString()
    }

    companion object {
        fun getSummary(inFile:IInputMediaFile):Summary {
            return inFile.openExtractor().useObj { extractor ->
                val metaData = MetaData.fromFile(inFile)
                val videoTrack = Extractor.findTrackIdx(extractor, "video")
                val videoSummary = if (videoTrack >= 0) {
                    val videoFormat = Extractor.getMediaFormat(extractor, videoTrack)
                    Converter.logger.info("BitRate = ${videoFormat.getBitRate() ?: -1L} / ${metaData.bitRate ?: -1L}")
                    VideoSummary(videoFormat, metaData)
                } else null
                val audioTrack = Extractor.findTrackIdx(extractor, "audio")
                val audioSummary = if (audioTrack >= 0) {
                    AudioSummary(Extractor.getMediaFormat(extractor, audioTrack))
                } else null
                val duration = metaData.duration ?: -1L
                Summary(inFile.getLength(), duration, videoSummary, audioSummary)
            }
        }
    }
}
