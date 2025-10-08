package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import io.github.toyota32k.media.lib.converter.Converter.Factory.RangeMs
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.misc.ISO6709LocationParser
import io.github.toyota32k.media.lib.track.Muxer.Companion.logger
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
class Splitter(
    val inPath:IInputMediaFile,
    private val deleteOutputOnError:Boolean = true,
    private val rotation: Rotation? = null,
    onProgress : ((IProgress)->Unit)? = null
    ) {
    private val progress = Progress(onProgress)
    private val actualSoughtMap = mutableMapOf<Long,Long>()


    class Factory(inPath:IInputMediaFile?=null, splitAtMs:Long=-1L) {
        private var mInPath: IInputMediaFile? = inPath
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

        // endregion

        fun build(): Splitter {
            val inPath = mInPath ?: throw IllegalStateException("input file is not specified.")
            return Splitter(
                inPath = inPath,
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

    class RangeUs(val start:Long, val end:Long) {
        companion object {
            fun fromMs(startMs:Long, endMs:Long) : RangeUs =
                if (endMs<=0 || endMs==Long.MAX_VALUE) RangeUs(startMs*1000L, Long.MAX_VALUE)
                else RangeUs(startMs*1000L, endMs*1000L)
            fun fromMs(rangeMs:RangeMs) : RangeUs =
                fromMs(rangeMs.startMs, rangeMs.endMs)
        }
    }

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

        init {
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
                this.muxer = muxer
                muxerIndex = muxer.addTrack(extractor.getTrackFormat(trackIndex))
            }
        }

        fun initialSeek(us:Long):Long {
            return if (isAvailable) {
                done = false
                extractor.seekTo(us, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                extractor.sampleTime
            } else -1
        }

        fun readAndWrite(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo, rangeUs:RangeUs) : Long {
            if (!isAvailable) return 0L   // トラックが存在しない場合は常にEOSとして扱う
            val startTime = sampleTimeUs
            var handled:Long = 0L
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
                    handled = sampleTimeUs - startTime
                    presentationTimeUs += handled
                }
            }
            return handled
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

    private fun extractRange(videoTrack:TrackInfo, audioTrack:TrackInfo, rangeUs:RangeUs, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val posVideo = videoTrack.initialSeek(rangeUs.start)
        audioTrack.initialSeek(if(posVideo>=0) posVideo else rangeUs.start)
        while (!videoTrack.done || !audioTrack.done) {
            if (isCancelled) {
                throw CancellationException()
            }
            if (!videoTrack.done && (audioTrack.done || videoTrack.sampleTimeUs < audioTrack.sampleTimeUs)) {
                // video track を処理
                val dealt = videoTrack.readAndWrite(buffer, bufferInfo, rangeUs)
                progress.updateVideo(dealt)
            } else {
                // audio track を処理
                val dealt = audioTrack.readAndWrite(buffer, bufferInfo, rangeUs)
                progress.updateAudio(dealt)
            }
        }
        actualSoughtMap[rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
    }


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

            // Extractorから読み上げてMuxerへ書き込む
            // バッファを確保
            val bufferSize = 1 * 1024 * 1024 // 1MB
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // 要求された範囲を取り出す
            for (rangeUs in rangesUs) {
                extractRange(videoTrack, audioTrack, rangeUs, buffer, bufferInfo)
            }
            // ファイナライズ
            muxer.stop()
        }
    }

    data class Result(val succeeded:Boolean, val error:Throwable?) {
        val cancelled : Boolean get() = error is CancellationException
        val hasError: Boolean get() = error != null && !cancelled
        companion object {
            val cancelled = Result(false, CancellationException())
            val success = Result(true, null)
            fun error(e:Throwable) = Result(false, e)
        }
    }

    class Progress(val onProgress:((IProgress)->Unit)?) : IProgress {
        private val startTick = System.currentTimeMillis()
        private var videoLength = 0L
        private var audioLength = 0L

        override var total: Long = 0L
        override val current: Long
            get() = min(videoLength, audioLength)
        override var remainingTime: Long = -1L
            private set

        fun updateVideo(video:Long) {
            val prev = current
            videoLength += video
            if (prev!=current && total>0) {
                if (percentage>10) {
                    remainingTime = (System.currentTimeMillis() - startTick) * (100 - percentage) / percentage
                }
                onProgress?.invoke(this)
            }
        }

        fun updateAudio(audio:Long) {
            val prev = current
            audioLength += audio
            if (prev!=current) {
                if (percentage>10) {
                    remainingTime = (System.currentTimeMillis() - startTick) * (100 - percentage) / percentage
                }
                onProgress?.invoke(this)
            }
        }
    }

    suspend fun trim(outPath: IOutputMediaFile, startMs: Long, endMs:Long): Result {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            val metaData = MetaData.fromFile(inPath)
            val actualEndMs = if (endMs>=0) endMs else metaData.duration?:Long.MAX_VALUE
            if (startMs<0 || actualEndMs<=startMs) return@withContext Result.error(IllegalArgumentException("invalid range: $startMs-$actualEndMs"))
            try {
                progress.total = if (actualEndMs==Long.MAX_VALUE) -1L else actualEndMs-startMs
                extractRangesToFile(metaData, outPath, RangeUs.fromMs(startMs, actualEndMs))
                Result.success
            } catch (e:Throwable) {
                if (deleteOutputOnError) {
                    runCatching { outPath.delete() }
                }
                Result.error(e)
            }
        }

    }

    suspend fun trim(outPath: IOutputMediaFile, vararg ranges: RangeMs):Result {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            if (ranges.isEmpty()) return@withContext Result.error(IllegalArgumentException("ranges is empty"))
            val metaData = MetaData.fromFile(inPath)
            val duration = metaData.duration ?: Long.MAX_VALUE
            progress.total = ranges.fold(0L) { acc, range ->
                if (acc<0) return@fold -1
                val actualEnd = if (range.endMs<0||range.endMs==Long.MAX_VALUE) duration else range.endMs
                if (actualEnd == Long.MAX_VALUE) -1
                else acc + (actualEnd - range.startMs)
            }
            try {
                extractRangesToFile(metaData, outPath, *(ranges.map { RangeUs.fromMs(it) }.toTypedArray()))
                Result.success
            } catch (e:Throwable) {
                if (deleteOutputOnError) {
                    runCatching { outPath.delete() }
                }
                Result.error(e)
            }
        }

    }

    suspend fun chop(out1Path: IOutputMediaFile, out2Path: IOutputMediaFile, atTimeMs:Long) : Result {
        actualSoughtMap.clear()
        isCancelled = false
        return withContext(Dispatchers.IO) {
            if (atTimeMs<=0L) return@withContext Result.error(IllegalArgumentException("invalid time: $atTimeMs"))
            val metaData = MetaData.fromFile(inPath)
            try {
                progress.total = metaData.duration?:-1L
                extractRangesToFile(metaData, out1Path, RangeUs.fromMs(0L,atTimeMs))
                extractRangesToFile(metaData, out2Path, RangeUs.fromMs(atTimeMs, Long.MAX_VALUE))
                Result.success
            } catch (e:Throwable) {
                if (deleteOutputOnError) {
                    runCatching {
                        out1Path.delete()
                        out2Path.delete()
                    }
                }
                return@withContext Result.error(e)
            }
        }
    }

    fun correctPosition(timeUs:Long):Long {
        return actualSoughtMap[timeUs] ?: timeUs
    }

}