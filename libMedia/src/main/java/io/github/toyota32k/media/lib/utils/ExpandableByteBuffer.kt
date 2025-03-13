package io.github.toyota32k.media.lib.utils

import java.nio.ByteBuffer

class ExpandableByteBuffer(val initialSize: Int =0, expandSize:Int=1024, private val creator: ((capacity: Int) -> ByteBuffer) = { ByteBuffer.allocate(it) }) {
    private var mBuffer: ByteBuffer? = null
    private val expand = expandSize.coerceAtLeast(1024)

    private fun allocateSize(size: Int):Int {
        return ((size+expand*2-1) / expand) * expand
    }

    fun alloc(incrementCapacity: Int): ByteBuffer {
        val capacity = (mBuffer?.capacity() ?: 0) + incrementCapacity
        return mBuffer.let { curBuffer ->
            if (curBuffer == null) {
                creator(allocateSize(capacity.coerceAtLeast(initialSize))).apply { mBuffer = this }
            } else if (curBuffer.capacity()<capacity) {
                creator(allocateSize(capacity)).also { newBuffer ->
                    curBuffer.flip()
                    newBuffer.put(curBuffer)
                    mBuffer = newBuffer
                }
            } else {
                curBuffer
            }
        }
    }

    val hasBuffer get() = mBuffer != null
    
    val buffer: ByteBuffer
        get() = mBuffer ?: throw IllegalStateException("buffer is not allocated")
    val bufferOrNull get() = mBuffer
    
    fun free() {
        mBuffer = null
    }
}