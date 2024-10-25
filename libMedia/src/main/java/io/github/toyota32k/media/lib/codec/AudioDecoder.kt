package io.github.toyota32k.media.lib.codec

import android.media.MediaFormat
import io.github.toyota32k.media.lib.audio.AudioChannel
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.track.Muxer
import kotlin.compareTo

class AudioDecoder(format: MediaFormat, report: Report, cancellation: ICancellation):BaseDecoder(format, report, cancellation) {
    private val audioChannel = AudioChannel()
    override val sampleType = Muxer.SampleType.Audio
    private var decoderEos = false

    override fun onDecoderEos() {
        decoderEos = true
    }

    override fun onFormatChanged(format: MediaFormat) {
        audioChannel.setActualDecodedFormat(format, mediaFormat)
    }

    override fun onDataConsumed(index: Int, length: Int, end: Boolean) {
        val encoder = chainedEncoder.encoder
        if (length > 0 /*&& trimmingRangeList.isValidPosition(timeUs)*/) {
            if(end) {
                logger.info("render end of data.")
            }
            audioChannel.drainDecoderBufferAndQueue(decoder, index, bufferInfo.presentationTimeUs)
            audioChannel.feedEncoder(decoder, encoder, 0)
        }
        if (end) {
            logger.debug("found eos")
            audioChannel.drainDecoderBufferAndQueue(decoder, AudioChannel.BUFFER_INDEX_END_OF_STREAM, 0)
            audioChannel.feedEncoder(decoder, encoder, 0)
            if(audioChannel.eos) {
                // 入力ストリームを AudioChannel に書き込んだ時点で、
                // AudioChannel もeosに達している（AudioChannelのバッファに未処理データがない）ので、
                // デコーダーが eos に達したと判断する。
                logger.debug("decoder complete (no more buffer in AudioChannel")
                eos = true
            }
        }
    }

    override fun consume() :Boolean {
        if(isCancelled) {
            return false
        }

        if(!decoderEos) {
            // デコーダーに未処理データが残っている間は、BaseDecoder の consume() に処理を任せる。
            return super.consume()
        }

        // デコーダーの入力（エクストラクターの出力）は eos に達しているが、AudioChannel のバッファにデータが残っている。
        val effected = if (!eos) {
            audioChannel.feedEncoder(decoder, chainedEncoder.encoder, 0).apply {
                if(audioChannel.eos) {
                    // AudioChannel も eos に達した。
                    logger.debug("decoder complete (AudioChannel flushed)")
                    eos = true
                }
            }
            true
        } else {
            false
        }
        return chainedEncoder.consume() || effected
    }
}