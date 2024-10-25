package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import java.nio.ByteBuffer

/**
 * 入力バッファ
 * データを書き込むバッファ
 */
class CodecInputBuffer private constructor(val codec:MediaCodec, val index:Int) : AutoCloseable {
    constructor(codec:MediaCodec, timeoutUs:Long) : this(codec, codec.dequeueInputBuffer(timeoutUs))

    private var offset: Int = 0
    private var length: Int = 0
    private var presentationTimeUS: Long = 0L
    private var flags: Int = 0
    var closed: Boolean = false
        private set

    val buffer: ByteBuffer? by lazy { if(index>=0&&!closed) codec.getInputBuffer(index) else null }
    val tryLater: Boolean get() = index == MediaCodec.INFO_TRY_AGAIN_LATER

    override fun close() {
        if(!closed && index>=0) {
            codec.queueInputBuffer(index,offset,length,presentationTimeUS,flags)
        }
        closed = true
    }

    fun close(offset:Int, length:Int, presentationTimeUS:Long, flags:Int) {
        this.offset = offset
        this.length = length
        this.presentationTimeUS = presentationTimeUS
        this.flags = flags
        close()
    }


    fun writeBuffer(fn:(ByteBuffer)->Unit) {
        buffer?.apply {
            fn(this)
        }
    }
}
