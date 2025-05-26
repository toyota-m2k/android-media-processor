package io.github.toyota32k.media.lib.audio

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.utils.UtLog
import java.lang.RuntimeException
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.*

class AudioChannel {
    companion object {
        const val BUFFER_INDEX_END_OF_STREAM = -1
        val logger = UtLog("AC", Converter.logger)
        private const val BYTES_PER_SHORT = 2
        private const val MICROSECS_PER_SEC: Long = 1000000
    }
    private class AudioBuffer {
        var bufferIndex = 0
        var presentationTimeUs: Long = 0
        var data: ShortBuffer? = null
    }

    private val mEmptyBuffers: Queue<AudioBuffer> = ArrayDeque()
    private val mFilledBuffers: Queue<AudioBuffer> = ArrayDeque()

    private val hasData:Boolean get() = mFilledBuffers.isNotEmpty()
    private var inputEos:Boolean = false
    val eos:Boolean
        get() = inputEos && !hasData

    private var mInputSampleRate = 0
    private var mInputChannelCount = 0
    private var mOutputChannelCount = 0
    val outputChannelCount: Int get() = mOutputChannelCount

    private lateinit var mRemixer: AudioRemixer

    private val mOverflowBuffer = AudioBuffer()

    private lateinit var mActualDecodedFormat: MediaFormat

    fun setActualDecodedFormat(actualFormat: MediaFormat, presetFormat: MediaFormat, audioStrategy: IAudioStrategy) {
        mActualDecodedFormat = actualFormat
        mInputSampleRate = actualFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val encodingSampleRate = presetFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (mInputSampleRate != encodingSampleRate) {
            // throw UnsupportedOperationException("Audio sample rate conversion not supported yet.")
            logger.info("Sample Rate: input=$mInputSampleRate, output=$encodingSampleRate")
            logger.info("Audio sample rate conversion not supported yet.")
        }
        // 実際の入力チャネル数
        mInputChannelCount = actualFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        // 出力チャネル数： 実際の入力チャネル数＋AudioStrategy によって出力チャネル数を決定する
        mOutputChannelCount = audioStrategy.resolveOutputChannelCount(actualFormat)
        if (mInputChannelCount != 1 && mInputChannelCount != 2) {
            throw UnsupportedOperationException("Input channel count ($mInputChannelCount) not supported.")
        }
        if (mOutputChannelCount != 1 && mOutputChannelCount != 2) {
            throw UnsupportedOperationException("Output channel count ($mOutputChannelCount) not supported.")
        }
        mRemixer = if (mInputChannelCount > mOutputChannelCount) {
            logger.debug("down mix")
            AudioRemixer.DOWNMIX
        } else if (mInputChannelCount < mOutputChannelCount) {
            logger.debug("up mix")
            AudioRemixer.UPMIX
        } else {
            logger.debug("pass through")
            AudioRemixer.PASSTHROUGH
        }
        mOverflowBuffer.presentationTimeUs = 0
    }

    fun drainDecoderBufferAndQueue(decoder:MediaCodec, bufferIndex: Int, presentationTimeUs: Long) {
        if (!::mActualDecodedFormat.isInitialized) {
            throw RuntimeException("Buffer received before format!")
        }
        val data: ByteBuffer? = if (bufferIndex == BUFFER_INDEX_END_OF_STREAM) {
            logger.debug("detect EOS (push to filledBuffers")
            inputEos = true
            null
        } else decoder.getOutputBuffer(bufferIndex)
        val buffer = mEmptyBuffers.poll() ?: AudioBuffer()

        buffer.bufferIndex = bufferIndex
        buffer.presentationTimeUs = presentationTimeUs
        buffer.data = data?.asShortBuffer()

        if (mOverflowBuffer.data == null && data!=null) {
            mOverflowBuffer.data = ByteBuffer
                .allocateDirect(data.capacity())
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    clear().flip()
                }
        }
        mFilledBuffers.add(buffer)
    }

    /**
     * 可能な限りデータをエンコーダーに書き込む
     */
    fun feedEncoder(decoder:MediaCodec, encoder: MediaCodec): Boolean {
        var result = false
        while(feedEncoderSub(decoder, encoder)) {
            result = true
        }
        return result
    }

    /**
     * デコーダー (MediaCodec)の outputバッファ、または、内部バッファ（mFilledBuffers）の１回分のデータを
     * エンコーダー (MediaCodec)の inputバッファに書き込む。
     * @return  true: データを書き込んだ
     *          false: 書き込まなかった（入力が空、または、出力バッファが busy）
     */
    private fun feedEncoderSub(decoder:MediaCodec, encoder: MediaCodec): Boolean {
        val hasOverflow = mOverflowBuffer.data?.hasRemaining() == true

        if (mFilledBuffers.isEmpty() && !hasOverflow) { // No audio data - Bail out
            logger.verbose("no audio data -- bail out")
            return false
        }
        val encoderInBuffIndex = encoder.dequeueInputBuffer(0 /*immediate*/)
        if (encoderInBuffIndex < 0) { // Encoder is full - Bail out
            logger.verbose { "encoder is full -- bail out" }
            return false
        }

        if (hasOverflow) {
            logger.verbose {"found over flow data"}
            // Drain overflow first
            val outBuffer: ShortBuffer? = encoder.getInputBuffer(encoderInBuffIndex)?.asShortBuffer()
            if(outBuffer==null) {
                logger.info("no output (encoder) buffer for overflow data.")
                return false
            }

            val presentationTimeUs = drainOverflow(outBuffer)
            encoder.queueInputBuffer(encoderInBuffIndex, 0, outBuffer.position() * BYTES_PER_SHORT, presentationTimeUs, 0)
            return true
        }
        val inBuffer = mFilledBuffers.poll()
        if(inBuffer==null) {
            logger.debug("filledBuffers queue is empty.")
            return false
        }
        if (inBuffer.bufferIndex == BUFFER_INDEX_END_OF_STREAM) {
            logger.debug("detect EOS (enqueue in encoder).")
            encoder.queueInputBuffer(encoderInBuffIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        val outBuffer: ShortBuffer? = encoder.getInputBuffer(encoderInBuffIndex)?.asShortBuffer()
        if(outBuffer==null) {
            logger.info("no output (encoder) buffer.")
            return false
        }

        val presentationTimeUs = remixAndMaybeFillOverflow(inBuffer, outBuffer)
        encoder.queueInputBuffer(encoderInBuffIndex, 0, outBuffer.position() * BYTES_PER_SHORT, presentationTimeUs, 0)
        decoder.releaseOutputBuffer(inBuffer.bufferIndex, false)
        mEmptyBuffers.add(inBuffer)
        return true
    }

    private fun sampleCountToDurationUs(sampleCount: Int, sampleRate: Int, channelCount: Int): Long {
        return sampleCount / (sampleRate * MICROSECS_PER_SEC) / channelCount
    }

    private fun drainOverflow(outBuff: ShortBuffer): Long {
        val overflowBuff = mOverflowBuffer.data ?: return 0L
        val overflowLimit = overflowBuff.limit()
        val overflowSize = overflowBuff.remaining()
        val beginPresentationTimeUs = mOverflowBuffer.presentationTimeUs + sampleCountToDurationUs(overflowBuff.position(), mInputSampleRate, mOutputChannelCount)
        outBuff.clear() // Limit overflowBuff to outBuff's capacity
        overflowBuff.limit(outBuff.capacity()) // Load overflowBuff onto outBuff
        outBuff.put(overflowBuff)
        if (overflowSize >= outBuff.capacity()) { // Overflow fully consumed - Reset
            overflowBuff.clear().limit(0)
        } else { // Only partially consumed - Keep position & restore previous limit
            overflowBuff.limit(overflowLimit)
        }
        return beginPresentationTimeUs
    }

    private fun remixAndMaybeFillOverflow(input: AudioBuffer, outBuff: ShortBuffer): Long {
        val inBuff = input.data ?: return 0L
        val overflowBuff = mOverflowBuffer.data
        outBuff.clear()

        // Reset position to 0, and set limit to capacity (Since MediaCodec doesn't do that for us)
        inBuff.clear()
        if (inBuff.remaining() > outBuff.remaining()) { // Overflow
            logger.verbose { "remix with overflow data: in=${inBuff.remaining()}, out=${outBuff.remaining()} out-cap=${outBuff.capacity()}" }
            // Limit inBuff to outBuff's capacity
            inBuff.limit(outBuff.capacity())
            mRemixer.remix(inBuff, outBuff)

            // Reset limit to its own capacity & Keep position
            inBuff.limit(inBuff.capacity())

            // Remix the rest onto overflowBuffer
            // NOTE: We should only reach this point when overflow buffer is empty
            val consumedDurationUs = sampleCountToDurationUs(inBuff.position(), mInputSampleRate, mInputChannelCount)
            if(overflowBuff!=null) {
                overflowBuff.clear()
                mRemixer.remix(inBuff, overflowBuff)

                // Seal off overflowBuff & mark limit
                overflowBuff.flip()
            }
            mOverflowBuffer.presentationTimeUs = input.presentationTimeUs + consumedDurationUs
        } else { // No overflow
//            logger.verbose("no overflow")
            mRemixer.remix(inBuff, outBuff)
        }
        return input.presentationTimeUs
    }

}