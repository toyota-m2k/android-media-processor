package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.internals.audio.AudioChannel
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.processor.contract.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.legacy.track.Muxer

class AudioDecoder(strategy:IAudioStrategy, format: MediaFormat, decoder:MediaCodec, report: Report, cancellation: ICancellation)
    :BaseDecoder(strategy, format, decoder, report, cancellation) {
    private val audioChannel = AudioChannel()
    override val sampleType = Muxer.SampleType.Audio
    override val logger = UtLog("Decoder(Audio)", Converter.logger)
    private var decoderEos = false
    private var formatDetected = false  // INFO_OUTPUT_FORMAT_CHANGED を受け取るまで、encoderへの書き込みを抑制するためのフラグ

    private val audioStrategy: IAudioStrategy get() = strategy as IAudioStrategy
    // MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)

    override fun onDecoderEos() {
        // このタイミングでは、まだ eos はセットしない
        decoderEos = true
    }

    /**
     * デコーダーからINFO_OUTPUT_FORMAT_CHANGEDを受け取った時の処理
     * 受け取ったフォーマットに基づいて、エンコーダーを構成・開始する。
     */
    override fun onFormatChanged(format: MediaFormat) {
        super.onFormatChanged(format)   // for log
        audioChannel.setActualDecodedFormat(format, mediaFormat, audioStrategy)
        if(!formatDetected) {   // 二度漬け禁止
            (chainedEncoder as AudioEncoder).configureWithActualSampleRate(audioChannel.outputSampleRate, audioChannel.outputChannelCount, audioChannel.outputBitRate)
            formatDetected = true
        }
    }

    /**
     * デコーダーからデータを読み込んだ時の処理
     * decoder.dequeueOutputBuffer が有効なインデックス(>=0)を返したときに、そのデータを処理する。
     */
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
        if(!formatDetected) {
            // INFO_OUTPUT_FORMAT_CHANGEDを検出する前にデータが読み込まれた
            // このようなことは起きないと思うが念のため防衛しておく
            logger.warn("read data before INFO_OUTPUT_FORMAT_CHANGED")
            onFormatChanged(mediaFormat)
        }
        val result = audioChannel.feedEncoder(decoder, chainedEncoder.encoder)
        if(audioChannel.eos) {
            // AudioChannel が eos に達した。
            logger.debug("decode completion")
            eos = true
        }
        return result
    }

    /**
     * decoderで処理したので、次は、encoderで処理する。
     * ただし、formatDetected == true になるまで（INFO_OUTPUT_FORMAT_CHANGEDを検出するまで）は、encoderの処理は呼ばない。
     */
    override fun afterComsumed(): Boolean {
        return if (formatDetected) {
            super.afterComsumed()   // chainedEncoder.consume()
        } else false
    }

    /**
     * Extractorからデータを読み出してデコードする。
     * @return  true: データを処理した / false: 何もしなかった (no-effect)
     */
    override fun consume() :Boolean {
        if (isCancelled) {
            return false
        }
        if (eos) {
            return super.consume()
        }

        if (!decoderEos) {
            // デコーダーに未処理データが残っている間は、BaseDecoder の consume() を呼ぶ
            if (super.consume()) {
                // super.consume がture を返した場合は、chainedEncoder.consume()は不要
                return true
            }
            if (!formatDetected) {
                // まだフォーマットが確定していなければ encoder にフィードしない
                return false
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