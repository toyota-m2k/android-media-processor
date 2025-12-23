package io.github.toyota32k.media.lib.processor.contract

import android.media.MediaCodec
import java.nio.ByteBuffer

interface IBufferSource {
    val buffer: ByteBuffer
    val bufferInfo: MediaCodec.BufferInfo
}