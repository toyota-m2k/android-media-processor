package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.Converter.Factory.RangeMs
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.misc.ISO6709LocationParser
import io.github.toyota32k.utils.UtLazyResetableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

/**
 * 元の形式のまま（デコード・再エンコードしないで）動画ファイルを分割する。
 */
class Splitter private constructor(
    val inPath:IInputMediaFile,
    private val deleteOutputOnError:Boolean,
    private val rotation: Rotation?,
    private val bufferSize:Int,
    onProgress : ((IProgress)->Unit)?
    ) {
    companion object {
        val logger = UtLog("Splitter", Converter.logger, Splitter::class.java)
        const val DEFAULT_BUFFER_SIZE:Int = 8 * 1024 * 1024     // 8MB ... 1MB だと extractor.readSampleData() で InvalidArgumentException が発生
    }
    // 進捗報告
    private val progress = ProgressHandler(onProgress)

    // 位置補正情報
    private val actualSoughtMap = mutableMapOf<Long,Long>()
    private var durationMs:Long = 0L

    // キャンセル
    private var isCancelled: Boolean = false
    fun cancel() {
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
    class Factory(inPath:IInputMediaFile?=null) {
        private var mInPath: IInputMediaFile? = inPath
        private var mDeleteOutputOnError:Boolean = true
        private var mRotation: Rotation? = null
        private var mBufferSize = DEFAULT_BUFFER_SIZE
        private var mOnProgress : ((IProgress)->Unit)? = null

        // region inPath

        /**
         * 入力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * @param src: IInputMediaFile
         */
        fun input(src:IInputMediaFile) = apply {
            mInPath = src
        }

        /**
         * 入力ファイルを設定
         * @param path: File
         */
        fun input(path: File)
            = input(AndroidFile(path))

        /**
         * 入力ファイルを設定
         * @param uri: Uri
         * @param context: Context
         */
        fun input(uri: Uri, context: Context)
            = input(AndroidFile(uri, context))

        /**
         * 入力ファイルを設定
         * @param uri: String URL (http/https)
         * @param context: Context
         */
        fun input(url: String, context: Context) = apply {
            if(!url.startsWith("http")) throw IllegalArgumentException("url must be http or https")
            input (HttpInputFile(context, url))
        }

        // endregion

        // region outPath

        /**
         * 入力ファイルを設定
         * @param source: IHttpStreamSource
         * @param context: Context
         */
        fun input(source: IHttpStreamSource, context: Context)
            = input(HttpInputFile(context, source))

        // endregion

        // region Misc

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
            val inPath = mInPath ?: throw IllegalStateException("input file is not specified.")
            return Splitter(
                inPath = inPath,
                deleteOutputOnError = mDeleteOutputOnError,
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
        actualSoughtMap[rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
    }


    /**
     * 指定範囲をファイルに出力
     */
    private fun extractRangesToFile(inputMetaData:MetaData, outPath: IOutputMediaFile, vararg rangesUs:RangeUs) {
        if (isCancelled) throw CancellationException("not started")
        Closeables().use { closer ->
            // Extractorの準備
            val videoTrack = closer.add(TrackInfo(inPath, video = true))
            val audioTrack = closer.add(TrackInfo(inPath, video = false))
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
        }
    }

    /**
     * 変換結果
     */
    data class Result(val succeeded:Boolean, val error:Throwable?) {
        val cancelled : Boolean get() = error is CancellationException
        val hasError: Boolean get() = error != null && !cancelled
        companion object {
            val cancelled = Result(false, CancellationException())
            val success = Result(true, null)
            fun error(e:Throwable) = Result(false, e)
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
    suspend fun trim(outPath: IOutputMediaFile, startMs: Long, endMs:Long): Result {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            val metaData = MetaData.fromFile(inPath)
            durationMs = metaData.duration ?: Long.MAX_VALUE
            val actualEndMs = if (endMs>=0) endMs else durationMs
            if (startMs<0 || actualEndMs<=startMs) return@withContext Result.error(IllegalArgumentException("invalid range: $startMs-$actualEndMs"))
            try {
                progress.setTotalMs(if (actualEndMs==Long.MAX_VALUE) -1L else actualEndMs-startMs)
                extractRangesToFile(metaData, outPath, RangeUs.fromMs(startMs, actualEndMs))
                Result.success
            } catch (e:Throwable) {
                if (e !is CancellationException) {
                    logger.error(e)
                }
                if (deleteOutputOnError) {
                    runCatching { outPath.delete() }
                }
                Result.error(e)
            } finally {
                resetBuffer()
            }
        }

    }

    /**
     * 有効範囲を複数指定して動画を切り取ってファイルに書き出す。
     * @param outPath 出力先
     * @param ranges 有効範囲
     * @return Result
     */
    suspend fun trim(outPath: IOutputMediaFile, vararg ranges: RangeMs):Result {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            if (ranges.isEmpty()) return@withContext Result.error(IllegalArgumentException("ranges is empty"))
            val metaData = MetaData.fromFile(inPath)
            durationMs = metaData.duration ?: Long.MAX_VALUE
            progress.setTotalMs( ranges.fold(0L) { acc, range ->
                if (acc<0) return@fold -1
                val actualEnd = if (range.endMs<0||range.endMs==Long.MAX_VALUE) durationMs else range.endMs
                if (actualEnd == Long.MAX_VALUE) -1
                else acc + (actualEnd - range.startMs)
            })
            try {
                extractRangesToFile(metaData, outPath, *(ranges.map { RangeUs.fromMs(it) }.toTypedArray()))
                Result.success
            } catch (e:Throwable) {
                if (e !is CancellationException) {
                    logger.error(e)
                }
                if (deleteOutputOnError) {
                    runCatching { outPath.delete() }
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
    suspend fun chop(out1Path: IOutputMediaFile, out2Path: IOutputMediaFile, atTimeMs:Long) : Result {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            if (atTimeMs<=0L) return@withContext Result.error(IllegalArgumentException("invalid time: $atTimeMs"))
            val metaData = MetaData.fromFile(inPath)
            durationMs = metaData.duration ?: Long.MAX_VALUE
            try {
                progress.setTotalMs( metaData.duration?:-1L )
                extractRangesToFile(metaData, out1Path, RangeUs.fromMs(0L,atTimeMs))
                extractRangesToFile(metaData, out2Path, RangeUs.fromMs(atTimeMs, Long.MAX_VALUE))
                Result.success
            } catch (e:Throwable) {
                if (e !is CancellationException) {
                    logger.error(e)
                }
                if (deleteOutputOnError) {
                    runCatching {
                        out1Path.delete()
                        out2Path.delete()
                    }
                }
                return@withContext Result.error(e)
            } finally {
                resetBuffer()
            }
        }
    }

    /**
     * 切り取り位置の補正
     * splitAtMs や startMs で指定した位置と実際に切り取られた位置（キーフレーム位置）は異なる可能性がある。
     */
    fun correctPositionMs(timeMs:Long):Long {
        return actualSoughtMap[timeMs*1000L]?.let { it/1000L  } ?: timeMs
    }

    fun correctPositionUs(timeUs:Long):Long {
        return actualSoughtMap[timeUs] ?: timeUs
    }

    /**
     * trim() に使った Array<RangeMs> を一括補正する。
     * trim()後の位置ではなく、元動画のどこで実際に分割したかを示す値を返す。
     * ConvertResult#adjustedTrimmingRangeList と型互換
     */
    fun adjustedRangeList(ranges:Array<RangeMs>) : ITrimmingRangeList {
        return TrimmingRangeListImpl().apply {
            for (range in ranges) {
                if (range.endMs > 0) {
                    addRange(correctPositionUs(range.startMs * 1000L), correctPositionUs(range.endMs * 1000L))
                } else {
                    addRange(correctPositionUs(range.startMs * 1000L), correctPositionUs(durationMs * 1000L))
                }
            }
        }
    }
}