package io.github.toyota32k.media.lib.converter

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.ConvertSplitter.Companion.positionMsListToRangeMsList
import io.github.toyota32k.media.lib.converter.ConvertSplitter.ConvertProgress
import io.github.toyota32k.media.lib.converter.RangeMs.Companion.outlineRange
import io.github.toyota32k.media.lib.converter.TrimmingRangeList.Companion.toRangeMsList
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.getDuration
import io.github.toyota32k.media.lib.misc.ISO6709LocationParser
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.utils.UtLazyResetableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

class ActualSoughtMapImpl : IActualSoughtMap {
    // 位置補正情報
    private var actualSoughtMap = mutableMapOf<Long,Long>()
    var durationMs:Long = 0L
        private set

    /**
     * 切り取り位置の補正
     * splitAtMs や startMs で指定した位置と実際に切り取られた位置（キーフレーム位置）は異なる可能性がある。
     */
    override fun correctPositionUs(timeUs: Long): Long {
        return actualSoughtMap[timeUs] ?: timeUs
    }

    override fun exportToMap(map: MutableMap<Long, Long>) {
        map.putAll(actualSoughtMap)
    }

    override val entries: Set<Map.Entry<Long, Long>> get() = actualSoughtMap.entries

    /**
     * trim() に使った Array<RangeMs> を一括補正する。
     * trim()後の位置ではなく、元動画のどこで実際に分割したかを示す値を返す。
     * ConvertResult#adjustedTrimmingRangeList と型互換
     */
    override fun adjustedRangeList(ranges: List<RangeMs>): ITrimmingRangeList {
        return super.adjustedRangeList(durationMs, ranges)
    }
    fun setDurationMs(durationMs:Long) {
        this.durationMs = durationMs
    }
    fun addPosition(at:Long, pos:Long) {
//        actualSoughtMap[rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
        actualSoughtMap[at] = if(pos>=0) pos else at
    }

    fun clear() {
        actualSoughtMap.clear()
        durationMs = 0L
    }

    fun clone(): ActualSoughtMapImpl {
        return ActualSoughtMapImpl().also { copy ->
            copy.actualSoughtMap.putAll(actualSoughtMap)
            copy.durationMs = durationMs
        }
    }
}

interface IOutputFileSelector {
    suspend fun initialize(trimmedRangeMsList:List<RangeMs>):Boolean
    suspend fun selectOutputFile(index:Int, positionMs:Long): IOutputMediaFile?
    suspend fun terminate()
}

/**
 * 複数範囲一括分割の結果 i/f
 */
interface IMultiSplitResult : IResultBase {
    val results: List<IConvertResult>
}

/**
 * 複数の範囲を指定して、一括分割を実行するAPIの i/f 定義
 */
interface IMultiChopper : ICancellable{
    /**
     * 分割位置を指定して複数ファイルに分割
     *
     * @param inputFile         ソース動画ファイル
     * @param positionMsList    分割位置(ms)のリスト
     * @param outputFileSelector 出力ファイル選択コールバック
     * @return IMultiSplitResult
     */
    suspend fun chop(inputFile: IInputMediaFile, positionMsList: List<Long>, outputFileSelector: IOutputFileSelector): IMultiSplitResult
}

/**
 * 事前にセットされたチャプター毎に分割するAPIの i/f 定義
 */
interface IMultiPartitioner : IMultiChopper {

    /**
     * チャプター毎に動画を切り出す
     * @param inputFile         ソース動画ファイル
     * @param outputFileSelector 出力ファイル選択コールバック
     * @return IMultiSplitResult
     */
    suspend fun chop(inputFile: IInputMediaFile, outputFileSelector: IOutputFileSelector): IMultiSplitResult
}

/**
 * 元の形式のまま（デコード・再エンコードしないで）動画ファイルを分割する。
 */
class Splitter private constructor(
//    val inPath:IInputMediaFile,
    private val deleteOutputOnError:Boolean,
    private val abortOnError:Boolean,
    private val rotation: Rotation?,
    private val bufferSize:Int,
    private val actualSoughtMap: ActualSoughtMapImpl = ActualSoughtMapImpl(),
    onProgress : ((IProgress)->Unit)?
    ) : IActualSoughtMap by actualSoughtMap, IMultiChopper {
    companion object {
        val logger = UtLog("Splitter", Converter.logger, Splitter::class.java)
        const val DEFAULT_BUFFER_SIZE:Int = 8 * 1024 * 1024     // 8MB ... 1MB だと extractor.readSampleData() で InvalidArgumentException が発生
        val builder: Builder get() = Builder()
    }
    // 進捗報告
    private val progress = ProgressHandler(onProgress)

    // 位置補正情報
//    private val actualSoughtMap = mutableMapOf<Long,Long>()
//    private var durationMs:Long = 0L

    // キャンセル
    private var isCancelled: Boolean = false
    override fun cancel() {
        isCancelled = true
    }

    // 作業用バッファ
    // 個々に確保するのは非経済的なので、クラスとして確保する。
    // 必要に応じて確保し、不要になったら解放できるよう UtLazyResetableValue を利用
    private val mBuffer = UtLazyResetableValue<ByteBuffer> { ByteBuffer.allocateDirect(bufferSize) }
    private val buffer: ByteBuffer
        get() = mBuffer.value
    private val mBufferInfo = UtLazyResetableValue<MediaCodec.BufferInfo> { MediaCodec.BufferInfo() }
    private val bufferInfo: MediaCodec.BufferInfo
        get() = mBufferInfo.value

    /**
     * 作業用バッファをクリア
     */
    private fun resetBuffer() {
        mBuffer.reset()
        mBufferInfo.reset()
    }

    /**
     * Splitterのファクトリークラス
     */
    class Builder {
//        private var mInPath: IInputMediaFile? = inPath
        private var mDeleteOutputOnError:Boolean = true
        private var mAbortOnError: Boolean = false
        private var mRotation: Rotation? = null
        private var mBufferSize = DEFAULT_BUFFER_SIZE
        private var mOnProgress : ((IProgress)->Unit)? = null

        // region Misc

        /**
         * エラーが発生した outputFile を削除するか
         * デフォルト： true
         */
        fun deleteOutputOnError(flag:Boolean) = apply {
            mDeleteOutputOnError = flag
        }

        /**
         * Multi Splitting の場合に、１つのファイルでエラーが起きたとき、残りのファイル出力を中止するか？
         * デフォルト： false （１つのファイルでエラーが起きても、処理を続ける）
         */
        fun abortOnError(flag:Boolean) = apply {
            mAbortOnError = flag
        }
        
        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation?) = apply {
            mRotation = rotation
        }
        /**
         * 進捗報告ハンドラを設定
         */
        fun setProgressHandler(proc:(IProgress)->Unit) = apply {
            mOnProgress = proc
        }

        fun setBufferSize(sizeInBytes:Int) = apply {
            mBufferSize = sizeInBytes.coerceAtLeast(DEFAULT_BUFFER_SIZE)
        }
        // endregion

        fun build(): Splitter {
//            val inPath = mInPath ?: throw IllegalStateException("input file is not specified.")
            return Splitter(
//                inPath = inPath,
                deleteOutputOnError = mDeleteOutputOnError,
                abortOnError = mAbortOnError,
                rotation = mRotation,
                bufferSize = mBufferSize,
                onProgress = mOnProgress
            )
        }
    }

    /**
     * Closeable をまとめて解放できるようにするクラス
     */
    private class Closeables : Closeable {
        private val list = mutableListOf<Closeable>()
        fun <T:Closeable> add(c:T): T {
            list.add(c)
            return c
        }
        override fun close() {
            list.forEach { it.close() }
        }
    }

    /**
     * US単位の時間範囲を保持するクラス
     */
    class RangeUs(val start:Long, val end:Long) {
        companion object {
            fun fromMs(startMs:Long, endMs:Long) : RangeUs =
                if (endMs<=0 || endMs==Long.MAX_VALUE) RangeUs(startMs*1000L, Long.MAX_VALUE)
                else RangeUs(startMs*1000L, endMs*1000L)
            fun fromMs(rangeMs:RangeMs) : RangeUs =
                fromMs(rangeMs.startMs, rangeMs.endMs)
        }
    }

    /**
     * Video/Audioトラックの処理を行うクラス
     */
    class TrackInfo(inPath:IInputMediaFile, video:Boolean):Closeable {
        private val rawExtractor = inPath.openExtractor()
        private var presentationTimeUs:Long = 0L
        val extractor = rawExtractor.obj
        val trackIndex:Int = findTrackIdx(video)
        var muxerIndex:Int = -1
        val isAvailable: Boolean get() = trackIndex>=0
        var done:Boolean = if (isAvailable) false else true
            private set
        val sampleTimeUs:Long get() = if (isAvailable) extractor.sampleTime else Long.MAX_VALUE
        lateinit var muxer: MediaMuxer
        val logger = UtLog("Track(${if(video) "video" else "audio"})", Converter.logger, Splitter::class.java)

        init {
            logger.info("available = $isAvailable")
            if (isAvailable) {
                extractor.selectTrack(trackIndex)
            }
        }

        private fun findTrackIdx(video: Boolean): Int {
            val type = if (video) "video/" else "audio/"
            for (idx in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(idx)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith(type) == true) {
                    return idx
                }
            }
            return -1
        }

        fun attachMuxer(muxer: MediaMuxer) {
            if (isAvailable) {
                logger.debug()
                this.muxer = muxer
                muxerIndex = muxer.addTrack(extractor.getTrackFormat(trackIndex))
            }
        }

        fun initialSeek(us:Long):Long {
            return if (isAvailable) {
                logger.debug()
                done = false
                extractor.seekTo(us, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                extractor.sampleTime
            } else -1
        }

        fun readAndWrite(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo, rangeUs:RangeUs) : Long {
            if (!isAvailable) return 0L   // トラックが存在しない場合は常にEOSとして扱う
            val startTime = sampleTimeUs
            var consumed = 0L
            if (sampleTimeUs == -1L || (rangeUs.end in 1..<sampleTimeUs)) {
                done = true
            } else {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    done = true
                } else {
                    bufferInfo.presentationTimeUs = presentationTimeUs //sampleTimeUs - rangeUs.start
                    bufferInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(muxerIndex, buffer, bufferInfo)
                    extractor.advance()
                    consumed = sampleTimeUs - startTime
                    presentationTimeUs += consumed
                }
            }
            return consumed
        }

        override fun close() {
            logger.debug()
            extractor.release()
        }
    }

    /**
     * Muxerに付加情報（Rotation、Location)を設定する
     */
    private fun setupMuxer(muxer: MediaMuxer, metaData: MetaData, rotation: Rotation?) {
        val metaRotation = metaData.rotation
        if (metaRotation != null) {
            val r = rotation?.rotate(metaRotation) ?: metaRotation
            muxer.setOrientationHint(r)
            logger.info("metadata: rotation=$metaRotation --> $r")
        } else if(rotation!=null){
            muxer.setOrientationHint(rotation.rotate(0))
        }
        val locationString = metaData.location
        if (locationString != null) {
            val location: FloatArray? = ISO6709LocationParser.parse(locationString)
            if (location != null) {
                muxer.setLocation(location[0], location[1])
                logger.info("metadata: latitude=${location[0]}, longitude=${location[1]}")
            } else {
                logger.error("metadata: failed to parse the location metadata: $locationString")
            }
        }
    }

    /**
     * 指定範囲をextractorから読み出してmuxerに書き込む
     */
    private fun extractRange(videoTrack:TrackInfo, audioTrack:TrackInfo, rangeUs:RangeUs) {
        val posVideo = videoTrack.initialSeek(rangeUs.start)
        audioTrack.initialSeek(if(posVideo>=0) posVideo else rangeUs.start)
        while (!videoTrack.done || !audioTrack.done) {
            if (isCancelled) {
                throw CancellationException()
            }
            if (!videoTrack.done && (audioTrack.done || videoTrack.sampleTimeUs < audioTrack.sampleTimeUs)) {
                // video track を処理
                val dealt = videoTrack.readAndWrite(buffer, bufferInfo, rangeUs)
                progress.updateVideoUs(dealt)
            } else {
                // audio track を処理
                val dealt = audioTrack.readAndWrite(buffer, bufferInfo, rangeUs)
                progress.updateAudioUs(dealt)
            }
        }
        actualSoughtMap.addPosition(rangeUs.start, posVideo) // [rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
    }


    /**
     * 指定範囲をファイルに出力
     */
    private fun extractRangesToFile(inPath: IInputMediaFile, inputMetaData:MetaData, outPath: IOutputMediaFile, rangesUs:List<RangeUs>, report:Report) {
        if (isCancelled) throw CancellationException("not started")
        Closeables().use { closer ->
            // Extractorの準備
            val videoTrack = closer.add(TrackInfo(inPath, video = true))
            val audioTrack = closer.add(TrackInfo(inPath, video = false))
            report.apply {
                updateInputSummary(videoTrack.extractor.getTrackFormat(videoTrack.trackIndex), inputMetaData)
                updateInputSummary(audioTrack.extractor.getTrackFormat(audioTrack.trackIndex), inputMetaData)
                updateInputFileInfo(inPath.getLength(), inputMetaData.duration ?: 0)
            }
            if (!videoTrack.isAvailable || !audioTrack.isAvailable) throw IllegalStateException("no track available")
            // Muxerの準備
            val muxer = closer.add(outPath.openMuxer(ContainerFormat.MPEG_4)).obj.apply {
                setupMuxer(this, inputMetaData, rotation)
            }
            videoTrack.attachMuxer(muxer)
            audioTrack.attachMuxer(muxer)
            muxer.start()

            // Extractorから要求された範囲を読み上げてMuxerへ書き込む
            for (rangeUs in rangesUs) {
                extractRange(videoTrack, audioTrack, rangeUs)
            }
            // ファイナライズ
            muxer.stop()
            report.updateOutputFileInfo(outPath.getLength(), progress.current)
        }
    }

    /**
     * 分割結果（１ファイル）
     */
    data class Result(override val succeeded:Boolean, override val outputFile: IOutputMediaFile?, override val requestedRangeMs: RangeMs, override val actualSoughtMap: IActualSoughtMap?, override val exception:Throwable?, override val errorMessage: String?, override val report: Report?=null):IConvertResult {
        companion object {
            val cancelled = Result(false, null, RangeMs.empty, null,CancellationException(), null)
            fun success(outputFile: IOutputMediaFile, requestedRangeMs: RangeMs, actualSoughtMap: IActualSoughtMap, report:Report) = Result(true, outputFile, requestedRangeMs, actualSoughtMap, null, null, report)
            fun error(e:Throwable, msg:String?=null) = Result(false, null, RangeMs.empty, null, e, msg)
        }
    }

    /**
     * 進捗報告用ハンドラクラス
     */
    private class ProgressHandler(private val onProgress:((IProgress)->Unit)?) : IProgress {
        private val startTick = System.currentTimeMillis()
        private var videoLength = 0L        // ms
        private var audioLength = 0L        // ms

        override var total: Long = 0L       // ms
            private set
        override val current: Long
            get() = min(videoLength, audioLength)
        override var remainingTime: Long = -1L
            private set

        fun setTotalMs(totalMs:Long) {
            total = totalMs
        }

        private fun updateRemainingTime() {
            if (percentage>10) {
                remainingTime = (System.currentTimeMillis() - startTick) * (100 - percentage) / percentage
            }
        }

        fun updateVideoUs(videoUs:Long) {
            val prev = current
            videoLength += videoUs/1000L
            if (prev!=current && total>0) {
                updateRemainingTime()
                onProgress?.invoke(this)
            }
        }

        fun updateAudioUs(audioUs:Long) {
            val prev = current
            audioLength += audioUs/1000L
            if (prev!=current) {
                updateRemainingTime()
                onProgress?.invoke(this)
            }
        }
    }

    /**
     * トリミング
     * start/end を指定してファイルに書き出す
     * @param outPath 出力先
     * @param startMs 開始時間
     * @param endMs 終了時間
     * @return Result
     */
    suspend fun trim(inPath: IInputMediaFile, outPath: IOutputMediaFile, startMs: Long, endMs:Long): IConvertResult {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            val metaData = MetaData.fromFile(inPath)
            actualSoughtMap.setDurationMs(metaData.duration ?: Long.MAX_VALUE)
            val actualEndMs = if (endMs>=0) endMs else actualSoughtMap.durationMs
            if (startMs<0 || actualEndMs<=startMs) return@withContext Result.error(IllegalArgumentException("invalid range: $startMs-$actualEndMs"))
            try {
                progress.setTotalMs(if (actualEndMs==Long.MAX_VALUE) -1L else actualEndMs-startMs)
                val report = Report().apply { start() }
                extractRangesToFile(inPath, metaData, outPath, listOf(RangeUs.fromMs(startMs, actualEndMs)), report)
                Result.success(outPath, RangeMs(startMs, actualEndMs), detachActualSoughtMap(), report.apply { end() })
            } catch (e:Throwable) {
                if (e !is CancellationException) {
                    logger.error(e)
                }
                if (deleteOutputOnError) {
                    outPath.safeDelete()
                }
                Result.error(e)
            } finally {
                resetBuffer()
            }
        }

    }

    /**
     * 有効範囲を複数指定して動画を切り取って１つのファイルに書き出す。
     *
     * @param outPath 出力先
     * @param ranges 有効範囲
     * @return Result
     */
    suspend fun trim(inPath: IInputMediaFile, outPath: IOutputMediaFile, ranges: List<RangeMs>):IConvertResult {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            if (ranges.isEmpty()) return@withContext Result.error(IllegalArgumentException("ranges is empty"))
            val metaData = MetaData.fromFile(inPath)
            actualSoughtMap.setDurationMs(metaData.duration ?: Long.MAX_VALUE)
            progress.setTotalMs( ranges.fold(0L) { acc, range ->
                if (acc<0) return@fold -1
                val actualEnd = if (range.endMs<0||range.endMs==Long.MAX_VALUE) actualSoughtMap.durationMs else range.endMs
                if (actualEnd == Long.MAX_VALUE) -1
                else acc + (actualEnd - range.startMs)
            })
            try {
                val report = Report().apply { start() }
                extractRangesToFile(inPath, metaData, outPath, ranges.map { RangeUs.fromMs(it) }, report)
                Result.success(outPath, ranges.outlineRange(metaData.duration ?: Long.MAX_VALUE),detachActualSoughtMap(), report.apply { end() })
            } catch (e:Throwable) {
                if (e !is CancellationException) {
                    logger.error(e)
                }
                if (deleteOutputOnError) {
                    outPath.safeDelete()
                }
                Result.error(e)
            } finally {
                resetBuffer()
            }
        }

    }

    /**
     * ファイルを２つに分割
     * @param out1Path 出力先1
     * @param out2Path 出力先2
     * @param atTimeMs 指定時間
     * @return Result
     */
    suspend fun chop(inPath: IInputMediaFile, out1Path: IOutputMediaFile, out2Path: IOutputMediaFile, atTimeMs:Long) : List<IConvertResult> {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            if (atTimeMs<=0L) return@withContext listOf(Result.error(IllegalArgumentException("invalid time: $atTimeMs")))
            val metaData = MetaData.fromFile(inPath)
            actualSoughtMap.setDurationMs(metaData.duration ?: Long.MAX_VALUE)
            try {
                progress.setTotalMs( metaData.duration?:-1L )
                val report1 = Report().apply { start() }
                extractRangesToFile(inPath, metaData, out1Path, listOf(RangeUs.fromMs(0L,atTimeMs)), report1)
                val report2 = Report().apply { start() }
                extractRangesToFile(inPath, metaData, out2Path, listOf(RangeUs.fromMs(atTimeMs, Long.MAX_VALUE)), report2)
                val map = detachActualSoughtMap()
                listOf(
                    Result.success(out1Path, RangeMs(0L,atTimeMs),map, report1),
                    Result.success(out2Path, RangeMs(atTimeMs, metaData.duration?:Long.MAX_VALUE), map, report2))
            } catch (e:Throwable) {
                if (e !is CancellationException) {
                    logger.error(e)
                }
                if (deleteOutputOnError) {
                    out1Path.safeDelete()
                    out2Path.safeDelete()
                }
                return@withContext listOf(Result.error(e), Result.error(e))
            } finally {
                resetBuffer()
            }
        }
    }

    /**
     * 分割結果（複数ファイル）
     */
    class MultiResult : IMultiSplitResult {
        override val results: MutableList<IConvertResult> = mutableListOf()
        override val succeeded: Boolean get() = results.all { it.succeeded }
        override val cancelled: Boolean get() = results.any { it.cancelled }
        override val exception: Throwable? get() = results.firstOrNull { !it.cancelled && it.exception!=null }?.exception
        override val errorMessage: String? get() = results.firstOrNull { !it.cancelled && it.errorMessage!=null }?.errorMessage

        fun add(result: IConvertResult) = apply {
            results.add(result)
        }

        fun cancel(): MultiResult = apply {
            add(Result.cancelled)
        }

        fun error(e: Throwable, msg: String? = null): MultiResult = apply {
            add(Result.error(e,msg))
        }
    }

    /**
     * 分割位置を指定して複数ファイルに分割
     *
     * @param inputFile         ソース動画ファイル
     * @param positionMsList    分割位置(ms)のリスト
     * @param outputFileSelector 出力ファイル選択コールバック
     * @return IMultiSplitResult
     */
    override suspend fun chop(inputFile: IInputMediaFile, positionMsList: List<Long>, outputFileSelector: IOutputFileSelector): IMultiSplitResult {
        val results = MultiResult()
        actualSoughtMap.clear()
        isCancelled = false

        // outputFileSelector.initializeByRanges()用に positionMsList を RangeMsリストに変換
        // 事前にまとめて出力ファイルを準備しておくために利用してもらう。
        val rangeMsList = mutableListOf<RangeMs>()
        var start = 0L
        for (pos in positionMsList) {
            rangeMsList.add(RangeMs(start, pos))
            start = pos
        }
        rangeMsList.add(RangeMs(start, 0))
        if (!outputFileSelector.initialize(rangeMsList)) {
            return results.cancel()
        }

        return withContext(Dispatchers.IO) {
            try {
                val metaData = MetaData.fromFile(inputFile)
                val count = positionMsList.size
                for (i in 0..count) {
                    actualSoughtMap.setDurationMs(metaData.duration ?: Long.MAX_VALUE)
                    val toTimeMs = if (i < count) positionMsList[i] else Long.MAX_VALUE
                    val fromTimeMs = if (i > 0) positionMsList[i - 1] else 0L
                    val outPath = outputFileSelector.selectOutputFile(i, fromTimeMs)
                    if (outPath == null) {
                        // cancelled
                        results.cancel()
                        break
                    }
                    try {
                        progress.setTotalMs(metaData.duration ?: -1L)
                        val report = Report().apply { start() }
                        extractRangesToFile(inputFile,metaData, outPath, listOf(RangeUs.fromMs(fromTimeMs, toTimeMs)), report)
                        results.add(Result.success(outPath, RangeMs(fromTimeMs, toTimeMs), detachActualSoughtMap(), report.apply { end()}))
                    } catch (e: Throwable) {
                        if (deleteOutputOnError) {
                            outPath.safeDelete()
                        }
                        if (e is CancellationException) {
                            results.cancel()
                            break
                        } else {
                            results.error(e)
                            if (abortOnError) {
                                break
                            }
                        }
                        logger.error(e)
                    }
                }
                results
            } catch (e: Throwable) {
                logger.error(e)
                results.error(e)
            } finally {
                resetBuffer()
                outputFileSelector.terminate()
            }
        }
    }

    /**
     * ActualSoughtMap を切り離して取得する。
     */
    fun detachActualSoughtMap(): IActualSoughtMap {
        val map = actualSoughtMap.clone()
        actualSoughtMap.clear()
        return map
    }
}

/**
 * トリミング＋分割
 * Splitter はトリミングするか分割するかの２択なのに対して、こちらは、トリミングしつつ分割する。
 * Splitter#chopでは、positions引数で指定した分割位置＋１個のファイルが出力されるが、
 * TrimSplitterでは、分割範囲がトリミングによって無効化されているケースもあり得るので、
 * トリミング位置（rangeList）と分割位置(positionMsList)の両方を評価するまで分割数（出力ファイル数）は決定されない。
 * このため、出力ファイルを指定する代わりに、IOutputFileSelector i/f を使って、必要に応じて出力ファイルを要求（コールバック）することになる。
 */
class TrimSplitter(
    private val splitterBuilder: Splitter.Builder,
    private val abortOnError: Boolean,
    private val trimmingRangeList: List<TrimmingRange>,
//    private val rangeList: List<RangeMs>,   // trimming
    private val progressHandler: ((IProgress)->Unit)? = null
) : IMultiChopper, IMultiPartitioner {
    companion object {
        val builder: TrimSplitter.Builder get() = Builder()
    }
    class Builder {
        private val mSplitterBuilder = Splitter.Builder()
        private var mOnProgress: ((IProgress)->Unit)? = null
//        private val mRangeList = mutableListOf<RangeMs>()
        private var mAbortOnError = false
        private var mTrimmingRangeListBuilder = TrimmingRangeList.Builder()

        fun trimming(fn: TrimmingRangeList.Builder.()->Unit) = apply {
            mTrimmingRangeListBuilder.fn()
        }

        fun setProgressHandler(handler: (IProgress)->Unit) = apply {
            mOnProgress = handler
        }

        /**
         * エラー発生時に出力ファイルを削除するかどうか
         * デフォルトは true
         */
        fun deleteOutputOnError(flag:Boolean) = apply {
            mSplitterBuilder.deleteOutputOnError(flag)
        }
        
        fun abortOnError(flag:Boolean) = apply {
            mAbortOnError = false
            mSplitterBuilder.abortOnError(flag)
            
        }
        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation?) = apply {
            mSplitterBuilder.rotate(rotation)
        }

        fun build(): TrimSplitter? {
            val trimmingRangeList = mTrimmingRangeListBuilder.build()?.list ?: return null
            return TrimSplitter(mSplitterBuilder, mAbortOnError, trimmingRangeList, mOnProgress)
        }
    }

    private var cancelled:Boolean = false
    private var splitter: Splitter? = null

    /**
     * 分割位置を指定して複数ファイルに分割
     *
     * @param inputFile         ソース動画ファイル
     * @param positionMsList    分割位置(ms)のリスト
     * @param outputFileSelector 出力ファイル選択コールバック
     * @return IMultiSplitResult
     */
    override suspend fun chop(inputFile: IInputMediaFile, positionMsList: List<Long>, outputFileSelector: IOutputFileSelector): IMultiSplitResult {
        cancelled = false
        if (positionMsList.isEmpty()) throw IllegalArgumentException("chopList is empty")
        val duration = inputFile.openMetadataRetriever().useObj { it.getDuration() } ?: throw IllegalStateException("cannot retrieve duration")

        val result = Splitter.MultiResult()
        val rangeMsList = positionMsListToRangeMsList(positionMsList, duration)
        if (rangeMsList.isEmpty()) {
            return result
        }
        val totalLengthMs = rangeMsList.fold(0L) { acc, range ->
            acc + range.lengthMs(duration)
        }
        if (!outputFileSelector.initialize(rangeMsList)) {
            return result.cancel()
        }
        var index = 0
        val convProgress = ConvertProgress(totalLengthMs*1000)
        for (range in rangeMsList) {
            val output = outputFileSelector.selectOutputFile(index, range.startMs)
            if (output == null) {
                result.cancel()
                break
            }
            index++
            val splitter = splitterBuilder
                .apply {
                    if (progressHandler!=null) {
                        setProgressHandler { progress->
                            convProgress.updateDuration(progress.current)
                            progressHandler(convProgress)
                        }
                    }
                }
                .build()
            this.splitter = splitter
            if (cancelled) {
                result.cancel()
                break
            }
            val trimmingRangeList = TrimmingRangeList.Builder(trimmingRangeList)
                .startFromMs(range.startMs)
                .endAtMs(range.endMs)
                .build() ?: continue
//                .list.toRangeMsList()
            val ranges = trimmingRangeList.list.toRangeMsList()
            val splitResult = splitter.trim(inputFile, output, ranges)
            result.add (splitResult)
            if (splitResult.cancelled) {
                break
            } else if (abortOnError && splitResult.hasError) {
                break
            }
            convProgress.updateDuration(ranges.fold(0L) { acc, range ->
                acc + range.lengthMs(duration)
            })
        }
        splitter = null
        outputFileSelector.terminate()
        return result
    }

    /**
     * チャプター毎に動画を切り出す
     * @param inputFile         ソース動画ファイル
     * @param outputFileSelector 出力ファイル選択コールバック
     * @return IMultiSplitResult
     */
    override suspend fun chop(inputFile: IInputMediaFile, outputFileSelector: IOutputFileSelector): IMultiSplitResult {
        cancelled = false
        val rangeList = trimmingRangeList.toRangeMsList()
        if (trimmingRangeList.isEmpty()) throw IllegalArgumentException("rangeList is empty")
        val duration = inputFile.openMetadataRetriever().useObj { it.getDuration() } ?: throw IllegalStateException("cannot retrieve duration")

        val result = Splitter.MultiResult()
        if (!outputFileSelector.initialize(rangeList)) {
            return result.cancel()
        }
        val totalRangeMs = rangeList.fold(0L) { acc, range ->
            acc + range.lengthMs(duration)
        }
        var index = 0
        val convProgress = ConvertProgress(totalRangeMs*1000)
        for (range in rangeList) {
            val output = outputFileSelector.selectOutputFile(index, range.startMs)
            if (output==null) {
                result.cancel()
                break
            }
            index++
            val splitter = splitterBuilder
                .apply {
                    if (progressHandler!=null) {
                        setProgressHandler { progress->
                            convProgress.updateDuration(progress.current)
                            progressHandler(convProgress)
                        }
                    }
                }
                .build()
            this.splitter = splitter
            if (cancelled) {
                result.cancel()
                break
            }
            val splitResult = splitter.trim(inputFile, output, range.startMs, range.endMs)
            result.add (splitResult)
            if (splitResult.cancelled) {
                break
            } else if (abortOnError && splitResult.hasError) {
                break
            }
            convProgress.updateDuration(range.lengthMs(duration))
        }
        splitter = null
        outputFileSelector.terminate()
        return result
    }

    override fun cancel() {
        cancelled = true
        splitter?.cancel()
    }
}