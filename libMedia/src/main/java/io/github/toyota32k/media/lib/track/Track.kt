package io.github.toyota32k.media.lib.track

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.codec.BaseDecoder
import io.github.toyota32k.media.lib.codec.BaseEncoder
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.misc.ICancellation
import java.io.Closeable

abstract class Track(val extractor:Extractor, type:Muxer.SampleType, cancellation: ICancellation) : Closeable, ICancellation by cancellation {
    val logger: UtLog = UtLog("Track($type)", Converter.logger)

    abstract val decoder: BaseDecoder
    abstract val encoder: BaseEncoder

    fun chain(muxer: Muxer) {
        extractor
            .chain(decoder)
            .chain(encoder)
            .chain(muxer)
    }

    val eos
        get() = extractor.eos && decoder.eos && encoder.eos
    val convertedLength:Long
        get() = encoder.writtenPresentationTimeUs

    fun consume() : Boolean {
        return extractor.consume()
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
