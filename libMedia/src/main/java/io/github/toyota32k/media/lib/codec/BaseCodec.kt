package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.utils.UtLog
import io.github.toyota32k.media.lib.utils.UtLoggerInstance
import java.io.Closeable

abstract class BaseCodec(val mediaFormat:MediaFormat) : Closeable {
    companion object {
//        val logger = UtLog("Codec", null, "io.github.toyota32k.")
        const val TIMEOUT_INFINITE:Long = -1L    // -1: infinite / 0:immediate / >0 milliseconds
        const val TIMEOUT_IMMEDIATE:Long = 0    // -1: infinite / 0:immediate / >0 milliseconds
        const val TIMEOUT_1SEC:Long = 1000L
    }
    abstract val sampleType: Muxer.SampleType
    abstract val name:String
    lateinit var logger:UtLog
    var eos:Boolean = false
        protected set

    abstract protected val mediaCodec:MediaCodec

    protected val bufferInfo = MediaCodec.BufferInfo()

    open protected fun configure() {
        mediaCodec.configure(mediaFormat, null, null, 0)
    }

    fun start() {
        logger = UtLog(name, null, "io.github.toyota32k.")
        configure()
        mediaCodec.start()
    }

    protected var disposed:Boolean = false
        private set
    override open fun close() {
        if(!disposed) {
            mediaCodec.release()
            disposed = true
        }
    }
}