package io.github.toyota32k.media.lib.track

import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.BaseDecoder
import io.github.toyota32k.media.lib.codec.BaseEncoder
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.misc.check
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import java.io.Closeable

abstract class Track(val extractor:Extractor, type:Muxer.SampleType/*, val inputFormat:MediaFormat?, val outputFormat:MediaFormat, val trackIdx:Int, type:Muxer.SampleType, report:Report*/) : Closeable {
    val logger: UtLog
    init {
        logger = UtLog("Track($type)", Converter.logger)
    }
    companion object {
    }
    abstract val decoder: BaseDecoder
    abstract val encoder: BaseEncoder

//    var trimmingRangeList: ITrimmingRangeList
//        get() = extractor.trimmingRangeList
//        set(v) {
//            extractor.trimmingRangeList = v
////            decoder.trimmingRangeList = v
//        }


    val eos
        get() = extractor.eos && decoder.eos && encoder.eos
    val convertedLength:Long
        get() = encoder.writtenPresentationTimeUs

    fun next(muxer: Muxer, coroutineScope: CoroutineScope?) : Boolean {
        var effected = false
        coroutineScope.check()
        if (extractor.chainTo(decoder)) {
            effected = true
        }
        coroutineScope.check()
        if (decoder.chainTo(encoder)) {
            effected = true
        }
        coroutineScope.check()
        if (encoder.chainTo(muxer)) {
            effected = true
        }
//        logger.debug("$effected")
        return effected
    }

    private var disposed = false
    override fun close() {
        if(!disposed) {
            disposed = true
            encoder.close()
            decoder.close()
            extractor.close()
        }
    }
}
