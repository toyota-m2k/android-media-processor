package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.net.Uri
import io.github.toyota32k.media.lib.format.DefaultAudioStrategy
import io.github.toyota32k.media.lib.format.HD720VideoStrategy
import io.github.toyota32k.media.lib.format.IAudioStrategy
import io.github.toyota32k.media.lib.format.IVideoStrategy
import io.github.toyota32k.media.lib.misc.RingBuffer
import io.github.toyota32k.media.lib.track.*
import io.github.toyota32k.media.lib.utils.UtLog
import kotlinx.coroutines.*
import java.io.File
import java.lang.IllegalStateException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * 動画ファイルのトランスコード/トリミングを行うコンバータークラス
 */
class Converter(private val inPath:AndroidFile, private val outPath: AndroidFile, private val videoStrategy:IVideoStrategy=HD720VideoStrategy, private val audioStrategy:IAudioStrategy=DefaultAudioStrategy, private val trimmingRange: TrimmingRange?=null) {
    companion object {
        val logger = UtLog("Converter", null, "io.github.toyota32k.")
        val factory
            get() = Factory()
    }

    // region - Result

    var deleteOutputOnError:Boolean = true

    // endregion - Result

    // region - Progress

//    private val progress: Progress
    var onProgress : ((IProgress)->Unit)? = null

    // endregion

    /**
     * Converterのファクトリクラス
     */
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    class Factory {
        companion object {
            val logger = UtLog("Factory", Converter.logger, Converter.logger.omissionNamespace)
        }
        private var inPath: AndroidFile? = null
        private var outPath: AndroidFile? = null
        private var videoStrategy:IVideoStrategy = HD720VideoStrategy
        private var audioStrategy:IAudioStrategy = DefaultAudioStrategy
        private var trimStart:Long = 0L
        private var trimEnd:Long = 0L
        private var deleteOutputOnError = true
        private var onProgress:((IProgress)->Unit)? = null

        fun input(path: File): Factory {
            inPath = AndroidFile(path)
            return this
        }

        fun input(uri: Uri, context: Context): Factory {
            inPath = AndroidFile(uri, context)
            return this
        }

        fun output(path: File): Factory {
            outPath = AndroidFile(path)
            return this
        }

        fun output(uri: Uri, context: Context): Factory {
            outPath = AndroidFile(uri, context)
            return this
        }

        fun videoStrategy(s:IVideoStrategy):Factory {
            videoStrategy = s
            return this
        }
        fun audioStrategy(s:IAudioStrategy):Factory {
            audioStrategy = s
            return this
        }

        fun trimmingStartFrom(timeMs:Long):Factory {
            trimStart = timeMs*1000L
            return this
        }

        fun trimmingEndTo(timeMs:Long):Factory {
            trimEnd = timeMs*1000L
            return this
        }

        fun setProgressHandler(proc:(IProgress)->Unit):Factory {
            onProgress = proc
            return this
        }

        fun deleteOutputOnError(flag:Boolean):Factory {
            this.deleteOutputOnError = flag
            return this
        }

        fun build():Converter {
            val input = inPath ?: throw IllegalStateException("input file is not specified.")
            val output = outPath ?: throw IllegalStateException("output file is not specified.")

            logger.info("### media converter information ###")
            logger.info("input : $input")
            logger.info("output: $output")
            logger.info("video strategy: ${videoStrategy.javaClass.name}")
            logger.info("audio strategy: ${audioStrategy.javaClass.name}")

            val trimmingRange = if(trimStart>0||trimEnd>0) {
                logger.info("trimming start: ${trimStart / 1000} ms")
                logger.info("trimming end  : ${trimEnd / 1000} ms")
                TrimmingRange(trimStart,trimEnd)
            } else {
                TrimmingRange.Empty
            }

            logger.info("delete output on error = $deleteOutputOnError")
            if(onProgress==null) {
                logger.info("no progress handler")
            }

            return Converter(input, output, videoStrategy, audioStrategy, trimmingRange).also { cv->
                cv.deleteOutputOnError = deleteOutputOnError
                cv.onProgress = onProgress
            }
        }

        suspend fun execute() : ConvertResult {
            return build().execute()
        }

        fun executeAsync(coroutineScope: CoroutineScope?=null):IAwaiter<ConvertResult> {
            return build().executeAsync(coroutineScope)
        }
    }

    /**
     * Awaiter
     * IAwaiter(キャンセル可能な待ち合わせi/f)の実装クラス
     */
    private data class Awaiter(private val deferred:Deferred<ConvertResult>): IAwaiter<ConvertResult> {

        override fun cancel() {
            deferred.cancel()
        }

        override suspend fun await():ConvertResult {
            return try {
                deferred.await()
            } catch(e:CancellationException) {
                return ConvertResult.cancelled
            }
        }
    }

    /**
     * IProgress(進捗報告i/f)の実装クラス
     */
    private class Progress(val durationUs:Long, val trimmingRange: TrimmingRange, val onProgress:(IProgress)->Unit): IProgress {
        companion object {
            const val ENTRY_COUNT = 10
            fun create(durationUs: Long, trimmingRange: TrimmingRange, onProgress:((IProgress)->Unit)?):Progress? {
                return if(onProgress!=null) Progress(durationUs, trimmingRange, onProgress) else null
            }
        }
        private data class DealtEntry(val position:Long, val tick:Long)
        val statistics = RingBuffer<DealtEntry>(ENTRY_COUNT)
        var audioProgressInUs: Long = 0L
            set(v) {
                if (field < v) {
                    field = v
                    progressInUs = min(videoProgressInUs, v)
                }
            }
        var videoProgressInUs: Long = 0L
            set(v) {
                if (field < v) {
                    field = v
                    progressInUs = min(audioProgressInUs, v)
                }
            }
        fun finish() {
            if(percentage !=100) {
                percentage = 100
                notifyProgress()
            }
        }

        private var busy = AtomicBoolean(false)
        fun notifyProgress() {
            if(busy.get()) return
            CoroutineScope(Dispatchers.Main).launch {
                busy.set(true)
                onProgress(this@Progress)
                busy.set(false)
            }
        }

        var progressInUs: Long = 0L
            set(v) {
                if (field < v) {
                    field = v
                    val pos = v - trimmingRange.startUs
                    val dur = total
                    if(dur>0) {
                        val p = max(0, min(100, (pos * 100L / total).toInt()))
                        if (percentage <p) {
                            percentage = p
                            statistics.put(DealtEntry(pos, System.currentTimeMillis()))
                            val head = statistics.head
                            val tail = statistics.tail
                            val time = tail.tick - head.tick
                            if(time>2000) {
                                val velocity = (tail.position - head.position) / time  // us / ms
                                if (velocity > 0) {
                                    remainingTime = (dur - pos) / velocity
                                }
                            }
                            notifyProgress()
                        }
                    } else {
                        notifyProgress()
                    }
                }
            }

        override var remainingTime: Long = 0
            private set
        override var percentage: Int = 0
            private set
        override val total: Long
            get() = when {
                    trimmingRange.hasEnd-> trimmingRange.endUs - trimmingRange.startUs
                    trimmingRange.hasStart-> durationUs-trimmingRange.startUs
                    else->durationUs
                }
        override val current: Long
            get() = progressInUs - trimmingRange.startUs
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
    fun executeAsync(coroutineScope: CoroutineScope?=null):IAwaiter<ConvertResult> {
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
    suspend fun execute():ConvertResult {
        return withContext(Dispatchers.Default) {
            try {
                AudioTrack.create(inPath, audioStrategy).use { audioTrack->
                VideoTrack.create(inPath, videoStrategy).use { videoTrack->
                Muxer(inPath, outPath, audioTrack!=null).use { muxer->
                    val tr = trimmingRange ?: TrimmingRange.Empty
                    val progress = Progress.create(muxer.durationUs, tr, onProgress)
                    videoTrack.trimmingRange = tr
                    audioTrack?.trimmingRange = tr
                    fun eos():Boolean = videoTrack.eos && audioTrack?.eos?:true
                    var tick = -1L
                    var count = 0
                    while (!eos()) {
                        if(!isActive) {
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
                            if(progress!=null) {
                                progress.videoProgressInUs = videoTrack.encoder.writtenPresentationTimeUs
                                progress.audioProgressInUs = audioTrack?.encoder?.writtenPresentationTimeUs ?: progress.videoProgressInUs
                            }
                        }
                    }
                    progress?.finish()
                    ConvertResult.succeeded
                }}}
            }
            catch(e:Throwable) {
                logger.stackTrace(e)
                if(deleteOutputOnError) {
                    try {
                        outPath.delete()
                    } catch(ef:Throwable) {
                        logger.stackTrace(ef,"cannot delete output file: $outPath")
                    }
                }
                ConvertResult.error(e)
            }
        }
    }
}