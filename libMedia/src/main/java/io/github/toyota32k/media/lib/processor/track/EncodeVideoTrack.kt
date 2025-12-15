package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.mime
import io.github.toyota32k.media.lib.processor.contract.IBufferSource
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.internals.surface.InputSurface
import io.github.toyota32k.media.lib.internals.surface.OutputSurface
import io.github.toyota32k.media.lib.internals.surface.RenderOption

class EncodeVideoTrack(inPath:IInputMediaFile, inputMetaData: MetaData, maxDurationUs:Long, bufferSource: IBufferSource, report: Report, val strategy: IVideoStrategy, val renderOption: RenderOption)
    : AbstractEncodeTrack(inPath, inputMetaData, maxDurationUs, bufferSource, report, video=true) {
    private var mOutputSurface:OutputSurface? = null    // for Decoder （in/outがややこしいので注意... decoder/encoderの気持ちになって in/out）
    private var mInputSurface:InputSurface? = null      // for Encoder

    override fun startRange(startFromUS: Long): Long {
        if (!isAvailable) return 0L

        logger.assertStrongly(mOutputSurface == null, "already opened")

        // ハマりポイントｗｗｗ
        // 必ず、Encoder-->Decoder の順に初期化＆開始する。そうしないと、Decoder側の inputSurfaceの初期化に失敗する。
        mEncoder = strategy.createEncoder().apply {
            outputTrackMediaFormat = strategy.createOutputFormat(inputTrackMediaFormat, inputMetaData, this, renderOption)
            configure(outputTrackMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mInputSurface = InputSurface(this.createInputSurface()).apply { makeCurrent() }
            start()
            report.updateVideoEncoder(this)
        }

        mOutputSurface = OutputSurface(renderOption).also { outputSurface ->
            mDecoder = MediaCodec.createDecoderByType(inputTrackMediaFormat.mime ?: extractor.getTrackFormat(trackIndex).mime!!)
                .apply {
                    configure(inputTrackMediaFormat, outputSurface.surface, null, 0)
                    start()
                    report.updateVideoDecoder(this)
                }
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
        mOutputSurface?.release()
        mOutputSurface = null
        mInputSurface?.release()
        mInputSurface = null
    }

    override fun onDecoderOutputFormatChanged(formatFromDecoder: MediaFormat) {
        logger.verbose()
    }

    override fun transformAndTransfer(index: Int, bufferInfo: MediaCodec.BufferInfo, eos: Boolean) {
        val decoder = mDecoder ?: throw IllegalStateException("decoder is already closed.")
        val outputSurface = mOutputSurface ?: throw IllegalStateException("outputSurface is already closed.")
        val inputSurface = mInputSurface ?: throw IllegalStateException("inputSurface is already closed.")

        if (eos) {
            logger.debug("found eos")
            eosDecoder = true
        }
        val render = bufferInfo.size>0
        decoder.releaseOutputBuffer(index, render)
        if (render) {
            outputSurface.awaitNewImage()
            outputSurface.drawImage()
            inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
            inputSurface.swapBuffers()
        }
    }
    override fun finalize() {
        super.finalize()
        report.videoExtractedDurationUs = presentationTimeUs
    }
}