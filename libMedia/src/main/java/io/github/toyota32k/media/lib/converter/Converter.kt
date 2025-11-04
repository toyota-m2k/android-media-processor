package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.isHDR
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.misc.RingBuffer
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.report.Summary
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IHDRSupport
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.surface.RenderOption
import io.github.toyota32k.media.lib.track.AudioTrack
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.media.lib.track.Track
import io.github.toyota32k.media.lib.track.VideoTrack
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
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

typealias RangeMs = Converter.Factory.RangeMs

/**
 * 動画ファイルのトランスコード/トリミングを行うコンバータークラス
 */
class Converter(
    private val inPath:IInputMediaFile,
    private val outPath: IOutputMediaFile,
    private val videoStrategy: IVideoStrategy = PresetVideoStrategies.AVC720Profile,
    private val audioStrategy:IAudioStrategy=PresetAudioStrategies.AACDefault,
    private var trimmingRangeKeeper : ITrimmingRangeKeeper = TrimmingRangeKeeperImpl.empty,
    private val deleteOutputOnError:Boolean = true,
    private val rotation: Rotation? = null,
    private val containerFormat: ContainerFormat = ContainerFormat.MPEG_4,
    private val preferSoftwareDecoder: Boolean = false,  // 　HWデコーダーで読めない動画も、SWデコーダーなら読めるかもしれない。（Videoのみ）
    private val renderOption: RenderOption = RenderOption.DEFAULT,
    private val onProgress : ((IProgress)->Unit)? = null,
) {
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

        fun checkReEncodingNecessity(file: IInputMediaFile, strategy: IVideoStrategy) : Boolean {
            val summary = analyze(file).videoSummary ?: return true // ソース情報が不明
            if (strategy.codec != summary.codec) {
                // コーデックが違う
                return true
            }
            if (summary.bitRate>0 && summary.bitRate > strategy.bitRate.max) {
                // ビットレートが、Strategy の max bitRate より大きい
                return true
            }
            if (max(summary.width, summary.height) > strategy.sizeCriteria.longSide ||
                min(summary.width, summary.height) > strategy.sizeCriteria.shortSide) {
                // 解像度が、Strategy の制限より大きい（縮小が必要）
                return true
            }
            return false
        }
    }

    lateinit var report :Report

    /**
     * Converterのファクトリクラス
     */
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    class Factory {
        companion object {
            val logger = UtLog("Factory", Converter.logger)
        }

        private val trimmingRangeList = TrimmingRangeListImpl()
        private var mInPath:IInputMediaFile? = null
        private var mOutPath: IOutputMediaFile? = null
        private var mVideoStrategy: IVideoStrategy = PresetVideoStrategies.AVC720Profile
        private var mAudioStrategy: IAudioStrategy = PresetAudioStrategies.AACDefault
        private var mOnProgress:((IProgress)->Unit)? = null
        private var mDeleteOutputOnError = true
        private var mRotation: Rotation? = null
        private var mContainerFormat: ContainerFormat = ContainerFormat.MPEG_4
        private var mPreferSoftwareDecoder = false

        // for backward compatibility only
        private var trimStart:Long = 0L
        private var trimEnd:Long = 0L
        private var limitDurationUs: Long = 0L

        private var mKeepProfile:Boolean = false
        private var mKeepHDR: Boolean = false
        private var mBrightnessFactor = 1f
        private var mCropRect: Rect? = null

        /**
         * 入力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * @param IInputMediaFile
         */
        fun input(src:IInputMediaFile) = apply {
            mInPath = src
        }

        /**
         * 入力ファイルを設定
         * @param File
         */
        fun input(path: File)
            = input(AndroidFile(path))

        /**
         * 入力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun input(uri: Uri, context: Context)
            = input(AndroidFile(uri, context))

        /**
         * 入力ファイルを設定
         * @param String URL (http/https)
         * @param Context
         */
        fun input(url: String, context: Context) = apply {
            if(!url.startsWith("http")) throw IllegalArgumentException("url must be http or https")
            input (HttpInputFile(context, url))
        }

        /**
         * 入力ファイルを設定
         * @param IHttpStreamSource
         * @param Context
         */
        fun input(source: IHttpStreamSource, context: Context)
            = input(HttpInputFile(context, source))

        /**
         * 出力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * ただし、inputと異なり、HttpFileは利用不可
         * @param IOutputMediaFile
         */
        fun output(dst:IOutputMediaFile) = apply {
            mOutPath = dst
        }

        /**
         * 出力ファイルを設定
         * @param File
         */
        fun output(path: File)
            = output(AndroidFile(path))

        /**
         * 出力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun output(uri: Uri, context: Context)
            = output(AndroidFile(uri, context))


        /**
         * VideoStrategyを設定
         */
        fun videoStrategy(s:IVideoStrategy) = apply {
            mVideoStrategy = s
        }

        /**
         * AudioStrategyを設定
         */
        fun audioStrategy(s: IAudioStrategy) = apply {
            mAudioStrategy = s
        }

        /**
         * Videoトラックを同じコーデックで再エンコードするとき、Profile/Levelを入力ファイルに合わせる
         */
        fun keepVideoProfile(flag:Boolean=true) = apply {
            mKeepProfile = flag
        }
        /**
         * HDRが有効なVideoトラックを再エンコードするとき、出力コーデックで可能ならHDRを維持するProfileを選択する。
         */
        fun keepHDR(flag:Boolean=true) = apply {
            mKeepHDR = flag
        }

        /**
         * トリミング範囲 (開始位置、終了位置) クラス (ms)
         */
        data class RangeMs(val startMs:Long, val endMs:Long) {
            constructor(start:Duration?, end: Duration?):this(
                startMs = start?.inWholeMilliseconds ?: 0L,
                endMs = end?.inWholeMilliseconds ?: 0L
            )
        }

        /**
         * トリミング範囲を追加
         * @param startMs 開始位置 (ms)
         * @param endMs 終了位置 (ms)   0なら最後まで
         */
        fun addTrimmingRange(startMs:Long, endMs:Long) = apply {
            trimmingRangeList.addRange(startMs*1000, endMs*1000)
        }

        fun resetTrimmingRangeList() = apply {
            trimmingRangeList.clear()
        }

        /**
         * トリミング範囲を追加
         * @param start 開始位置 (Duration) nullなら先頭から
         * @param end 終了位置 (Duration) nullなら最後まで
         */
        fun addTrimmingRange(start:Duration?, end:Duration?)
            = addTrimmingRange(start?.inWholeMilliseconds ?: 0L, end?.inWholeMilliseconds ?: 0L)

        /**
         * トリミング範囲を追加
         * @param range トリミング範囲 (ms)
         */
        fun addTrimmingRange(range: RangeMs) = apply {
            return addTrimmingRange(range.startMs, range.endMs)
        }

        /**
         * トリミング範囲を一括追加
         */
        fun addTrimmingRanges(vararg ranges:RangeMs) = apply {
            ranges.forEach {
                addTrimmingRange(it.startMs, it.endMs)
            }
        }

        /**
         * 指定された位置より前をトリミング
         */
        fun trimmingStartFrom(timeMs:Long) = apply {
            if(timeMs>0) {
                trimStart = timeMs * 1000L
            }
        }

        /**
         * 指定された位置より前をトリミング
         */
        fun trimmingStartFrom(time: Duration)
            = trimmingStartFrom(time.inWholeMilliseconds)

        /**
         * 指定された位置より後をトリミング
         */
        fun trimmingEndTo(timeMs:Long) = apply {
            if(timeMs>0) {
                trimEnd = timeMs * 1000L
            }
        }
        /**
         * 指定された位置より後をトリミング
         */
        fun trimmingEndTo(time: Duration)
            = trimmingEndTo(time.inWholeMilliseconds)

        /**
         * 最大動画長を指定 (ms)
         * 0以下なら制限なし
         */
        fun limitDuration(durationMs:Long) = apply {
            limitDurationUs = durationMs * 1000L
        }

        /**
         * 最大動画長を指定 (Duration)
         * nullまたは0以下なら制限なし
         */
        fun limitDuration(duration: Duration?)
            = limitDuration(duration?.inWholeMilliseconds ?: 0L)

        /**
         * 進捗報告ハンドラを設定
         */
        fun setProgressHandler(proc:(IProgress)->Unit) = apply {
            mOnProgress = proc
        }

        /**
         * エラー発生時に出力ファイルを削除するかどうか
         * デフォルトは true
         */
        fun deleteOutputOnError(flag:Boolean) = apply {
            mDeleteOutputOnError = flag
        }

        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation) = apply {
            mRotation = rotation
        }

        /**
         * コンテナフォーマットを指定
         * MPEG_4 以外はテストしていません。
         */
        fun containerFormat(format: ContainerFormat) = apply {
            mContainerFormat = format
        }

        /**
         * ソフトウェアデコーダーを優先するかどうか
         */
        fun preferSoftwareDecoder(flag:Boolean):Factory {
            mPreferSoftwareDecoder = flag
            return this
        }

        fun brightness(brightness:Float):Factory {
            mBrightnessFactor = brightness
            return this
        }
        fun crop(rect:Rect) : Factory {
            mCropRect = rect
            return this
        }
        fun crop(x:Int, y:Int, cx:Int, cy:Int) : Factory {
            mCropRect = Rect(x, y, x+cx, y+cy)
            return this
        }

        private val inputSummary: Summary by lazy {
            val inPath = mInPath ?: throw IllegalStateException("no input path.")
            analyze(inPath)
        }

        /**
         * 内部メソッド：build()時にVideoStrategyを調整する
         * mKeepHDR, mKeepProfile の設定に基づいて、videoStrategyを調整する
         */
        private fun adjustVideoStrategy(strategy:IVideoStrategy):IVideoStrategy {
            if (!mKeepHDR && !mKeepProfile) return strategy
            val summary = inputSummary
            val srcCodec = summary.videoSummary?.codec ?: return strategy
            val srcProfile = summary.videoSummary?.profile ?: return strategy
            val srcLevel = summary.videoSummary?.level ?: strategy.maxLevel

            return if (mKeepProfile && strategy.codec == srcCodec && strategy.profile != srcProfile) {
                strategy.derived(profile = srcProfile, level = srcLevel)
            } else if (mKeepHDR && srcProfile.isHDR() && strategy is IHDRSupport) {
                if (strategy.codec == srcCodec) {
                    strategy.hdr(srcProfile, srcLevel)
                } else {
                    strategy.hdr()
                }
            } else {
                strategy
            }
        }

        fun build():Converter {
            if(mInPath==null) throw IllegalStateException("input file is not specified.")
            if(mOutPath==null) throw IllegalStateException("output file is not specified.")

            val videoStrategy = if (mKeepProfile || mKeepHDR) {
                adjustVideoStrategy(mVideoStrategy)
            } else {
                mVideoStrategy
            }


            logger.info("### media converter information ###")
            logger.info("input : ${mInPath}")
            logger.info("output: ${mOutPath}")
            logger.info("video strategy: ${videoStrategy.javaClass.name}")
            logger.info("audio strategy: ${mAudioStrategy.javaClass.name}")

            if(trimmingRangeList.isEmpty && (trimStart>0 || trimEnd>0)) {
                trimmingRangeList.addRange(trimStart, trimEnd)
            }

            val trimmingRangeKeeper = if(trimmingRangeList.isNotEmpty) {
                TrimmingRangeKeeperImpl(trimmingRangeList)
            } else {
                TrimmingRangeKeeperImpl.empty
            }
            if(limitDurationUs>0) {
                trimmingRangeKeeper.limitDurationUs = limitDurationUs
                logger.info("limit duration: ${limitDurationUs / 1000} ms")
            }

            logger.info("delete output on error = ${mDeleteOutputOnError}")
            if(mOnProgress==null) {
                logger.info("no progress handler")
            }
            val renderOption = if (mCropRect!=null) {
                val summary = inputSummary.videoSummary ?: throw IllegalStateException("no video information, cannot crop.")
                RenderOption.create(summary.width, summary.height, mCropRect!!, mBrightnessFactor)
            } else if (mBrightnessFactor!=1f) {
                RenderOption.create(mBrightnessFactor)
            } else {
                RenderOption.DEFAULT
            }
            return Converter(
                mInPath!!,
                mOutPath!!,
                videoStrategy,
                mAudioStrategy,
                trimmingRangeKeeper,
                mDeleteOutputOnError,
                mRotation,
                mContainerFormat,
                mPreferSoftwareDecoder,
                renderOption,
                mOnProgress)
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
            ConvertResult.cancelled
        } catch(e:Throwable) {
            logger.error(e)
            ConvertResult.error(e)
        } finally {
            cancellation = null
        }
    }

    /**
     * コンバートを実行の中の人
     */
    private suspend fun executeCore(cancellation: ICancellation):ConvertResult {
        return withContext(Dispatchers.IO) {
            val result = try {
                report = Report().apply { start() }
                AudioTrack.create(inPath, audioStrategy, report, cancellation).use { audioTrack->
                VideoTrack.create(inPath, videoStrategy, report, cancellation, preferSoftwareDecoder, renderOption).use { videoTrack->
                Muxer(videoTrack.metaData, outPath, audioTrack!=null, rotation, containerFormat).use { muxer->
                    videoTrack.chain(muxer)
                    audioTrack?.chain(muxer)
                    trimmingRangeKeeper = videoTrack.extractor.adjustAndSetTrimmingRangeList(trimmingRangeKeeper, muxer.durationUs)
                    audioTrack?.extractor?.setTrimmingRangeList(trimmingRangeKeeper)
                    report.updateInputFileInfo(inPath.getLength(), muxer.durationUs/1000L)
                    Progress.create(trimmingRangeKeeper, onProgress).use { progress ->
                        var tick = -1L
                        var count = 0
                        val tracks = TrackMediator(muxer, videoTrack, audioTrack)
                        while (!tracks.eos && !cancellation.isCancelled) {
                            if (!tracks.next()) {
                                if (videoTrack.decoder.eos && audioTrack?.decoder?.eos != false) {
                                    count++
                                    if (tick < 0) {
                                        tick = System.currentTimeMillis()
                                    } else if (System.currentTimeMillis() - tick > LIMIT_OF_PATIENCE && count > MAX_RETRY_COUNT) {
                                        logger.info("decoders reached EOS but encoder not working ... forced to stop muxer")
                                        videoTrack.encoder.forceEos(muxer)
                                        audioTrack?.encoder?.forceEos(muxer)
                                    }
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
                            if(audioTrack!=null) {
                                // AudioTrack の SamplingRate が、コンバート中に書き変ることがあるので、最後に更新しておく。
                                report.updateOutputSummary(audioTrack.encoder.mediaFormat)
                            }
                            report.updateOutputFileInfo(outPath.getLength(), videoTrack.extractor.naturalDurationUs / 1000L)
                            report.end()
                            report.setDurationInfo(trimmingRangeKeeper.trimmedDurationUs, videoTrack.extractor.naturalDurationUs, audioTrack?.extractor?.naturalDurationUs ?: 0L, muxer.naturalDurationUs)
                            logger.info(report.toString())
                            progress?.finish()
                            ConvertResult.succeeded(trimmingRangeKeeper, report)
                        }
                    }
                }}}
            }
            catch(e:Throwable) {
                logger.stackTrace(e)
                ConvertResult.error(e)
            }
            if(!result.succeeded && deleteOutputOnError) {
                try {
                    outPath.delete()
                } catch(ef:Throwable) {
                    logger.stackTrace(ef,"cannot delete output file: $outPath")
                }
            }
            result
        }
    }
}