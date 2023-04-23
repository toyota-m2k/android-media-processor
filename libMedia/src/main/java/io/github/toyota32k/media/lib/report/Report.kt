package io.github.toyota32k.media.lib.report

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.utils.TimeSpan
import io.github.toyota32k.media.lib.utils.UtLog

class Report {
    var videoEncoderName: String? = null
    var audioEncoderName: String? = null
    var startTick:Long = 0L
    var endTick:Long = 0L

    val input = Summary()
    val output = Summary()

    fun start() {
        startTick = System.currentTimeMillis()
    }
    fun end() {
        endTick = System.currentTimeMillis()
    }

    private fun updateSummary(summary:Summary, format: MediaFormat) {
        val codec = Codec.Companion.codecOf(format) ?: return
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

    fun dump(logger: UtLog, message:String) {
        logger.info("$message")
        logger.info("Video Encoder = ${videoEncoderName?:"n/a"}")
        logger.info("Audio Encoder = ${audioEncoderName?:"n/a"}")
        val consumed = TimeSpan(endTick - startTick)
        logger.info("Consumed Time = ${consumed.formatH()}")
        input.dump(logger, "Input Stream")
    }
}