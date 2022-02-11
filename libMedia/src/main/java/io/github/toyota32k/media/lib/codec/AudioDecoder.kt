package io.github.toyota32k.media.lib.codec

import android.media.MediaFormat
import io.github.toyota32k.media.lib.audio.AudioChannel
import io.github.toyota32k.media.lib.track.Muxer

class AudioDecoder(format: MediaFormat):BaseDecoder(format) {
    private val audioChannel = AudioChannel()
    override val sampleType = Muxer.SampleType.Audio

    override fun chainTo(encoder: BaseEncoder) :Boolean {
        return chainTo( { decodedFormat -> audioChannel.setActualDecodedFormat(decodedFormat, mediaFormat) }) inner@ { index, length, end, timeUs ->
            if (length > 0 && trimmingRange.contains(timeUs)) {
                audioChannel.drainDecoderBufferAndQueue(decoder, index, bufferInfo.presentationTimeUs)
                audioChannel.feedEncoder(decoder, encoder.encoder, 0)
            }
            if (end) {
                audioChannel.drainDecoderBufferAndQueue(decoder, AudioChannel.BUFFER_INDEX_END_OF_STREAM, 0)
                audioChannel.feedEncoder(decoder, encoder.encoder, 0)
            }
        }
    }
}