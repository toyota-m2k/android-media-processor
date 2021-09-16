package io.github.toyota32k.media.lib.converter

import io.github.toyota32k.media.lib.format.DefaultAudioStrategy
import io.github.toyota32k.media.lib.format.HD720VideoStrategy
import io.github.toyota32k.media.lib.format.IAudioStrategy
import io.github.toyota32k.media.lib.format.IVideoStrategy
import io.github.toyota32k.media.lib.misc.AndroidFile
import io.github.toyota32k.media.lib.misc.TrimmingRange
import io.github.toyota32k.media.lib.track.*
import io.github.toyota32k.media.lib.utils.UtLog
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.concurrent.TimeoutException

class Converter(val inPath:AndroidFile, val outPath: AndroidFile, val videoStrategy:IVideoStrategy=HD720VideoStrategy, val audioStrategy:IAudioStrategy=DefaultAudioStrategy, val trimmingRange:TrimmingRange?=null) {
    companion object {
        val logger = UtLog("Converter", null, "io.github.toyota32k.")
        val factory
            get() = Factory()
    }

//    private val audioTrack: AudioTrack?
//    private val videoTrack: VideoTrack
//    private val muxer: Muxer

    // region - Result

    lateinit var result:Result
    var deleteOutputOnError:Boolean = true

    // endregion - Result

    // region - Cancel
    // コンバート中にキャンセルする方法は２つ
    // - cancel()を呼ぶ
    // - あらかじめ、canceller をセットしておいて、そのisCancelled をtrueにする
    private var cancelling:Boolean = false
    fun cancel() {
        cancelling = true
    }
    private val isCancelled:Boolean
        get() = cancelling

    // endregion - Cancel

    // region - Progress

//    private val progress: Progress
    var onProgress : ((IProgress)->Unit)? = null

    // endregion

//    init {
//        audioTrack = AudioTrack.create(inPath, audioStrategy)
//        videoTrack = VideoTrack.create(inPath, videoStrategy) ?: throw UnsupportedOperationException("no video track")
//        muxer = Muxer(inPath, outPath, audioTrack!=null)
//        progress = Progress(muxer)
//    }

//    fun trackOf(idx: Int): Track? {
//        return when (idx) {
//            audioTrack?.trackIdx -> audioTrack
//            videoTrack.trackIdx -> videoTrack
//            else -> null
//        }
//    }

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

//    val eos:Boolean
//        get() = videoTrack.eos && audioTrack?.eos?:true

//    fun transcode() {
//        Chronos(logger).measure {
//            var tick = -1L
//            var count:Int = 0
//            while (!eos) {
//                val ve = videoTrack.next(muxer)
//                val ae = audioTrack?.next(muxer) ?: false
//                if(!ve&&!ae) {
//                    count++
//                    if(tick<0) {
//                        tick = System.currentTimeMillis()
//                    } else if(System.currentTimeMillis()-tick>5000 && count>100) {
//                        break
//                    }
//                } else {
//                    tick = -1
//                    count=0
//                }
//            }
//        }
//    }
    data class Awaiter(private val defered:Deferred<Result>): IAwaiter<Result> {

        override fun cancel() {
            defered.cancel()
        }

        override suspend fun await():Result {
            return try {
                defered.await()
            } catch(e:CancellationException) {
                return Result.cancelled
            }
        }
    }

    /**
     * val converter = Converter(...)
     * val awaiter = converter.executeAsync(viewModelScope)
     * viewModelScope.launch {
     *   val result = awaiter.await()
     *   when {
     *      result.succeeded -> // コンバート成功
     *      result.cancelled -> // キャンセルされた
     *      else -> showErrorMessage(result.errorMessage)
     *   }
     * }
     * ...
     * コンバートをキャンセルするときは、awaiter.cancel()を呼び出す。
     * 尚、親のスコープ(↑の例では、viewModelScope)がキャンセルされると、execute()のスコープもキャンセルされるので、
     * この場合は、awaitから結果を受け取る前にコルーチンから抜けてしまい、resultは受け取らない。
     */
    fun executeAsync(coroutineScope: CoroutineScope?=null):IAwaiter<Result> {
        val cs = coroutineScope ?: CoroutineScope(Dispatchers.Default)
        return Awaiter(cs.async {
            execute()
        })
    }

    /**
     * コンバートを実行
     * 呼び出し元のCoroutineContext (JobやDeferred)をキャンセルすると処理は中止されるが、
     * withContextもキャンセルされてしまうので、リターンしないで終了する（= Result.cancelを返せない。）
     * キャンセルの結果をハンドリングしたい（Result.cancelを受け取りたい）時は、executeAsyncを使う。
     *
     * val converter = Converter(...)
     * val job = viewModelScope.launch {
     *   val result = converter.execute()
     *   when {
     *      result.succeeded -> // コンバート成功
     *      result.cancelled -> // ここには入らない
     *      else -> showErrorMessage(result.errorMessage)
     *   }
     * }
     * ...
     * コンバートをキャンセルするときは、job.cancel()を呼び出すが、その場合、execute()がキャンセルされた時点で、
     * このjob自体が終了してしまうため、resultは受け取れない。
     */
    suspend fun execute():Result {
        return withContext(Dispatchers.Default) {
            try {
                AudioTrack.create(inPath, audioStrategy).use { audioTrack->
                VideoTrack.create(inPath, videoStrategy).use { videoTrack->
                Muxer(inPath, outPath, audioTrack!=null).use { muxer->
                    val progress = Progress(muxer, onProgress)
                    val tr = trimmingRange ?: TrimmingRange.Empty
                    videoTrack.trimmingRange = tr
                    audioTrack?.trimmingRange = tr
                    fun eos():Boolean = videoTrack.eos && audioTrack?.eos?:true
                    var tick = -1L
                    var count: Int = 0
                    while (!eos()) {
                        if(!isActive || isCancelled) {
                            throw CancellationException("cancelled")
                        }
                        val ve = videoTrack.next(muxer, this)
                        val ae = audioTrack?.next(muxer, this) ?: false
                        if (!ve && !ae) {
                            count++
                            if (tick < 0) {
                                tick = System.currentTimeMillis()
                            } else if (System.currentTimeMillis() - tick > 5000 && count > 100) {
                                throw TimeoutException("no response from transcoder.")
                            }
                        } else {
                            tick = -1
                            count = 0
                            progress.videoProgressInUs = videoTrack.encoder.writtenPresentationTimeUs
                            progress.audioProgressInUs = audioTrack?.encoder?.writtenPresentationTimeUs ?: progress.videoProgressInUs
                        }
                    }
                    progress.finish()
                    Result.succeeded
                }}}
            }
            catch(e:Throwable) {
                logger.stackTrace(e)
                if(deleteOutputOnError) {
                    try {
                        outPath.delete()
                    } catch(e:Throwable) {
                        logger.stackTrace(e,"cannot delete output file: $outPath")
                    }
                }
                Result.error(e)
            }
        }
    }

//    fun execute():Result {
//        result = Chronos(logger).measure {
//            try {
//                videoTrack.trimmingRange = trimmingRange
//                audioTrack?.trimmingRange = trimmingRange
//                var tick = -1L
//                var count: Int = 0
//                while (!eos && !isCancelled) {
//                    val ve = videoTrack.next(muxer)
//                    val ae = audioTrack?.next(muxer) ?: false
//                    if (!ve && !ae) {
//                        count++
//                        if (tick < 0) {
//                            tick = System.currentTimeMillis()
//                        } else if (System.currentTimeMillis() - tick > 5000 && count > 100) {
//                            throw TimeoutException("no response from transcoder.")
//                        }
//                    } else {
//                        tick = -1
//                        count = 0
//                    }
//                }
//                if(eos) { Result.succeeded } else { Result.cancelled }
//            } catch(e:Throwable) {
//                Result.error(e)
//            }
//        }
//        return result
//    }


//    private var disposed = false
//    override fun close() {
//        if (!disposed) {
//            disposed = true
//            audioTrack?.close()
//            videoTrack.close()
//            muxer.close()
//            if(!result.succeeded&&deleteOutputOnError) {
//                try {
//                    outPath.delete()
//                } catch(e:Throwable) {
//                    logger.stackTrace(e,"cannot delete output file: $outPath")
//                }
//            }
//        }
//    }
}