package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.BaseCodec
import io.github.toyota32k.media.lib.codec.BaseCodec.Companion.TIMEOUT_IMMEDIATE
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.dump
import io.github.toyota32k.media.lib.processor.contract.IBufferSource
import io.github.toyota32k.media.lib.processor.contract.format3digits
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.types.RangeUs
import io.github.toyota32k.media.lib.types.RangeUs.Companion.formatAsUs
import java.nio.ByteBuffer

abstract class AbstractEncodeTrack(inPath:IInputMediaFile, inputMetaData: MetaData, maxDurationUs:Long, bufferSource: IBufferSource, report: Report, video:Boolean): AbstractBaseTrack(inPath, inputMetaData, maxDurationUs, bufferSource, report, video) {
    protected var mDecoder: MediaCodec? = null
    protected var mEncoder: MediaCodec? = null

    override lateinit var outputTrackMediaFormat: MediaFormat

    protected var eosExtractor:Boolean = false
    protected var eosDecoder:Boolean = false
    protected var eosEncoder:Boolean = false
    protected var noResponse: Boolean = false

    protected var writtenPresentationTimeUs:Long = 0
    protected var lastRangeEndPresentationTimeUs: Long = 0L
    protected var currentRangeStartTimeUs: Long = 0L

    override val done: Boolean get() = !isAvailable || noResponse || (eosExtractor && eosDecoder && eosEncoder)

    override fun startRange(startFromUS: Long): Long {
        noResponse = false
        eosExtractor = false
        eosDecoder = false
        eosEncoder = false
        lastRangeEndPresentationTimeUs = writtenPresentationTimeUs
        return super.startRange(startFromUS).apply {
            currentRangeStartTimeUs = this
        }
    }

    /**
     * Extractorからデータを読み込んで、Decoder（の inputBuffer）に書き出す。
     * Video/Audio 共通処理
     */
    protected fun extractorToDecoder(rangeUs: RangeUs):Boolean {
        if (eosExtractor) return false
        var extracted = false
        val startTime = extractor.sampleTime
        val decoder = mDecoder ?: throw IllegalStateException("decoder is already closed.")
        val endUs = rangeUs.actualEndUs(inputMetaData.durationUs)
        val maxDuration = if (maxDurationUs<=0 ) Long.MAX_VALUE else maxDurationUs
        if (startTime == -1L || endUs <= startTime || presentationTimeUs >= maxDuration) {
            // 実際のEOSに達したか、要求範囲のEndに達した.
            // BUFFER_FLAG_END_OF_STREAM を書き込む
            logger.info("Extractor: reached end of the range: ${startTime.formatAsUs()}")
            eosExtractor = true
        } else {
            val inputBufferIdx = decoder.dequeueInputBuffer(BaseCodec.TIMEOUT_IMMEDIATE)
            if (inputBufferIdx < 0) {
                // デコーダーがバッファフル ... デコーダーの処理に進む
                logger.verbose("Extractor: no buffer in the decoder.")
                extracted = true
            } else {
                //  Decoderからバッファが取得できたら、Encoderからバッファーに読みだす
                val inputBuffer = decoder.getInputBuffer(inputBufferIdx) as ByteBuffer
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize > 0) {
                    logger.verbose { "Extractor: read ${sampleSize.format3digits()} bytes at ${presentationTimeUs.formatAsUs()}" }
                    presentationTimeUs = lastRangeEndPresentationTimeUs + extractor.sampleTime - currentRangeStartTimeUs
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    decoder.queueInputBuffer(inputBufferIdx, 0, sampleSize, presentationTimeUs, extractor.sampleFlags)
                    extractor.advance()
//                    logger.debug("after : trackIndex=${findTrackIdx(video=true)} extractor=${extractor.sampleTrackIndex} size=${extractor.sampleSize} time=${extractor.sampleTime}")
                    extracted = true
                } else {
                    // 本来は、↑の idx < 0 でチェックしているので、ここには入らないはず。
                    // もし入ってきたら、dequeue した inputBufferだけは解放しておく。
                    logger.error("Extractor: zero byte read. it may be eos.")
                    decoder.queueInputBuffer(inputBufferIdx, 0, 0, 0, 0)
                }
            }
        }
        return extracted
    }

    /**
     * Decoderから、MediaCodec.INFO_OUTPUT_FORMAT_CHANGED を受け取った時の処理
     * in/outがややこしいので注意
     * Decoderからみた Output Format  は、入力ファイル ファイルのフォーマットを意味する。
     */
    abstract fun onDecoderOutputFormatChanged(formatFromDecoder: MediaFormat)

    /**
     * デコーダーの出力データを処理して、エンコーダーの入力につなげる。
     *
     * @param index バッファーのインデックス
     * @param bufferInfo バッファ情報
     * @param eos デコーダーがEOSに達したときに true / それ以外は false
     */
    abstract fun transformAndTransfer(index:Int, bufferInfo: MediaCodec.BufferInfo, eos:Boolean)

    /**
     * Decoder（のoutputSurface）からEncoder（の inputSurface）にデータを書き出す。
     */
    protected fun decoderToEncoder(bufferInfo: MediaCodec.BufferInfo):Boolean {
        val decoder = mDecoder ?: throw IllegalStateException("decoder is already closed.")
        var effected: Boolean = false
        while (!eosDecoder) {
            val index = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
            when {
                index >= 0 -> {
                    logger.verbose { "Decoder: output:$index size=${bufferInfo.size}" }
                    val eos = bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    transformAndTransfer(index, bufferInfo,eos)
                    effected = true
                    break
                }

                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    logger.verbose { "Decoder: no sufficient data in decoder yet" }
                    break
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    logger.debug("Decoder: output format changed")
                    val decoderOutputFormat = decoder.outputFormat
                    report.updateInputSummary(decoderOutputFormat,null)
                    decoderOutputFormat.dump(logger, "decoder outputFormat changed")
                    onDecoderOutputFormatChanged(decoderOutputFormat)
                    effected = true
                }

                else -> {
                    if (index == -3) {
                        logger.debug("Decoder: BUFFERS_CHANGED ... ignorable.")
                    } else {
                        logger.error("Decoder: unknown index ($index)")
                    }
                }
            }
        }
        return effected
    }


    protected open fun encoderToMuxer(bufferInfo: MediaCodec.BufferInfo): Boolean {
        var effected = false
        val encoder = mEncoder ?: throw IllegalStateException("encoder is already closed.")
        while (!eosEncoder) {
            val index: Int = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    logger.verbose { "Encoder: no sufficient data in encoder yet." }
                    return effected
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    logger.debug("Encoder: input format changed.")
                    effected = true
                    val actualFormat = encoder.outputFormat
                    outputTrackMediaFormat = actualFormat
                    muxer.setOutputFormat(video, actualFormat)
                    actualFormat.dump(logger, "Encoder: outputFormat changed")
                }
                index >= 0 -> {
                    effected = true
                    if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        logger.debug("Encoder: found end of stream.")
                        eosEncoder = true
                        // muxer.stop() で終端されるので、これは不要（むしろ不正な動画ファイルができる恐れあり）らしい。by Copilot
                        // bufferInfo.set(0, 0, 0, bufferInfo.flags)
                        encoder.releaseOutputBuffer(index, false)
                        return true
                    }
                    else if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) { // SPS or PPS, which should be passed by MediaFormat.
                        logger.debug("Encoder: codec config.")
                        encoder.releaseOutputBuffer(index, false)
                        continue
                    }
                    logger.verbose {"Encoder: output:$index size=${bufferInfo.size} time=${bufferInfo.presentationTimeUs.formatAsUs()}"}
                    muxer.writeSampleData(video, encoder.getOutputBuffer(index)!!, bufferInfo)
                    if(bufferInfo.presentationTimeUs>0) {
                        writtenPresentationTimeUs = bufferInfo.presentationTimeUs
                    }
                    encoder.releaseOutputBuffer(index, false)

                    // TextureRender#drawImage (GLES20.glClear) でハングする不具合を回避する
                    // デコーダーがInputSurfaceに描画したあと、エンコーダーが読み出す前（OutputSurfaceに転送される前）に、
                    // 次のdrawImage()が呼ばれると、読み出し（or転送）処理と描画処理がコンフリクトして、デッドロックするのではないか、と推測。
                    // そこで、Encoderでは、データが取り出せる限り取り出して muxerに書き込む、という処理に変更してみた。
                    // 変更後何度も試してみたが、これでハングしなくなったように見えている。
                }
                else -> {}
            }
        }
        return effected
    }

    override fun readAndWrite(rangeUs: RangeUs): Boolean {
        if (!isAvailable) return false   // トラックが存在しない場合は常にEOSとして扱う

        // Phase-1 ... EncoderからDecoderにデータを読みだす
        var effected = extractorToDecoder(rangeUs)
        do {
            // Phase-2 ... DecoderからEncoderにデータを書き出す
            val decoded = decoderToEncoder(bufferInfo)
            // Phase-3 ... EncoderからMuxerにデータを書き出す
            val encoded = encoderToMuxer(bufferInfo)
            if (decoded || encoded) {
                effected = true
            }
        } while (decoded || encoded)
        if (!effected) {
            noResponse = true
        }
        return effected
    }

}