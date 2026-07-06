package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.processor.contract.ITrack
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.types.RangeUs
import kotlin.math.min

/**
 * 無音を生成してエンコードする ITrack 実装（結合(concat)用）。
 *
 * 音声トラックを持たないソースが、音声ありソースと混在して結合される場合に、
 * 当該ソースの区間を無音で埋めて A/V 同期を維持するために使用する。
 * （コンテナ上の音声サンプルの空白は再生互換性を下げるため、実際に無音をエンコードして書き込む）
 *
 * 無音の生成量:
 * - ソースの長さ(durationUs)が既知の場合: range の実効長（actualEnd - start）分を生成する。
 * - 不明な場合: 映像トラック(videoTrack)の進行に追従し、映像が done になった時点まで生成する。
 *
 * @param strategy      音声ストラテジー（エンコーダー生成・ビットレート決定に使用）
 * @param sampleRate    出力サンプルレート（UnifiedOutputFormatで決定された値）
 * @param channelCount  出力チャネル数 1 or 2（同上）
 * @param videoTrack    同一ソースの映像トラック（ソース長不明時の進行基準）
 * @param durationUs    ソースの再生時間 (us)。不明なら null。
 */
class SilentAudioTrack(
    private val strategy: IAudioStrategy,
    private val sampleRate: Int,
    private val channelCount: Int,
    private val videoTrack: ITrack,
    private val durationUs: Long?,
    private val report: Report,
) : ITrack {
    companion object {
        const val SAMPLES_PER_FRAME = 1024                  // AAC 1フレームのサンプル数（チャネルあたり）
        private const val BYTES_PER_SHORT = 2
        private const val TIMEOUT_DRAIN_US = 10_000L        // EOS送信後の encoder drain 待ち時間
    }
    private val logger = UtLog("T(S)", Processor.logger, this::class.java)

    init {
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(channelCount == 1 || channelCount == 2) { "channelCount must be 1 or 2: $channelCount" }
    }

    override val isAvailable: Boolean = true
    override var presentationTimeUs: Long = 0L
        private set
    override var currentRangeStartPresentationTimeUs: Long = 0L
        private set

    private lateinit var muxer: SyncMuxer
    private var mEncoder: MediaCodec? = null
    private val mBufferInfo = MediaCodec.BufferInfo()
    private val mSilence = ByteArray(SAMPLES_PER_FRAME * channelCount * BYTES_PER_SHORT)  // ゼロ埋めPCM (1フレーム分)

    private var mRangeStartInputUs = 0L         // range開始位置（入力時間軸）
    private var mEmittedSampleFrames = 0L       // このrangeで生成したPCMフレーム数（チャネルあたりサンプル数）
    private var inputDone = false
    private var eosEncoder = false
    private var noResponse = false

    override val done: Boolean get() = noResponse || (inputDone && eosEncoder)

    override fun setup(muxer: SyncMuxer) {
        this.muxer = muxer
    }

    override fun setBasePresentationTimeUs(baseUs: Long) {
        logger.info("base presentation time = $baseUs us")
        presentationTimeUs = baseUs
        currentRangeStartPresentationTimeUs = baseUs
    }

    override fun startRange(startFromUS: Long): Long {
        logger.assertStrongly(mEncoder == null, "already opened")
        inputDone = false
        eosEncoder = false
        noResponse = false
        mEmittedSampleFrames = 0L
        mRangeStartInputUs = startFromUS
        currentRangeStartPresentationTimeUs = presentationTimeUs

        val format = MediaFormat.createAudioFormat(strategy.codec.mime, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, strategy.profile.value)
            setInteger(MediaFormat.KEY_BIT_RATE, strategy.bitRatePerChannel.value(null) * channelCount)
        }
        mEncoder = strategy.createEncoder().apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
            report.updateAudioEncoder(this)
        }
        return startFromUS
    }

    override fun endRange() {
        mEncoder?.apply {
            stop()
            release()
        }
        mEncoder = null
    }

    override fun readAndWrite(rangeUs: RangeUs): Boolean {
        var effected = feedSilence(rangeUs)
        if (drainEncoder()) {
            effected = true
        }
        if (!effected && !done) {
            noResponse = true
        }
        return effected
    }

    /** このrangeで生成済みの無音の長さ (us) ... 累積サンプル数から算出（丸め誤差が蓄積しない） */
    private fun emittedDurationUs(): Long {
        return mEmittedSampleFrames * 1_000_000L / sampleRate
    }

    /**
     * 無音PCMを1フレーム分エンコーダーに書き込む。目標量に達したら EOS を書き込む。
     */
    private fun feedSilence(rangeUs: RangeUs): Boolean {
        if (inputDone) return false
        val encoder = mEncoder ?: return false

        // 目標量に達したか？
        val endUs = rangeUs.actualEndUs(durationUs)
        val targetReached = if (endUs == Long.MAX_VALUE) {
            // ソースの長さが不明: 映像トラックの進行に追従する
            videoTrack.done && presentationTimeUs >= videoTrack.presentationTimeUs
        } else {
            emittedDurationUs() >= endUs - mRangeStartInputUs
        }

        val idx = encoder.dequeueInputBuffer(0L /*immediate*/)
        if (idx < 0) {
            // エンコーダーがバッファフル ... drainで進捗するので progress ありとして扱う
            return true
        }
        if (targetReached) {
            logger.debug("silence: reached the end of the range. sending EOS.")
            encoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
            return true
        }
        val buffer = encoder.getInputBuffer(idx)
        if (buffer == null) {
            logger.error("no input buffer for silence data.")
            encoder.queueInputBuffer(idx, 0, 0, 0, 0)
            return true
        }
        // フレーム境界(2byte×チャネル数)にアラインして書き込む
        val frameBytes = channelCount * BYTES_PER_SHORT
        val bytes = (min(mSilence.size, buffer.capacity()) / frameBytes) * frameBytes
        buffer.clear()
        buffer.put(mSilence, 0, bytes)
        val pts = currentRangeStartPresentationTimeUs + emittedDurationUs()
        encoder.queueInputBuffer(idx, 0, bytes, pts, 0)
        mEmittedSampleFrames += bytes / frameBytes
        presentationTimeUs = pts
        return true
    }

    /**
     * エンコーダーの出力をMuxerに書き込む。
     */
    private fun drainEncoder(): Boolean {
        val encoder = mEncoder ?: return false
        var effected = false
        while (!eosEncoder) {
            // EOS送信後は、エンコーダーの遅延で出力が空に見えることがあるため、少し待つ
            val index = encoder.dequeueOutputBuffer(mBufferInfo, if (inputDone) TIMEOUT_DRAIN_US else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    return effected
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    effected = true
                    logger.debug("silence: encoder output format changed.")
                    muxer.setOutputFormat(false, encoder.outputFormat)
                }
                index >= 0 -> {
                    effected = true
                    if (mBufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        logger.debug("silence: found end of stream.")
                        eosEncoder = true
                        encoder.releaseOutputBuffer(index, false)
                        return true
                    }
                    if (mBufferInfo.flags.and(MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encoder.releaseOutputBuffer(index, false)
                        continue
                    }
                    muxer.writeSampleData(false, encoder.getOutputBuffer(index)!!, mBufferInfo)
                    encoder.releaseOutputBuffer(index, false)
                }
                else -> {}
            }
        }
        return effected
    }

    override fun finalize() {
        report.audioExtractedDurationUs = presentationTimeUs
    }

    override fun close() {
        endRange()
    }
}
