package io.github.toyota32k.media.lib.codec

import android.media.MediaFormat
import io.github.toyota32k.media.lib.audio.AudioChannel
import io.github.toyota32k.media.lib.track.Muxer

class AudioDecoder(format: MediaFormat):BaseDecoder(format) {
    private val audioChannel = AudioChannel()
    override val sampleType = Muxer.SampleType.Audio

    override fun chainTo(encoder: BaseEncoder) :Boolean {
        return if(!decoderEos) {
            chainTo(
                formatChanged = { decodedFormat ->
                    audioChannel.setActualDecodedFormat(decodedFormat, mediaFormat)
                },
                dataConsumed = { index, length, end, timeUs ->
                    if (length > 0 && trimmingRange.contains(timeUs)) {
                        if(end) {
                            logger.info("render end of data.")
                        }
                        audioChannel.drainDecoderBufferAndQueue(decoder, index, bufferInfo.presentationTimeUs)
                        audioChannel.feedEncoder(decoder, encoder.encoder, 0)
                    }
                    if (end) {
                        logger.debug("found eos")
                        audioChannel.drainDecoderBufferAndQueue(decoder, AudioChannel.BUFFER_INDEX_END_OF_STREAM, 0)
                        audioChannel.feedEncoder(decoder, encoder.encoder, 0)
                        if(audioChannel.eos) {
                            // 入力ストリームを AudioChannel に書き込んだ時点で、
                            // AudioChannel もeosに達している（AudioChannelのバッファに未処理データがない）ので、
                            // デコーダーが eos に達したと判断する。
                            logger.debug("decoder complete (no more buffer in AudioChannel")
                            eos = true
                        }
                    }
                })
        } else if (!eos) {
            // デコーダーの入力（エクストラクターの出力）は eos に達しているが、AudioChannel のバッファにデータが残っている。
            audioChannel.feedEncoder(decoder, encoder.encoder, 0).apply {
                if(audioChannel.eos) {
                    // AudioChannel も eos に達した。
                    logger.debug("decoder complete (AudioChannel flushed")
                    eos = true
                }
            }
        } else {
            false
        }
    }
}