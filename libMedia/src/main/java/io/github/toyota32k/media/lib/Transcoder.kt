package io.github.toyota32k.media.lib

import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.format.DefaultAudioStrategy
import io.github.toyota32k.media.lib.format.HD720VideoStrategy
import io.github.toyota32k.media.lib.format.IAudioStrategy
import io.github.toyota32k.media.lib.format.IVideoStrategy
import io.github.toyota32k.media.lib.misc.MediaFile
import io.github.toyota32k.media.lib.track.AudioTrack
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.track.Track
import io.github.toyota32k.media.lib.track.VideoTrack
import io.github.toyota32k.media.lib.utils.Chronos
import io.github.toyota32k.media.lib.utils.UtLog
import java.io.Closeable
import java.lang.UnsupportedOperationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class Transcoder(inPath:MediaFile, outPath: MediaFile, videoStrategy:IVideoStrategy=HD720VideoStrategy, audioStrategy:IAudioStrategy=DefaultAudioStrategy) : Closeable {
    companion object {
        val logger = UtLog("Transcoder", null, "io.github.toyota32k.")
    }

    val extractor: Extractor
    val audioTrack: AudioTrack?
    val videoTrack: VideoTrack
    val muxer: Muxer

    init {
        extractor = Extractor(inPath)
        audioTrack = AudioTrack.create(inPath, audioStrategy)
        videoTrack = VideoTrack.create(inPath, videoStrategy) ?: throw UnsupportedOperationException("no video track")
        muxer = Muxer(inPath, outPath, audioTrack!=null)
    }

    fun trackOf(idx: Int): Track? {
        return when (idx) {
            audioTrack?.trackIdx -> audioTrack
            videoTrack.trackIdx -> videoTrack
            else -> null
        }
    }

//    var eos: Boolean = false
//    fun nextSample(): Int {
//        val idx = extractor.nextSample()
//        logger.debug("next sample: trackIndex = $idx")
//        if (idx < 0) {
//            logger.debug("found eos")
//            eos = true
//        }
//        return idx
//    }

    val eos:Boolean
        get() = videoTrack.eos && audioTrack?.eos?:true

    fun convert() {
        Chronos(logger).measure {
            var tick = -1L
            var count:Int = 0
            while (!eos) {
                val ve = videoTrack.next(muxer)
                val ae = audioTrack?.next(muxer) ?: false
                if(!ve&&!ae) {
                    count++
                    if(tick<0) {
                        tick = System.currentTimeMillis()
                    } else if(System.currentTimeMillis()-tick>5000 && count>100) {
                        break
                    }
                } else {
                    tick = -1
                    count=0
                }
            }
        }
    }

    private var disposed = false
    override fun close() {
        if (!disposed) {
            disposed = true
            extractor.close()
            audioTrack?.close()
            videoTrack.close()
            muxer.close()
        }
    }
}