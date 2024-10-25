package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import java.nio.ByteBuffer


class CodecOutputBuffer private constructor(val codec:MediaCodec, val index:Int, val bufferInfo:MediaCodec.BufferInfo) : AutoCloseable {
    constructor(codec:MediaCodec,  timeoutUs:Long, bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()) : this(codec, codec.dequeueOutputBuffer(bufferInfo,timeoutUs), bufferInfo)

    var render:Boolean = false  // surface は使わないことにしたので、常に false
    var closed: Boolean = false
        private set
    val tryLater: Boolean get() = index == MediaCodec.INFO_TRY_AGAIN_LATER
    val formatChanged: Boolean get() = index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
    val buffer: ByteBuffer? by lazy { if(index>=0&&!closed) codec.getOutputBuffer(index) else null }

    val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    val codecConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
    val length = bufferInfo.size
    val offset = bufferInfo.offset
    val presentationTimeUs = bufferInfo.presentationTimeUs

    override fun close() {
        if(!closed && index>=0) {
            codec.releaseOutputBuffer(index, render)
        }
        closed = true
    }

    fun withBuffer(fn:(ByteBuffer)->Unit) {
        buffer?.apply {
            fn(this)
        }
    }
}
