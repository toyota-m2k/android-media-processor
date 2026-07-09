package io.github.toyota32k.media.lib.internals.audio

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.format.bitRate
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.ArrayDeque
import java.util.Queue
import kotlin.math.min

/**
 * デコーダー出力（PCM）をチャネル変換（remix）・サンプルレート変換（resample）してエンコーダーに供給するクラス。
 *
 * @param fixedOutputSampleRate   出力サンプルレートを固定する場合に指定（結合(concat)用）。
 *                                null なら入力と同じサンプルレートで出力（従来動作）。
 *                                入力と異なるレートが指定された場合はリサンプラーを挿入する。
 * @param fixedOutputChannelCount 出力チャネル数を固定する場合に指定（結合(concat)用）。
 *                                null なら AudioStrategy から決定（従来動作）。
 */
class AudioChannel(
    private val fixedOutputSampleRate: Int? = null,
    private val fixedOutputChannelCount: Int? = null,
) {
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

    private lateinit var mRemixer: AudioRemixer

    // リサンプリング（サンプルレート変換）
    private var mResampler: IAudioResampler? = null
    private var mRemixScratchBuffer: ShortBuffer? = null    // remix（チャネル変換）結果の作業バッファ
    private var mResampleScratchBuffer: ShortBuffer? = null // resample 結果の作業バッファ

    private val mOverflowBuffer = AudioBuffer()

    private lateinit var mActualDecodedFormat: MediaFormat

    // for encoder
    var outputChannelCount: Int = 0
        private set
    var outputSampleRate: Int = 0
        private set
    var outputBitRate: Int = 0
        private set

    fun setActualDecodedFormat(actualFormat: MediaFormat, presetFormat: MediaFormat, audioStrategy: IAudioStrategy) {
        mActualDecodedFormat = actualFormat
        mInputSampleRate = actualFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        // 出力サンプルレート:
        // - fixedOutputSampleRate 指定時（結合(concat)用）: その値に固定し、必要ならリサンプラーを挿入する
        // - 未指定時: 入力と同じサンプルレートで出力（従来動作）
        outputSampleRate = fixedOutputSampleRate ?: mInputSampleRate
        // 実際の入力チャネル数
        mInputChannelCount = actualFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        // 出力チャネル数： fixedOutputChannelCount 指定時はその値、未指定なら入力チャネル数＋AudioStrategy によって決定する
        outputChannelCount = fixedOutputChannelCount ?: audioStrategy.resolveOutputChannelCount(actualFormat)
        if (mInputChannelCount != 1 && mInputChannelCount != 2) {
            throw UnsupportedOperationException("Input channel count ($mInputChannelCount) not supported.")
        }
        if (outputChannelCount != 1 && outputChannelCount != 2) {
            throw UnsupportedOperationException("Output channel count ($outputChannelCount) not supported.")
        }
        outputBitRate = audioStrategy.resolveOutputSampleRate(actualFormat, mInputChannelCount, outputChannelCount)
        logger.debug("bitrate: ${actualFormat.bitRate?:0} --> $outputBitRate")
        mRemixer = if (mInputChannelCount > outputChannelCount) {
            logger.debug("down mix")
            AudioRemixer.DOWNMIX
        } else if (mInputChannelCount < outputChannelCount) {
            logger.debug("up mix")
            AudioRemixer.UPMIX
        } else {
            logger.debug("pass through")
            AudioRemixer.PASSTHROUGH
        }
        mResampler = if (outputSampleRate != mInputSampleRate) {
            logger.info("resample: $mInputSampleRate Hz --> $outputSampleRate Hz")
            LinearResampler(mInputSampleRate, outputSampleRate, outputChannelCount)
        } else null
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
            // 作業バッファの確保
            // - remix(チャネル変換)の最悪ケース(UPMIX)で入力の2倍
            // - resample(レート変換)がある場合は、さらにレート比で増加
            val inputShorts = data.capacity() / BYTES_PER_SHORT
            val remixedShorts = inputShorts * 2
            val resampledShorts = mResampler?.estimateOutputSampleCount(remixedShorts) ?: remixedShorts
            mOverflowBuffer.data = allocateShortBuffer(resampledShorts).apply {
                clear().flip()
            }
            if (mResampler != null) {
                mRemixScratchBuffer = allocateShortBuffer(remixedShorts)
                mResampleScratchBuffer = allocateShortBuffer(resampledShorts)
            }
        }
        mFilledBuffers.add(buffer)
    }

    private fun allocateShortBuffer(sizeInShorts: Int): ShortBuffer {
        return ByteBuffer
            .allocateDirect(sizeInShorts * BYTES_PER_SHORT)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
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
        // 旧実装 sampleCount / (sampleRate * MICROSECS_PER_SEC) / channelCount は
        // 整数除算により常にほぼ0を返すバグがあったため修正。
        if (sampleRate <= 0 || channelCount <= 0) return 0L
        return sampleCount.toLong() * MICROSECS_PER_SEC / sampleRate / channelCount
    }

    private fun drainOverflow(outBuff: ShortBuffer): Long {
        val overflowBuff = mOverflowBuffer.data ?: return 0L
        val overflowLimit = overflowBuff.limit()
        // overflowバッファの内容は出力フォーマット(出力レート・出力チャネル数)のPCM
        val beginPresentationTimeUs = mOverflowBuffer.presentationTimeUs + sampleCountToDurationUs(overflowBuff.position(), outputSampleRate, outputChannelCount)
        outBuff.clear()
        // outBuff の容量に収まる分だけコピーする。
        // 旧実装は overflowBuff.limit(outBuff.capacity()) としていたが、これは
        // 残量が outBuff.capacity() と一致しない場合に limit を引き上げて
        // 未初期化領域をコピーしてしまうため修正。
        val copyCount = min(overflowBuff.remaining(), outBuff.capacity())
        overflowBuff.limit(overflowBuff.position() + copyCount)
        outBuff.put(overflowBuff)
        overflowBuff.limit(overflowLimit)
        if (!overflowBuff.hasRemaining()) { // Overflow fully consumed - Reset
            overflowBuff.clear().limit(0)
        }
        return beginPresentationTimeUs
    }

    private fun remixAndMaybeFillOverflow(input: AudioBuffer, outBuff: ShortBuffer): Long {
        val resampler = mResampler
        return if (resampler != null) {
            remixResampleAndMaybeFillOverflow(input, outBuff, resampler)
        } else {
            remixDirectAndMaybeFillOverflow(input, outBuff)
        }
    }

    /**
     * リサンプリングあり:
     * decoder出力 → remix(チャネル変換) → resample(レート変換) → encoder入力バッファ
     * encoder入力バッファに入り切らない分は overflow バッファに退避する。
     *
     * サンプルレート変換は再生時間を変えないため、PTSは入力バッファの値をそのまま使用できる。
     */
    private fun remixResampleAndMaybeFillOverflow(input: AudioBuffer, outBuff: ShortBuffer, resampler: IAudioResampler): Long {
        val inBuff = input.data ?: return 0L
        val remixBuff = mRemixScratchBuffer ?: return 0L
        val resampledBuff = mResampleScratchBuffer ?: return 0L
        outBuff.clear()

        // Reset position to 0, and set limit to capacity (Since MediaCodec doesn't do that for us)
        inBuff.clear()

        // 1. remix (チャネル変換) → 作業バッファ（入力全量が必ず収まるサイズを確保済み）
        remixBuff.clear()
        mRemixer.remix(inBuff, remixBuff)
        remixBuff.flip()

        // 2. resample (レート変換) → 作業バッファ（同上）
        resampledBuff.clear()
        resampler.resample(remixBuff, resampledBuff)
        resampledBuff.flip()

        // 3. encoder入力バッファへ書き込み。入り切らない分は overflow へ。
        if (resampledBuff.remaining() > outBuff.remaining()) {
            val writeCount = outBuff.remaining()
            val savedLimit = resampledBuff.limit()
            resampledBuff.limit(resampledBuff.position() + writeCount)
            outBuff.put(resampledBuff)
            resampledBuff.limit(savedLimit)

            // NOTE: We should only reach this point when overflow buffer is empty
            val overflowBuff = mOverflowBuffer.data
            if (overflowBuff != null) {
                overflowBuff.clear()
                overflowBuff.put(resampledBuff)
                overflowBuff.flip()
            }
            mOverflowBuffer.presentationTimeUs =
                input.presentationTimeUs + sampleCountToDurationUs(writeCount, outputSampleRate, outputChannelCount)
        } else {
            outBuff.put(resampledBuff)
        }
        return input.presentationTimeUs
    }

    /**
     * リサンプリングなし（従来動作）:
     * decoder出力 → remix(チャネル変換) → encoder入力バッファ
     */
    private fun remixDirectAndMaybeFillOverflow(input: AudioBuffer, outBuff: ShortBuffer): Long {
        val inBuff = input.data ?: return 0L
        val overflowBuff = mOverflowBuffer.data
        outBuff.clear()

        // Reset position to 0, and set limit to capacity (Since MediaCodec doesn't do that for us)
        inBuff.clear()
        if (mRemixer.checkOverflow(inBuff, outBuff)) { // Overflow
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