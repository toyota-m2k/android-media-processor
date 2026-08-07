package io.github.toyota32k.media.lib.report

import android.media.MediaExtractor
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.legacy.extractor.Extractor
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.bitRate
import io.github.toyota32k.media.lib.io.AndroidFile
import io.github.toyota32k.media.lib.processor.optimizer.FastStart
import io.github.toyota32k.media.lib.types.RangeUs.Companion.formatAsMs
import io.github.toyota32k.media.lib.utils.TimeSpan
import java.util.Locale

data class Summary (
    var size:Long = 0L,
    var duration:Long = 0L, //MS
    var videoSummary: VideoSummary? = null,
    var audioSummary: AudioSummary? = null,
    var fastStartCheck: FastStart.CheckResult? = null,
    val iFrameIntervalCalced:Long = -1L
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
            IAttributes.KeyValue("Fast Start", fastStartCheck?.toString() ?: "uav"),
            IAttributes.KeyValue("iFrameInterval(calc)", iFrameIntervalCalced.formatAsMs())
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
        private fun calcIFrameInterval(extractor: MediaExtractor, trackIndex:Int):Long {
            extractor.selectTrack(trackIndex)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            var prev = extractor.sampleTime
            var interval = 0L
            for(i in 0..10) {
                extractor.seekTo(prev + 1, MediaExtractor.SEEK_TO_NEXT_SYNC)
                val t = extractor.sampleTime
                if(t <= prev) break
                interval = t - prev
                prev = t
            }
            return interval /1000L
        }

        fun getSummary(inFile:IInputMediaFile):Summary {
            val fastStartCheck = if (inFile is AndroidFile) {
                FastStart.check(inFile)
            } else null
            return inFile.openExtractor().useObj { extractor ->
                val metaData = MetaData.fromFile(inFile)
                val videoTrack = Extractor.findTrackIdx(extractor, "video")
                val videoSummary = if (videoTrack >= 0) {
                    val videoFormat = Extractor.getMediaFormat(extractor, videoTrack)
                    Converter.logger.info("BitRate = ${videoFormat.bitRate ?: -1L} / ${metaData.bitRate ?: -1L}")
                    VideoSummary(null, videoFormat, metaData)
                } else null
                val iFrameInterval = calcIFrameInterval(extractor, videoTrack)
                val audioTrack = Extractor.findTrackIdx(extractor, "audio")
                val audioSummary = if (audioTrack >= 0) {
                    AudioSummary(null, Extractor.getMediaFormat(extractor, audioTrack))
                } else null
                val duration = metaData.duration ?: -1L
                Summary(inFile.getLength(), duration, videoSummary, audioSummary, fastStartCheck, iFrameInterval)
            }
        }
    }
}
