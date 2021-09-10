package io.github.toyota32k.media.lib.track

import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.BaseDecoder
import io.github.toyota32k.media.lib.codec.BaseEncoder
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.utils.Chronos
import io.github.toyota32k.media.lib.utils.UtLog
import java.io.Closeable

abstract class Track(val extractor:Extractor, val inputFormat:MediaFormat?, val outputFormat:MediaFormat, val trackIdx:Int) : Closeable {
    init {
        extractor.selectTrack(trackIdx)
    }
    companion object {
        val logger = UtLog("Track")
        fun findTrackIdx(extractor: MediaExtractor, type: String): Int {
            for (idx in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(idx)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith(type) == true) {
                    return idx
                }
            }
            return -1
        }

        fun getMediaFormat(extractor: MediaExtractor, idx:Int): MediaFormat {
            return extractor.getTrackFormat(idx)
        }
    }
    abstract val decoder: BaseDecoder
    abstract val encoder: BaseEncoder

    val eos
        get() = extractor.eos && decoder.eos && encoder.eos

    fun next(muxer: Muxer) : Boolean {
        return Chronos(logger).measure {
            var effected = false
            if (extractor.chainTo(decoder)==true) {
                effected = true
            }
            if (decoder.chainTo(encoder)) {
                effected = true
            }
            if (encoder.chainTo(muxer)) {
                effected = true
            }
            effected
        }
    }

    private var disposed = false
    override fun close() {
        if(!disposed) {
            disposed = true
            encoder.close()
            decoder.close()
        }
    }
}
