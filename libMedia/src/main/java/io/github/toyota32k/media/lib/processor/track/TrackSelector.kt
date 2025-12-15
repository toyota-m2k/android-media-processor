package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
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

    fun openVideoTrack(renderOption: RenderOption): ITrack {
        return when (videoStrategy) {
            is PresetVideoStrategies.InvalidStrategy -> {
                if (renderOption!=RenderOption.DEFAULT) {
                    throw IllegalArgumentException("renderOption is specified with InvalidStrategy")
                }
                NoReEncodeTrack(inFile, inputMetaData, limitDurationUs, bufferSource = this, report, video=true)
            }
            else -> EncodeVideoTrack(inFile, inputMetaData, limitDurationUs, bufferSource = this, report, videoStrategy, renderOption)
        }
    }
    fun openAudioTrack(): ITrack {
        return when (audioStrategy) {
            is PresetAudioStrategies.InvalidStrategy -> return NoReEncodeTrack(inFile, inputMetaData, limitDurationUs, bufferSource = this, report, video=false)
            is PresetAudioStrategies.NoAudio -> return EmptyTrack
            else -> EncodeAudioTrack(inFile, inputMetaData, limitDurationUs, bufferSource = this, report, audioStrategy)
        }
    }

    override fun close() {
        mBuffer.reset()
        mBufferInfo.reset()
    }

}