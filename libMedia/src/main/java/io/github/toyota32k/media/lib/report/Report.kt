package io.github.toyota32k.media.lib.report

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.utils.TimeSpan
import java.lang.StringBuilder
import java.text.DecimalFormat
import kotlin.math.roundToLong

class Report : IAttributes {
    var videoDecoderName: String? = null
    var videoEncoderName: String? = null
    var audioDecoderName: String? = null
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

    private fun updateSummary(summary:Summary, format: MediaFormat, metaData: MetaData?=null) {
        val codec = Codec.fromFormat(format) ?: return
        if(codec.media.isVideo()) {
            summary.videoSummary = VideoSummary(format, metaData)
        } else {
            summary.audioSummary = AudioSummary(format)
        }
    }

    fun updateInputSummary(format: MediaFormat, metaData: MetaData?) {
        updateSummary(input, format, metaData)
    }
    fun updateOutputSummary(format: MediaFormat) {
        updateSummary(output, format)
    }
    fun updateVideoDecoder(decoder:MediaCodec) {
        videoDecoderName = decoder.name
    }
    fun updateAudioDecoder(decoder:MediaCodec) {
        audioDecoderName = decoder.name
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

    var sourceDurationUs:Long = 0L
    var videoExtractedDurationUs:Long = 0L
    var audioExtractedDurationUs:Long = 0L
    var muxerDurationUs:Long = 0L
    fun setDurationInfo(
        sourceDurationUs: Long,
        videoExtractedDurationUs: Long,
        audioExtractedDurationUs: Long,
        muxerDurationUs: Long
    ) {
        this.sourceDurationUs = sourceDurationUs
        this.videoExtractedDurationUs = videoExtractedDurationUs
        this.audioExtractedDurationUs = audioExtractedDurationUs
        this.muxerDurationUs = muxerDurationUs
    }

    override var title: String = "Conversion Results"
    override val subAttributes: List<IAttributes?>
        get() = listOf(input, output)

    override fun toList(): List<IAttributes.KeyValue> {
        val speed = if (endTick > startTick) {
            (input.size.toDouble() / (endTick-startTick).toDouble()).roundToLong()
        } else 0L
        val speedText = if (speed > 0) {
            "${DecimalFormat("#,###").format(speed)} bytes/sec"
        } else {
            "n/a"
        }
        return listOf(
            IAttributes.KeyValue("Video Decoder", videoDecoderName ?: "n/a"),
            IAttributes.KeyValue("Video Encoder", videoEncoderName ?: "n/a"),
            IAttributes.KeyValue("Audio Decoder", audioDecoderName ?: "n/a"),
            IAttributes.KeyValue("Audio Encoder", audioEncoderName ?: "n/a"),
            IAttributes.KeyValue("Consumed Time", TimeSpan(endTick - startTick).formatH()),
            IAttributes.KeyValue("Speed", speedText),
            IAttributes.KeyValue("Duration(Input)", TimeSpan(sourceDurationUs/1000).formatH()),
            IAttributes.KeyValue("Extracted(Video)", TimeSpan(videoExtractedDurationUs/1000).formatH()),
            IAttributes.KeyValue("Extracted(Audio)", TimeSpan(audioExtractedDurationUs/1000).formatH()),
            IAttributes.KeyValue("Muxer", TimeSpan(muxerDurationUs/1000).formatH()),
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