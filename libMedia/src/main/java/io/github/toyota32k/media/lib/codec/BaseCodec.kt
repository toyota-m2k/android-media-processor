package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.utils.UtLog
import java.io.Closeable

abstract class BaseCodec(val mediaFormat:MediaFormat) : Closeable {
    companion object {
        const val TIMEOUT_IMMEDIATE:Long = 0    // -1: infinite / 0:immediate / >0 milliseconds
//        const val TIMEOUT_INFINITE:Long = -1L    // -1: infinite / 0:immediate / >0 milliseconds
//        const val TIMEOUT_1SEC:Long = 1000L
    }
    abstract val sampleType: Muxer.SampleType
    abstract val name:String
    lateinit var logger:UtLog
    var eos:Boolean = false
        protected set

    protected abstract val mediaCodec:MediaCodec

    protected val bufferInfo = MediaCodec.BufferInfo()

    protected open fun configure() {
        mediaCodec.configure(mediaFormat, null, null, 0)
    }

    fun start() {
        logger = UtLog(name, null, "io.github.toyota32k.")
        configure()
        mediaCodec.start()
    }

    protected var disposed:Boolean = false
        private set

    override fun close() {
        if(!disposed) {
            mediaCodec.release()
            disposed = true
            logger.debug("disposed")
        }
    }
}