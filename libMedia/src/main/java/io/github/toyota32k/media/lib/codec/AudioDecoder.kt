package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.audio.AudioChannel
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.utils.Chronos

class AudioDecoder(format: MediaFormat):BaseDecoder(format) {
    private val audioChannel = AudioChannel()
    override val sampleType = Muxer.SampleType.Audio

    override fun chainTo(encoder: BaseEncoder) :Boolean {
        return chainTo({ decodedFormat -> audioChannel.setActualDecodedFormat(decodedFormat, mediaFormat) }) { index, length, end ->
            if (end) {
                eos = true
                audioChannel.drainDecoderBufferAndQueue(decoder, AudioChannel.BUFFER_INDEX_END_OF_STREAM, 0)
            } else if (length > 0) {
                audioChannel.drainDecoderBufferAndQueue(decoder, index, bufferInfo.presentationTimeUs)
            }
            audioChannel.feedEncoder(decoder, encoder.encoder, 0)
        }
    }
}