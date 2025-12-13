package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.audio.AudioChannel
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.bitRate
import io.github.toyota32k.media.lib.format.channelCount
import io.github.toyota32k.media.lib.format.mime
import io.github.toyota32k.media.lib.format.sampleRate
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy

class EncodeAudioTrack(inPath:IInputMediaFile, inputMetaData: MetaData, maxDurationUs:Long, bufferSource: IBufferSource, report: Report, val strategy: IAudioStrategy)
    : AbstractEncodeTrack(inPath, inputMetaData, maxDurationUs, bufferSource, report, video=false) {
    private lateinit var audioChannel : AudioChannel
    private var formatDetected = false  // INFO_OUTPUT_FORMAT_CHANGED を受け取るまで、encoderへの書き込みを抑制するためのフラグ

    override fun startRange(startFromUS: Long):Long {
        if (!isAvailable) return 0L
        audioChannel = AudioChannel()
        mDecoder = MediaCodec.createDecoderByType(inputTrackMediaFormat.mime ?: extractor.getTrackFormat(trackIndex).mime!!)
            .apply {
                // configure は不要？
                configure(inputTrackMediaFormat, null, null, 0)
                start()
            }
        mEncoder = strategy.createEncoder().apply {
            outputTrackMediaFormat = strategy.createOutputFormat(inputTrackMediaFormat,  this)
            //configure(outputTrackMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // encoder はまだ start() しない。デコーダーがINFO_OUTPUT_FORMAT_CHANGEDを受け取ってから start する。
        }
        return super.startRange(startFromUS)
    }

    override fun endRange() {
        mDecoder?.apply {
            stop()
            release()
        }
        mDecoder = null
        mEncoder?.apply {
            stop()
            release()
        }
        mEncoder = null
    }

    override fun onDecoderOutputFormatChanged(formatFromDecoder: MediaFormat) {
        logger.verbose()
        audioChannel.setActualDecodedFormat(formatFromDecoder, inputTrackMediaFormat, strategy)
        if(!formatDetected) {   // 二度漬け禁止
            configureWithActualSampleRate(audioChannel.outputSampleRate, audioChannel.outputChannelCount, audioChannel.outputBitRate)
            formatDetected = true
        }
    }

    /**
     * 真のサンプリングレート&チャネル数が判明したとき（デコーダーがMediaCodec.INFO_OUTPUT_FORMAT_CHANGEDを受け取った時）に、
     * エンコーダーを configure / start する。
     */
    private fun configureWithActualSampleRate(sampleRate:Int, channelCount:Int, bitrate:Int) {
        val encoder = mEncoder ?: throw IllegalStateException("encoder is already closed.")
        val mediaFormat = outputTrackMediaFormat
        var modified = false
        if(sampleRate != mediaFormat.sampleRate) {
            mediaFormat.sampleRate = sampleRate
            modified = true
        }
        if(channelCount != mediaFormat.channelCount) {
            if(channelCount!=1 && channelCount!=2) {
                throw UnsupportedOperationException("Input channel count ($channelCount) not supported.")
            }
            mediaFormat.channelCount = channelCount
            modified = true
        }
        if(bitrate != mediaFormat.bitRate) {
            mediaFormat.bitRate = bitrate
            modified = true
        }
        if(modified) {
            report.updateOutputSummary(mediaFormat)
        }
        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        logger.debug("AudioEncoder configured.")
    }


    override fun transformAndTransfer(index: Int, bufferInfo: MediaCodec.BufferInfo, eos: Boolean) {
        val decoder = mDecoder ?: throw IllegalStateException("decoder is already closed.")
        val length = bufferInfo.size
        if (length > 0) {
            if(eos) {
                logger.info("render end of data.")
            }
            audioChannel.drainDecoderBufferAndQueue(decoder, index, bufferInfo.presentationTimeUs)
        }
        if (eos) {
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
            onDecoderOutputFormatChanged(outputTrackMediaFormat)
        }
        val decoder = mDecoder ?: throw IllegalStateException("decoder is already closed.")
        val encoder = mEncoder ?: throw IllegalStateException("encoder is already closed.")
        val result = audioChannel.feedEncoder(decoder, encoder)
        if(audioChannel.eos) {
            // AudioChannel が eos に達した。
            logger.debug("decode completion")
            eosDecoder = true
        }
        return result
    }

    override fun encoderToMuxer(bufferInfo: MediaCodec.BufferInfo): Boolean {
        if (!formatDetected) {
            return false
        }
        return super.encoderToMuxer(bufferInfo)
    }

}