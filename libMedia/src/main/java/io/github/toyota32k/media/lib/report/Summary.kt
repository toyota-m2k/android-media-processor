package io.github.toyota32k.media.lib.report

import android.media.MediaMetadataRetriever
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.misc.safeUse
import io.github.toyota32k.media.lib.track.Track
import io.github.toyota32k.media.lib.utils.TimeSpan
import java.lang.StringBuilder
import java.util.Locale

data class Summary (
    var size:Long = 0L,
    var duration:Long = 0L, //MS
    var videoSummary: VideoSummary? = null,
    var audioSummary: AudioSummary? = null,
) : IAttributes {
    private fun stringInKb(size: Long): String {
        return String.format(Locale.US, "%,d KB", size / 1000L)
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
        fun getSummary(file:AndroidFile):Summary {

            val extractor = Extractor(file)
            val videoTrack = Track.findTrackIdx(extractor.extractor, "video")
            val videoSummary = if (videoTrack >= 0) {
                VideoSummary(Track.getMediaFormat(extractor.extractor, videoTrack))
            } else null
            val audioTrack = Track.findTrackIdx(extractor.extractor, "audio")
            val audioSummary = if(audioTrack >= 0) {
                AudioSummary(Track.getMediaFormat(extractor.extractor, audioTrack))
            } else null
            val duration = file.fileDescriptorToRead { fd-> MediaMetadataRetriever().apply { setDataSource(fd) }}.safeUse { mediaMetadataRetriever ->
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: -1L
            }
            return Summary(file.getLength(), duration, videoSummary, audioSummary)
        }
    }
}
