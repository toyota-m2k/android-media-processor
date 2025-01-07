package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.net.Uri
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.misc.RingBuffer
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.report.Summary
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.track.AudioTrack
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.track.Track
import io.github.toyota32k.media.lib.track.VideoTrack
import io.github.toyota32k.utils.IAwaiter
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.UtLoggerInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * 動画ファイルのトランスコード/トリミングを行うコンバータークラス
 */
class Converter {
    companion object {
        val logger = UtLog("AMP", null, "io.github.toyota32k.media.lib.")
        @Suppress("unused")
        val factory
            get() = Factory()
        // 片方のトラックがN回以上、応答なしになったとき、もう片方のトラックに処理をまわす、そのNの定義（数は適当）
        private const val MAX_NO_EFFECTED_COUNT = 20
        // 両方のトラックが、デコーダーまでEOSになった後、NOPのまま待たされる時間の限界値
        private const val LIMIT_OF_PATIENCE = 15*1000L        // 15秒
        private const val MAX_RETRY_COUNT = 1000

        fun analyze(file:IInputMediaFile) : Summary {
            return try {
                Summary.getSummary(file)
            } catch(_:Throwable) {
                Summary()
            }
        }

        @Suppress("unused")
        fun setExternalLogger(externalLogger:IAmpLogger) {
            UtLoggerInstance.externalLogger = externalLogger.asUtExternalLogger()
        }
    }

    lateinit var inPath:IInputMediaFile
    lateinit var outPath: IOutputMediaFile

    var videoStrategy: IVideoStrategy = PresetVideoStrategies.AVC720Profile
    var audioStrategy:IAudioStrategy=PresetAudioStrategies.AACDefault
    var trimmingRangeList : ITrimmingRangeList = ITrimmingRangeList.empty() // TrimmingRange.Empty
    var deleteOutputOnError:Boolean = true
    var rotation: Rotation? = null
    var containerFormat: ContainerFormat = ContainerFormat.MPEG_4
    var preferSoftwareDecoder: Boolean = false  // 　HWデコーダーで読めない動画も、SWデコーダーなら読めるかもしれない。（Videoのみ）
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

        fun input(url: String, context: Context): Factory {
            if(!url.startsWith("http")) throw IllegalArgumentException("url must be http or https")
            converter.inPath = HttpInputFile(context, url)
            return this
        }

        fun input(source: IHttpStreamSource, context: Context): Factory {
            converter.inPath = HttpInputFile(context, source)
            return this
        }

        fun input(src:IInputMediaFile): Factory {
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

        fun output(dst:IOutputMediaFile):Factory {
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

        fun rotate(rotation: Rotation):Factory {
            converter.rotation = rotation
            return this
        }

        fun containerFormat(format: ContainerFormat):Factory {
            converter.containerFormat = format
            return this
        }

        fun preferSoftwareDecoder(flag:Boolean):Factory {
            converter.preferSoftwareDecoder = flag
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

//        suspend fun execute() : ConvertResult {
//            return build().execute()
//        }
//
//        fun executeAsync(coroutineScope: CoroutineScope?=null):IAwaiter<ConvertResult> {
//            return build().executeAsync(coroutineScope)
//        }
    }

    /**
     * Awaiter
     * IAwaiter(キャンセル可能な待ち合わせi/f)の実装クラス
     */
    private class Awaiter: IAwaiter<ConvertResult>, ICancellation {
        override var isCancelled: Boolean = false
        lateinit var deferred:Deferred<ConvertResult>

        override fun cancel() {
            isCancelled = true      // deferred.isCancelled の参照が遅いかも知れないので、自前でフラグを持っておく
            deferred.cancel()
        }

        override suspend fun await():ConvertResult {
            return try {
                deferred.await()
            } catch(_:CancellationException) {
                return ConvertResult.cancelled
            }
        }
    }

    /**
     * IProgress(進捗報告i/f)の実装クラス
     */
    private class Progress(val trimmingRangeList: ITrimmingRangeList, val onProgress:(IProgress)->Unit): IProgress, AutoCloseable {
        companion object {
            const val ENTRY_COUNT = 50
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
            progressInUs = total
        }

        lateinit var job: Job
        override fun close() {
            job.cancel()
        }

        private fun startWatch() {
            data class ProgressData(
                override var total: Long = 0,
                override var current: Long = 0,
                override var remainingTime: Long = 0):IProgress {
                fun update(progress: IProgress) {
                    total = progress.total
                    current = progress.current
                    remainingTime = progress.remainingTime
                }
            }
            val progressData = ProgressData()

            job = CoroutineScope(Dispatchers.Main).launch {
                do {
                    synchronized(this) { progressData.update(this@Progress) }
                    onProgress(progressData)
                    delay(500L)
                } while(permyriad<10000)
            }
        }

        private var dTime:Long = 0L

        var progressInUs: Long = 0L
            get() = synchronized(this) { field }
            set(v) {
                synchronized(this) {
                    if (field < v) {
                        field = v
                        val pos = v // trimmingRangeList.getPositionInTrimmedDuration(v)
                        val dur = total
                        val tick = System.currentTimeMillis()
                        if(tick-dTime>=1000L) {
                            dTime = 0L  // コンバート速度、残り時間は1秒に1回更新する
                        }
                        if (dur > 0 && dTime==0L) {
                            dTime = tick
                            statistics.put(DealtEntry(pos, tick))
                            val head = statistics.head
                            val tail = statistics.tail
                            val time = tail.tick - head.tick
                            if (time > 2000) {
                                val velocity = (tail.position - head.position) / time  // us / ms
                                if (velocity > 0) {
                                    remainingTime = (dur - pos) / velocity
                                }
                            }
                        }
                    }
                }
            }

        override var remainingTime: Long = 0

        override val total: Long
            get() = trimmingRangeList.trimmedDurationUs
        override val current: Long
            get() = progressInUs

        init {
            startWatch()
        }
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

        val eos:Boolean get() = videoTrack.eos && audioTrack?.eos != false

        private fun runUp():Boolean {
            // フォーマットが確定するまでは、入力がすべてバッファリングされるだけで、outputへの書き込みが発生しないため、
            val rv = if(!muxer.isVideoReady) {
                if(videoTrack.eos) {
                    throw IllegalStateException("unexpected eos in video track.")
                }
                videoTrack.consume()
            } else false

            val ra = if(audioTrack!=null && !muxer.isAudioReady) {
                if(audioTrack.eos) {
                    throw IllegalStateException("unexpected eos in audio track.")
                }
                audioTrack.consume()
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
                    if(videoNoEffectContext>MAX_NO_EFFECTED_COUNT) {
                        // N回以上、ビデオトラックから応答がなければ、オーディオトラックに処理をまわしてみる
//                        logger.debug {"no response from video track ($videoNoEffectContext) ... try audio track."}
                        videoNoEffectContext = 0
                        audioTrack
                    } else {
                        // 順当にビデオトラック
                        videoTrack
                    }
                }
                else {
                    // オーディオトラックの処理がビデオトラックより遅れている
                    if(audioNoEffectedCount>MAX_NO_EFFECTED_COUNT) {
                        // N回以上、オーディオトラックから応答がなければ、ビデオトラックに処理をまわしてみる。
//                        logger.debug {"no response from audio track ($audioNoEffectedCount)... try video track."}
                        audioNoEffectedCount = 0
                        videoTrack
                    } else {
                        audioTrack
                    }
                }

        fun next():Boolean {
            // フォーマットが確定するまでは、両方を平行して進める
            if(!muxer.isReady) {
                return runUp()
            }
            // フォーマットが確定したら、進捗度合いが同程度になるよう調停する。
            val track = nextTrack
            val result = track.consume()

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

    @Deprecated("use execute()")
    fun executeAsync(coroutineScope: CoroutineScope?=null):IAwaiter<ConvertResult> {
        val cs = coroutineScope ?: CoroutineScope(Dispatchers.IO)

        return Awaiter().apply {
            deferred = cs.async {
                executeCore(this@apply)
            }
        }
    }

    private class Cancellation: ICancellation {
        override var isCancelled: Boolean = false
        lateinit var deferred:Deferred<ConvertResult>
        fun cancel() {
            isCancelled = true      // deferred.isCancelled の参照が遅いかも知れないので、自前でフラグを持っておく
            deferred.cancel()
        }
    }

    private var cancellation:Cancellation? = null

    /**
     * コンバート (execute) のキャンセル
     */
    fun cancel() {
        cancellation?.cancel()
    }

    /**
     * コンバートを実行
     *
     * @return  ConvertResult
     */
    suspend fun execute() : ConvertResult {
        return try {
            withContext(Dispatchers.IO) {
                Cancellation().apply {
                    cancellation = this
                    deferred = async {
                        executeCore(this@apply)
                    }
                }
            }.deferred.await()
        } catch(_:CancellationException) {
            return ConvertResult.cancelled
        } catch(e:Throwable) {
            logger.error(e)
            return ConvertResult.error(e)
        } finally {
            cancellation = null
        }
    }

    /**
     * コンバートを実行の中の人
     */
    private suspend fun executeCore(cancellation: ICancellation):ConvertResult {
        return withContext(Dispatchers.IO) {
            try {
                report = Report().apply { start() }
                AudioTrack.create(inPath, audioStrategy, report, cancellation).use { audioTrack->
                VideoTrack.create(inPath, videoStrategy, report, cancellation, preferSoftwareDecoder).use { videoTrack->
                Muxer(videoTrack.metaData, outPath, audioTrack!=null, rotation, containerFormat).use { muxer->
                    videoTrack.chain(muxer)
                    audioTrack?.chain(muxer)
                    trimmingRangeList = videoTrack.extractor.adjustAndSetTrimmingRangeList(trimmingRangeList, muxer.durationUs)
                    audioTrack?.extractor?.setTrimmingRangeList(trimmingRangeList)
                    report.updateInputFileInfo(inPath.getLength(), muxer.durationUs/1000L)
                    Progress.create(trimmingRangeList, onProgress).use { progress ->
                        var tick = -1L
                        var count = 0
                        val tracks = TrackMediator(muxer, videoTrack, audioTrack)
                        while (!tracks.eos && !cancellation.isCancelled) {
//                        val ve = videoTrack.next(muxer, this)
//                        val ae = audioTrack?.next(muxer, this) ?: false
//                        if(!ve&&!ae) {
                            if (!tracks.next()) {
                                if (videoTrack.decoder.eos && audioTrack?.decoder?.eos != false) {
                                    count++
                                    if (tick < 0) {
                                        tick = System.currentTimeMillis()
                                    } else if (System.currentTimeMillis() - tick > LIMIT_OF_PATIENCE && count > MAX_RETRY_COUNT) {
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
                                if (progress != null) {
                                    progress.videoProgressInUs = videoTrack.encoder.writtenPresentationTimeUs
                                    progress.audioProgressInUs = audioTrack?.encoder?.writtenPresentationTimeUs ?: progress.videoProgressInUs
                                }
                            }
                        }
                        if(cancellation.isCancelled) {
                            ConvertResult.cancelled
                        } else {
                            report.updateOutputFileInfo(outPath.getLength(), videoTrack.extractor.naturalDurationUs / 1000L)
                            report.end()
                            report.setDurationInfo(trimmingRangeList.trimmedDurationUs, videoTrack.extractor.naturalDurationUs, audioTrack?.extractor?.naturalDurationUs ?: 0L, muxer.naturalDurationUs)
                            logger.info(report.toString())
                            progress?.finish()
                            ConvertResult.succeeded(trimmingRangeList, report)
                        }
                    }
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