package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.net.Uri
import io.github.toyota32k.media.lib.misc.RingBuffer
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.track.AudioTrack
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.track.Track
import io.github.toyota32k.media.lib.track.VideoTrack
import io.github.toyota32k.media.lib.utils.UtLog
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * 動画ファイルのトランスコード/トリミングを行うコンバータークラス
 */
class Converter {
    companion object {
        val logger = UtLog("AMP", null, "io.github.toyota32k.media.lib.")
        val factory
            get() = Factory()
        // 片方のトラックがN回以上、応答なしになったとき、もう片方のトラックに処理をまわす、そのNの定義（数は適当）
        private const val MaxNoEffectedCount = 20
        // 両方のトラックが、デコーダーまでEOSになった後、NOPのまま待たされる時間の限界値
        private const val LimitOfPatience = 15*1000L        // 15秒
        private const val MaxRetryCount = 1000
    }

    lateinit var inPath:AndroidFile
    lateinit var outPath: AndroidFile

    var videoStrategy: IVideoStrategy = PresetVideoStrategies.AVC720Profile
    var audioStrategy:IAudioStrategy=PresetAudioStrategies.AACDefault
    var trimmingRangeList : ITrimmingRangeList = ITrimmingRangeList.empty() // TrimmingRange.Empty
    var deleteOutputOnError:Boolean = true
    var onProgress : ((IProgress)->Unit)? = null
    lateinit var report :Report

    /**
     * Converterのファクトリクラス
     */
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    class Factory {
        companion object {
            val logger = UtLog("Factory", Converter.logger)
        }
        private val converter = Converter()
        private val trimmingRangeList = TrimmingRangeListImpl()

        // for backward compatibility only
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
        fun audioStrategy(s: IAudioStrategy):Factory {
            converter.audioStrategy = s
            return this
        }

        fun addTrimmingRange(startMs:Long, endMs:Long):Factory {
            trimmingRangeList.addRange(startMs*1000, endMs*1000)
            return this
        }

        data class RangeMs(val startMs:Long, val endMs:Long)

        fun addTrimmingRanges(vararg ranges:RangeMs):Factory {
            ranges.forEach {
                addTrimmingRange(it.startMs, it.endMs)
            }
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
            logger.info("input : ${converter.inPath}")
            logger.info("output: ${converter.outPath}")
            logger.info("video strategy: ${converter.videoStrategy.javaClass.name}")
            logger.info("audio strategy: ${converter.audioStrategy.javaClass.name}")

            if(trimmingRangeList.isEmpty && (trimStart>0 || trimEnd>0)) {
                trimmingRangeList.addRange(trimStart, trimEnd)
            }

            if(trimmingRangeList.isNotEmpty) {
//                logger.info("trimming start: ${trimStart / 1000} ms")
//                logger.info("trimming end  : ${trimEnd / 1000} ms")
                converter.trimmingRangeList = trimmingRangeList
            }

            logger.info("delete output on error = ${converter.deleteOutputOnError}")
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
    private class Progress(val trimmingRangeList: ITrimmingRangeList, val onProgress:(IProgress)->Unit): IProgress {
        companion object {
            const val ENTRY_COUNT = 10
            fun create(trimmingRangeList: ITrimmingRangeList, onProgress:((IProgress)->Unit)?):Progress? {
                return if(onProgress!=null) Progress(trimmingRangeList, onProgress) else null
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
                    val pos = v // trimmingRangeList.getPositionInTrimmedDuration(v)
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
            get() = trimmingRangeList.trimmedDurationUs
        override val current: Long
            get() = progressInUs
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
     *
     * このロジックだと、進捗が遅れているトラックで読み込みが止まると、もう片方も（処理可能であっても）止まってしまってしまう可能性があるので、
     * MaxNoEffectedCount回（20回 <-- テキトー）に1回くらいは、もう片方に処理をまわしてみる。
     */
    class TrackMediator(val muxer:Muxer, private val videoTrack: VideoTrack, private val audioTrack: AudioTrack?) {
        private var audioNoEffectedCount = 0
        private var videoNoEffectContext = 0

        val eos:Boolean get() = videoTrack.eos && audioTrack?.eos?:true

        private fun runUp(coroutineScope: CoroutineScope):Boolean {
            // フォーマットが確定するまでは、入力がすべてバッファリングされるだけで、outputへの書き込みが発生しないため、
            val rv = if(!muxer.isVideoReady) {
                if(videoTrack.eos) {
                    throw IllegalStateException("unexpected eos in video track.")
                }
                videoTrack.next(muxer, coroutineScope)
            } else false

            val ra = if(audioTrack!=null && !muxer.isAudioReady) {
                if(audioTrack.eos) {
                    throw IllegalStateException("unexpected eos in audio track.")
                }
                audioTrack.next(muxer, coroutineScope)
            } else false
            return rv || ra
        }


        private val nextTrack:Track
            get() =
                if(audioTrack==null||audioTrack.eos) {
                    // オーディオトラックがない、または、オーディオトラックがEOSに達しているなら、videoTrack一択
                    videoTrack
                }
                else if(videoTrack.eos) {
                    // ビデオトラックがEOSに達している
                    audioTrack
                }
                else if(videoTrack.convertedLength<=audioTrack.convertedLength) {
                    // ビデオトラックの処理がオーディオトラックより遅れている
                    if(videoNoEffectContext>MaxNoEffectedCount) {
                        // N回以上、ビデオトラックから応答がなければ、オーディオトラックに処理をまわしてみる
                        logger.debug {"no response from video track ($videoNoEffectContext) ... try audio track."}
                        videoNoEffectContext = 0
                        audioTrack
                    } else {
                        // 順当にビデオトラック
                        videoTrack
                    }
                }
                else {
                    // オーディオトラックの処理がビデオトラックより遅れている
                    if(audioNoEffectedCount>MaxNoEffectedCount) {
                        // N回以上、オーディオトラックから応答がなければ、ビデオトラックに処理をまわしてみる。
                        logger.debug {"no response from audio track ($audioNoEffectedCount)... try video track."}
                        audioNoEffectedCount = 0
                        videoTrack
                    } else {
                        audioTrack
                    }
                }

        fun next(coroutineScope: CoroutineScope):Boolean {
            // フォーマットが確定するまでは、両方を平行して進める
            if(!muxer.isReady) {
                return runUp(coroutineScope)
            }
            // フォーマットが確定したら、進捗度合いが同程度になるよう調停する。
            val track = nextTrack
            val result = track.next(muxer, coroutineScope)

            // 応答なしカウンタのメンテナンス
            if(track === videoTrack) {
                if(result) {
                    videoNoEffectContext = 0
                } else {
                    videoNoEffectContext++
//                    logger.verbose("no response from video track ($videoNoEffectContext)")
                }
            } else {
                if(result) {
                    audioNoEffectedCount = 0
                } else {
                    audioNoEffectedCount++
//                    logger.verbose("no response from audio track ($audioNoEffectedCount)")
                }
            }
            return result
        }
    }

//    var aLetterForYou:String = ""
//        private set
//
//    private fun sendALetterToYou(v:Boolean, a:Boolean) {
//        if(v && a) {
//            aLetterForYou = "video and audio tracks were forced to be finalized"
//        } else if (v) {
//            aLetterForYou = "video track was forced to be finalized"
//        } else if (a) {
//            aLetterForYou = "audio track was forced to be finalized"
//        } else {
//            aLetterForYou = "unexpected status."
//        }
//    }

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
                report = Report().apply { start() }
                AudioTrack.create(inPath, audioStrategy,report).use { audioTrack->
                VideoTrack.create(inPath, videoStrategy,report).use { videoTrack->
                Muxer(inPath, outPath, audioTrack!=null).use { muxer->
                    trimmingRangeList.closeBy(muxer.durationUs)
                    report.updateInputFileInfo(inPath.getLength(), muxer.durationUs/1000L)
                    val progress = Progress.create(trimmingRangeList, onProgress)
                    videoTrack.trimmingRangeList = trimmingRangeList
                    audioTrack?.trimmingRangeList = trimmingRangeList
//                    fun eos():Boolean = videoTrack.eos && audioTrack?.eos?:true
                    var tick = -1L
                    var count = 0
                    val tracks = TrackMediator(muxer, videoTrack, audioTrack)
                    while (!tracks.eos) {
                        if(!isActive) {
                            throw CancellationException("cancelled")
                        }


//                        val ve = videoTrack.next(muxer, this)
//                        val ae = audioTrack?.next(muxer, this) ?: false
//                        if(!ve&&!ae) {
                        if (!tracks.next(this)) {
                            if(videoTrack.decoder.eos && audioTrack?.decoder?.eos != false) {
                                count++
                                if (tick < 0) {
                                    tick = System.currentTimeMillis()
                                } else if (System.currentTimeMillis() - tick > LimitOfPatience && count > MaxRetryCount) {
                                    logger.info("decoders reached EOS but encoder not working ... forced to stop muxer")
                                    videoTrack.encoder.forceEos(muxer)
                                    audioTrack?.encoder?.forceEos(muxer)
//                                    val fv = videoTrack.encoder.forceEos(muxer)
//                                    val fa = audioTrack?.encoder?.forceEos(muxer)==true
//                                    sendALetterToYou(fv, fa)
                                }
//                                throw TimeoutException("no response from transcoder.")
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
                    report.updateOutputFileInfo(outPath.getLength(), trimmingRangeList.trimmedDurationUs/1000L)
                    report.end()
                    report.dump(logger, "#### Video Converted.")
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