package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.audio.AudioChannel
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.track.Muxer

class AudioDecoder(format: MediaFormat, decoder:MediaCodec, report: Report, cancellation: ICancellation)
    :BaseDecoder(format, decoder, report, cancellation) {
    private val audioChannel = AudioChannel()
    override val sampleType = Muxer.SampleType.Audio
    private var decoderEos = false

    // MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)

    override fun onDecoderEos() {
        // このタイミングでは、まだ eos はセットしない
        decoderEos = true
    }

    override fun onFormatChanged(format: MediaFormat) {
        audioChannel.setActualDecodedFormat(format, mediaFormat)
    }

    override fun onDataConsumed(index: Int, length: Int, end: Boolean) {
        if (length > 0 /*&& trimmingRangeList.isValidPosition(timeUs)*/) {
            if(end) {
                logger.info("render end of data.")
            }
            audioChannel.drainDecoderBufferAndQueue(decoder, index, bufferInfo.presentationTimeUs)
        }
        if (end) {
            logger.debug("found eos")
            audioChannel.drainDecoderBufferAndQueue(decoder, AudioChannel.BUFFER_INDEX_END_OF_STREAM, 0)
        }
        feedEncoder()
    }

    private fun feedEncoder() : Boolean {
        val result = audioChannel.feedEncoder(decoder, chainedEncoder.encoder)
        if(audioChannel.eos) {
            // AudioChannel が eos に達した。
            logger.debug("decode completion")
            eos = true
        }
        return result
    }

    override fun consume() :Boolean {
        if (isCancelled || eos) {
            return false
        }

        if (!decoderEos) {
            // デコーダーに未処理データが残っている間は、BaseDecoder の consume() を呼ぶ
            if (super.consume()) {
                // super.consume がture を返した場合は、chainedEncoder.consume()は不要
                return true
            }
        }

        // デコーダーの入力（エクストラクターの出力）が eos（decoderEos==true)に達していても、AudioChannel のバッファにデータが残っていることがある。
        // また、入力データが読めなくても (super.consume()==false)、バッファにデータが残っていることがある。
        // そのため、ここで feedしておかないと終了しなくなる可能性がある（というか実際に問題の発生を確認した）ので、
        // encoder.consume()を呼ぶ前に、一度、feedToEncoder()を実行しておく
        val effected = feedEncoder()
        return chainedEncoder.consume() || effected
    }
}