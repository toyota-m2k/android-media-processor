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
import java.util.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * 動画ファイルのトランスコード/トリミングを行うコンバータークラス
 */
class Converter {
    companion object {
        val logger = UtLog("Converter", null, "io.github.toyota32k.")
        val factory
            get() = Factory()
    }

    lateinit var inPath:AndroidFile
    lateinit var outPath: AndroidFile

    var videoStrategy:IVideoStrategy=HD720VideoStrategy
    var audioStrategy:IAudioStrategy=DefaultAudioStrategy
    var trimmingRange = TrimmingRange.Empty
    var deleteOutputOnError:Boolean = true
    var onProgress : ((IProgress)->Unit)? = null

    /**
     * Converterのファクトリクラス
     */
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    class Factory {
        companion object {
            val logger = UtLog("Factory", Converter.logger, Converter.logger.omissionNamespace)
        }
        private val converter = Converter()
        private var trimStart:Long = 0L
        private var trimEnd:Long = 0L

        fun input(path: File): Factory {
            converter.inPath = AndroidFile(path)
            return this
        }

        fun input(uri: Uri, context: Context): Factory {
            converter.inPath = AndroidFile(uri, context)
            return this
        }

        fun input(src:AndroidFile): Factory {
            converter.inPath = src
            return this
        }

        fun output(path: File): Factory {
            converter.outPath = AndroidFile(path)
            return this
        }

        fun output(uri: Uri, context: Context): Factory {
            converter.outPath = AndroidFile(uri, context)
            return this
        }

        fun output(dst:AndroidFile):Factory {
            converter.outPath = dst
            return this
        }

        fun videoStrategy(s:IVideoStrategy):Factory {
            converter.videoStrategy = s
            return this
        }
        fun audioStrategy(s:IAudioStrategy):Factory {
            converter.audioStrategy = s
            return this
        }

        fun trimmingStartFrom(timeMs:Long):Factory {
            if(timeMs>0) {
                trimStart = timeMs * 1000L
            }
            return this
        }

        fun trimmingEndTo(timeMs:Long):Factory {
            if(timeMs>0) {
                trimEnd = timeMs * 1000L
            }
            return this
        }

        fun setProgressHandler(proc:(IProgress)->Unit):Factory {
            converter.onProgress = proc
            return this
        }

        fun deleteOutputOnError(flag:Boolean):Factory {
            converter.deleteOutputOnError = flag
            return this
        }

        fun build():Converter {
            if(!converter::inPath.isInitialized) throw IllegalStateException("input file is not specified.")
            if(!converter::outPath.isInitialized) throw IllegalStateException("output file is not specified.")

            logger.info("### media converter information ###")
            logger.info("input : $converter.input")
            logger.info("output: $converter.output")
            logger.info("video strategy: ${converter.videoStrategy.javaClass.name}")
            logger.info("audio strategy: ${converter.audioStrategy.javaClass.name}")

            if(trimStart>0||trimEnd>0) {
                logger.info("trimming start: ${trimStart / 1000} ms")
                logger.info("trimming end  : ${trimEnd / 1000} ms")
                converter.trimmingRange = TrimmingRange(trimStart,trimEnd)
            }

            logger.info("delete output on error = $converter.deleteOutputOnError")
            if(converter.onProgress==null) {
                logger.info("no progress handler")
            }
            return converter
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
        val cs = coroutineScope ?: CoroutineScope(Dispatchers.IO)
        return Awaiter(cs.async {
            execute()
        })
    }

    /**
     * より進捗の悪いトラックを優先的に処理することで、できるだけ均等に処理を進めてみる。
     *
     * 長い動画で、ビデオトラックとオーディオトラックの進捗度合いが大きく乖離すると、
     *  - Extractor: decoder.dequeueInputBuffer(TIMEOUT_IMMEDIATE)
     *  - Encoder: encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_IMMEDIATE)
     * が永遠に、-1 を返し始めて、処理が完了しない現象に遭遇。
     * ここからは、想像なのだけど、ビデオとオーディオの両方が揃うまで muxer が処理できず、バッファリングし続けているうちに、
     * バッファがいっぱいになって、トラックの読み込みや、変換結果の書き込み、変換結果の読みだし、のどこかで処理が止まってしまうのではありますまいか？
     * そこで、audio/videoトラックを均等に処理していくようにしてみたら、現象が回避された模様。
     */
    private fun CoroutineScope.next(muxer:Muxer, videoTrack:VideoTrack, audioTrack:AudioTrack?):Boolean {
        // フォーマットが確定するまでは、両方を平行して進める
        if(!muxer.isReady) {
            // フォーマットが確定するまでは、入力がすべてバッファリングされるだけで、outputへの書き込みが発生しないため、
            // Track.convertedLength がゼロのままになるので、下のロジックだと、Video側だけが読み込まれてバッファーがオーバーフローする。
            val rv = if(!muxer.isVideoReady) {
                if(videoTrack.eos) {
                    throw IllegalStateException("unexpected eos in video track.")
                }
                videoTrack.next(muxer, this)
            } else false
            val ra = if(audioTrack!=null && !muxer.isAudioReady) {
                if(audioTrack.eos) {
                    throw IllegalStateException("unexpected eos in audio track.")
                }
                audioTrack.next(muxer, this)
            } else false
            return rv || ra
        }

        // video/audio両方のフォーマットが確定したら（isReadyになったら）、進捗（convertedLength）が同程度になるように調整する。
        return if(!videoTrack.eos && (audioTrack==null || audioTrack.eos || videoTrack.convertedLength<=audioTrack.convertedLength)) {
            videoTrack.next(muxer, this)
        } else if (audioTrack!=null && !audioTrack.eos) {
            audioTrack.next(muxer, this)
        } else {
            logger.assert(videoTrack.eos && audioTrack?.eos ?: true)
            false
        }
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
        return withContext(Dispatchers.IO) {
            try {
                AudioTrack.create(inPath, audioStrategy).use { audioTrack->
                VideoTrack.create(inPath, videoStrategy).use { videoTrack->
                Muxer(inPath, outPath, audioTrack!=null).use { muxer->
                    val progress = Progress.create(muxer.durationUs, trimmingRange, onProgress)
                    videoTrack.trimmingRange = trimmingRange
                    audioTrack?.trimmingRange = trimmingRange
                    fun eos():Boolean = videoTrack.eos && audioTrack?.eos?:true
                    var tick = -1L
                    var count = 0
                    while (!eos()) {
                        if(!isActive) {
                            throw CancellationException("cancelled")
                        }

//                        val ve = videoTrack.next(muxer, this)
//                        val ae = audioTrack?.next(muxer, this) ?: false
//                        if(!ve&&!ae) {
                        if (!next(muxer, videoTrack, audioTrack)) {
                            count++
                            if (tick < 0) {
                                tick = System.currentTimeMillis()
                            } else if (System.currentTimeMillis() - tick > (3*60*1000) && count > 100_000) {
                                // throw TimeoutException("no response from transcoder.")
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