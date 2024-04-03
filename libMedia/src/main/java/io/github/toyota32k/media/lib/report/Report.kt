package io.github.toyota32k.media.lib.report

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.utils.TimeSpan
import java.lang.StringBuilder

class Report : IAttributes {
    var videoEncoderName: String? = null
    var audioEncoderName: String? = null
    var startTick:Long = 0L
    var endTick:Long = 0L

    val input = Summary().apply { title = "Input Stream"}
    val output = Summary().apply { title = "Output Stream"}

    fun start() {
        startTick = System.currentTimeMillis()
    }
    fun end() {
        endTick = System.currentTimeMillis()
    }

    private fun updateSummary(summary:Summary, format: MediaFormat) {
        val codec = Codec.Companion.fromFormat(format) ?: return
        if(codec.media.isVideo()) {
            summary.videoSummary = VideoSummary(format)
        } else {
            summary.audioSummary = AudioSummary(format)
        }
    }

    fun updateInputSummary(format: MediaFormat) {
        updateSummary(input, format)
    }
    fun updateOutputSummary(format: MediaFormat) {
        updateSummary(output, format)
    }
    fun updateVideoEncoder(encoder:MediaCodec) {
        videoEncoderName = encoder.name
    }
    fun updateAudioEncoder(encoder:MediaCodec) {
        audioEncoderName = encoder.name
    }
    fun updateInputFileInfo(size:Long, duration:Long) {
        input.size = size
        input.duration = duration
    }
    fun updateOutputFileInfo(size:Long, duration:Long) {
        output.size = size
        output.duration = duration
    }

    override var title: String = "Conversion Results"
    override val subAttributes: List<IAttributes?>
        get() = listOf(input, output)

    override fun toList(): List<IAttributes.KeyValue> {
        return listOf(
            IAttributes.KeyValue("Video Encoder", videoEncoderName ?: "n/a"),
            IAttributes.KeyValue("Audio Encoder", audioEncoderName?:"n/a"),
            IAttributes.KeyValue("Consumed Time", TimeSpan(endTick - startTick).formatH()),
            )
    }

//    fun dump(logger: UtLog, message:String) {
//        logger.info(message)
//        logger.info("Video Encoder = ${videoEncoderName?:"n/a"}")
//        logger.info("Audio Encoder = ${audioEncoderName?:"n/a"}")
//        val consumed = TimeSpan(endTick - startTick)
//        logger.info("Consumed Time = ${consumed.formatH()}")
//        input.dump(logger, "Input Stream")
//        output.dump(logger, "Output Stream")
//    }

    override fun toString(): String {
        return format(StringBuilder(), "- ").toString()
    }
}