package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.processor.contract.IBufferSource
import io.github.toyota32k.media.lib.processor.contract.ITrack
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.internals.surface.RenderOption
import io.github.toyota32k.utils.UtLazyResetableValue
import java.io.Closeable
import java.nio.ByteBuffer

class TrackSelector(private val inFile: IInputMediaFile, val limitDurationUs:Long, val report: Report, bufferSize:Int, private val videoStrategy: IVideoStrategy, private val audioStrategy: IAudioStrategy)
    : IBufferSource, Closeable {
    val inputMetaData = MetaData.fromFile(inFile)

    // region Buffer

    // 作業用バッファ
    // 個々に確保するのは非経済的なので、クラスとして確保する。
    // 必要に応じて確保し、不要になったら解放できるよう UtLazyResetableValue を利用
    private val mBuffer = UtLazyResetableValue<ByteBuffer> { ByteBuffer.allocateDirect(bufferSize) }
    override val buffer: ByteBuffer
        get() = mBuffer.value
    private val mBufferInfo = UtLazyResetableValue<MediaCodec.BufferInfo> { MediaCodec.BufferInfo() }
    override val bufferInfo: MediaCodec.BufferInfo
        get() = mBufferInfo.value

    // endregion

    /**
     * @param fixedOutputFormat 出力フォーマットを固定する場合に指定（結合(concat)用）。nullなら入力から導出（従来動作）。
     */
    fun openVideoTrack(renderOption: RenderOption, fixedOutputFormat: MediaFormat? = null): ITrack {
        return when (videoStrategy) {
            is PresetVideoStrategies.InvalidStrategy -> {
                if (renderOption!=RenderOption.DEFAULT) {
                    throw IllegalArgumentException("renderOption is specified with InvalidStrategy")
                }
                if (fixedOutputFormat!=null) {
                    throw IllegalArgumentException("fixedOutputFormat is specified with InvalidStrategy")
                }
                NoReEncodeTrack(inFile, inputMetaData, limitDurationUs, bufferSource = this, report, video=true)
            }
            else -> EncodeVideoTrack(inFile, inputMetaData, limitDurationUs, bufferSource = this, report, videoStrategy, renderOption, fixedOutputFormat)
        }
    }
    /**
     * @param fixedSampleRate   出力サンプルレートを固定する場合に指定（結合(concat)用）。nullなら入力から決定（従来動作）。
     * @param fixedChannelCount 出力チャネル数を固定する場合に指定（結合(concat)用）。nullなら入力から決定（従来動作）。
     */
    fun openAudioTrack(fixedSampleRate: Int? = null, fixedChannelCount: Int? = null): ITrack {
        return when (audioStrategy) {
            is PresetAudioStrategies.InvalidStrategy -> {
                if (fixedSampleRate != null || fixedChannelCount != null) {
                    throw IllegalArgumentException("fixedSampleRate/fixedChannelCount is specified with InvalidStrategy")
                }
                NoReEncodeTrack(inFile, inputMetaData, limitDurationUs, bufferSource = this, report, video=false)
            }
            is PresetAudioStrategies.NoAudio -> EmptyTrack
            else -> EncodeAudioTrack(inFile, inputMetaData, limitDurationUs, bufferSource = this, report, audioStrategy, fixedSampleRate, fixedChannelCount)
        }
    }

    override fun close() {
        mBuffer.reset()
        mBufferInfo.reset()
    }

}