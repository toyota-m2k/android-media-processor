package io.github.toyota32k.media.lib.report

import android.media.MediaMetadataRetriever
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.track.Track
import io.github.toyota32k.media.lib.utils.TimeSpan

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
            val duration = file.fileDescriptorToRead { fd-> MediaMetadataRetriever().apply { setDataSource(fd) }}.use { mediaMetadataRetriever ->
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: -1L
            }
            return Summary(file.getLength(), duration, videoSummary, audioSummary)
        }
    }
}
