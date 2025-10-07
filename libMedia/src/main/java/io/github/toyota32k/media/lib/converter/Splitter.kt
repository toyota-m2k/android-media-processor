package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.getDuration
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.misc.ISO6709LocationParser
import io.github.toyota32k.media.lib.track.Muxer.Companion.logger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

interface ISplitProgress : IProgress {
    val progressFirst: Boolean
}
/**
 * 元の形式のまま（デコード・再エンコードしないで）動画ファイルを分割する。
 */
class Splitter(
    val inPath:IInputMediaFile,
    val splitAtUs: Long,
    val out1Path: IOutputMediaFile,
    val out2Path: IOutputMediaFile? = null,
    private val deleteOutputOnError:Boolean = true,
    private val rotation: Rotation? = null,
    onProgress : ((ISplitProgress)->Unit)? = null
    ) {
    private val progress = Progress(onProgress)


    class Factory(inPath:IInputMediaFile?=null, splitAtMs:Long=-1L) {
        private var mSplitAtMs: Long = splitAtMs
        private var mInPath: IInputMediaFile? = inPath
        private var mOut1File: IOutputMediaFile? = null
        private var mOut2File: IOutputMediaFile? = null
        private var mDeleteOutputOnError:Boolean = true
        private var mRotation: Rotation? = null
        private var mOnProgress : ((IProgress)->Unit)? = null

        // region inPath

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

        // endregion

        // region outPath

        /**
         * 入力ファイルを設定
         * @param IHttpStreamSource
         * @param Context
         */
        fun input(source: IHttpStreamSource, context: Context)
            = input(HttpInputFile(context, source))

        /**
         * 前半出力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * ただし、inputと異なり、HttpFileは利用不可
         * @param IOutputMediaFile
         */
        fun outputFirstHalf(dst :IOutputMediaFile) = apply {
            mOut1File = dst
        }

        /**
         * 前半出力ファイルを設定
         * @param File
         */
        fun outputFirstHalf(path: File)
            = outputFirstHalf(io.github.toyota32k.media.lib.converter.AndroidFile(path))

        /**
         * 前半出力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun outputFirstHalf(uri: Uri, context: Context)
            = outputFirstHalf(AndroidFile(uri, context))

        /**
         * 後半出力ファイルを設定（オプショナル）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * ただし、inputと異なり、HttpFileは利用不可
         * @param IOutputMediaFile
         */
        fun outputLastHalf(dst :IOutputMediaFile) = apply {
            mOut2File = dst
        }

        /**
         * 後半出力ファイルを設定
         * @param File
         */
        fun outputLastHalf(path: File)
            = outputFirstHalf(io.github.toyota32k.media.lib.converter.AndroidFile(path))

        /**
         * 後半出力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun outputLastHalf(uri: Uri, context: Context)
            = outputFirstHalf(AndroidFile(uri, context))

        // endregion

        // region Misc

        fun splitAtMs(ms:Long) = apply {
            mSplitAtMs = ms
        }

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

        // endregion

        fun build(): Splitter {
            val inPath = mInPath ?: throw IllegalStateException("input file is not specified.")
            val out1Path = mOut1File ?: throw IllegalStateException("output file is not specified.")
            if (mSplitAtMs < 0) throw IllegalStateException("split time is not specified.")
            return Splitter(
                inPath = inPath,
                splitAtUs = mSplitAtMs*1000L,
                out1Path = out1Path,
                out2Path = mOut2File,
                deleteOutputOnError = mDeleteOutputOnError,
                rotation = mRotation,
                onProgress = mOnProgress
            )
        }
    }

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

    class TrackInfo(inPath:IInputMediaFile, video:Boolean, val startTimeUs:Long, val endTimeUs:Long):Closeable {
        private val rawExtractor = inPath.openExtractor()
        val extractor = rawExtractor.obj
        val trackIndex:Int = findTrackIdx(video)
        var muxerIndex:Int = -1
        val isAvailable: Boolean get() = trackIndex>=0
        var done:Boolean = if (isAvailable) false else true
            private set
        val sampleTimeUs:Long get() = if (isAvailable) extractor.sampleTime else Long.MAX_VALUE
        lateinit var muxer: MediaMuxer

        init {
            if (isAvailable) {
                extractor.selectTrack(trackIndex)
                extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
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
                this.muxer = muxer
                muxerIndex = muxer.addTrack(extractor.getTrackFormat(trackIndex))
            }
        }

        fun readAndWrite(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) : Boolean {
            if (!isAvailable) return true   // トラックが存在しない場合は常にEOSとして扱う
            if (sampleTimeUs == -1L || (endTimeUs in 1..<sampleTimeUs)) {
                done = true
            } else {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    done = true
                } else {
                    bufferInfo.presentationTimeUs = sampleTimeUs - startTimeUs
                    bufferInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(muxerIndex, buffer, bufferInfo)
                    extractor.advance()
                }
            }
            return done

        }

        override fun close() {
            extractor.release()
        }
    }

    private var isCancelled: Boolean = false
    fun cancel() {
        isCancelled = true
    }

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


    private fun extract(inputMetaData:MetaData, startTimeUs:Long, endTimeUs:Long, outPath: IOutputMediaFile) {
        if (isCancelled) throw CancellationException("not started")
        Closeables().use { closer ->
            // Extractorの準備
            val videoTrack = closer.add(TrackInfo(inPath, video = true, startTimeUs, endTimeUs))
            val audioTrack = closer.add(TrackInfo(inPath, video = false, startTimeUs, endTimeUs))
            if (!videoTrack.isAvailable || !audioTrack.isAvailable) throw IllegalStateException("no track available")
            // Muxerの準備
            val muxer = closer.add(outPath.openMuxer(ContainerFormat.MPEG_4)).obj.apply {
                setupMuxer(this, inputMetaData, rotation)
            }
            videoTrack.attachMuxer(muxer)
            audioTrack.attachMuxer(muxer)
            muxer.start()

            // Extractorから読み上げてMuxerへ書き込む
            val bufferSize = 1 * 1024 * 1024 // 1MB
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            val startTick = System.currentTimeMillis()
            while (!videoTrack.done || !audioTrack.done) {
                if (isCancelled) {
                    throw CancellationException()
                }
                if (!videoTrack.done && (audioTrack.done || videoTrack.sampleTimeUs < audioTrack.sampleTimeUs)) {
                    // video track を処理
                    videoTrack.readAndWrite(buffer, bufferInfo)
                } else {
                    // audio track を処理
                    audioTrack.readAndWrite(buffer, bufferInfo)
                }
                if (progress.percentage>10) {
                    val spent = System.currentTimeMillis() - startTick
                    val remaining = spent * 100 / progress.percentage - spent
                    progress.remainingTime = remaining
                }
                progress.current = max(videoTrack.sampleTimeUs, audioTrack.sampleTimeUs)
            }
            muxer.stop()
        }
    }

    data class SingleResult(val succeeded:Boolean, val error:Throwable?) {
        val isCancelled : Boolean get() = error is CancellationException
        val isError: Boolean get() = error != null
        companion object {
            val cancelled = SingleResult(false, CancellationException())
            val success = SingleResult(true, null)
            fun error(e:Throwable) = SingleResult(false, e)
        }
    }
    data class Result(val first:SingleResult, val last:SingleResult?) {
        val isCancelled: Boolean get() = first.isCancelled || last?.isCancelled == true
        val isError: Boolean get() = first.isError || last?.isError == true
        val isSucceeded: Boolean get() = first.succeeded && last?.succeeded != false
    }

    class Progress(val onProgress:((ISplitProgress)->Unit)?) : ISplitProgress {
        override var total: Long = 0L
        override var current: Long = 0L
            set(v) { field = v; if (total>0L) { onProgress?.invoke(this) } }
        override var remainingTime: Long = -1L
        override var progressFirst: Boolean = true

        fun setPart(first:Boolean, partDuration:Long) {
            total = 0L
            current = 0L
            remainingTime = -1L
            progressFirst = first
            total = partDuration
            onProgress?.invoke(this)
        }

    }

    suspend fun split() : Result {
        isCancelled = false
        return withContext(Dispatchers.IO) {
            val metaData = MetaData.fromFile(inPath)
            try {
                progress.setPart(true, splitAtUs)
                extract(metaData, 0L, splitAtUs, out1Path)
            } catch (e:Throwable) {
                if (deleteOutputOnError) {
                    runCatching { out1Path.delete() }
                }
                return@withContext Result(SingleResult.error(e), null)
            }
            if (out2Path == null) return@withContext Result(SingleResult.success, null)
            try {
                val duration = (metaData.duration ?: 0) * 1000L
                progress.setPart(false, duration-splitAtUs)
                extract(metaData, splitAtUs, Long.MAX_VALUE, out2Path)
                Result(SingleResult.success, SingleResult.success)
            } catch (e:Throwable) {
                if (deleteOutputOnError) {
                    runCatching { out2Path.delete() }
                }
                Result(SingleResult.success, SingleResult.error(e))
            }
        }
    }
}