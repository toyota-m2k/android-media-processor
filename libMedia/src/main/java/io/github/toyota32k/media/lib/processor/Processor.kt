package io.github.toyota32k.media.lib.processor

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.ActualSoughtMapImpl
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.IOutputMediaFile
import io.github.toyota32k.media.lib.converter.IProgress
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.processor.misc.RangeUs
import io.github.toyota32k.media.lib.processor.misc.RangeUs.Companion.isValidTime
import io.github.toyota32k.media.lib.processor.misc.RangeUs.Companion.totalLengthUs
import io.github.toyota32k.media.lib.processor.misc.RangeUs.Companion.us2ms
import io.github.toyota32k.media.lib.processor.track.ITrack
import io.github.toyota32k.media.lib.processor.track.SyncMuxer
import io.github.toyota32k.media.lib.processor.track.TrackSelector
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.surface.RenderOption
import java.io.Closeable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

class Processor(
//    private val rotation: Rotation?,
//    private val renderOption: RenderOption = RenderOption.DEFAULT,
    val containerFormat: ContainerFormat = ContainerFormat.MPEG_4,
    val bufferSize:Int = DEFAULT_BUFFER_SIZE,
    val onProgress : ((IProgress)->Unit)?,
    val videoStrategy: IVideoStrategy,
    val audioStrategy: IAudioStrategy,
) : ICancellable {
    companion object {
        val logger = UtLog("Processor", Converter.logger, this::class.java)
        const val DEFAULT_BUFFER_SIZE:Int = 8 * 1024 * 1024     // 8MB ... 1MB だと extractor.readSampleData() で InvalidArgumentException が発生
    }

    class Builder {
        companion object {
            fun fromInstance(instance:Processor) = Builder().apply {
                mContainerFormat = instance.containerFormat
                mBufferSize = instance.bufferSize
                mOnProgress = instance.onProgress
                mVideoStrategy = instance.videoStrategy
                mAudioStrategy = instance.audioStrategy
            }
        }
        private var mContainerFormat: ContainerFormat = ContainerFormat.MPEG_4
        private var mBufferSize:Int = DEFAULT_BUFFER_SIZE
        private var mOnProgress : ((IProgress)->Unit)? = null
        private var mVideoStrategy: IVideoStrategy = PresetVideoStrategies.InvalidStrategy
        private var mAudioStrategy: IAudioStrategy = PresetAudioStrategies.InvalidStrategy

        fun containerFormat(containerFormat: ContainerFormat) = apply {
            mContainerFormat = containerFormat
        }
        fun bufferSize(sizeInBytes:Int) = apply {
            mBufferSize = sizeInBytes.coerceAtLeast(DEFAULT_BUFFER_SIZE)
        }
        fun onProgress(progress:((IProgress)->Unit)?) = apply {
            mOnProgress = progress
        }
        fun videoStrategy(strategy: IVideoStrategy) = apply {
            mVideoStrategy = strategy
        }
        fun audioStrategy(strategy: IAudioStrategy) = apply {
            mAudioStrategy = strategy
        }
        fun build():Processor {
            return Processor(
//                rotation = mRotation,
//                renderOption = mRenderOption,
                containerFormat = mContainerFormat,
                bufferSize = mBufferSize,
                onProgress = mOnProgress,
                videoStrategy = mVideoStrategy,
                audioStrategy = mAudioStrategy,
            )
        }

    }


    // region Utility Classes

    /**
     * Closeable をまとめて解放できるようにするクラス
     */
    private class Closeables : Closeable {
        private val list = mutableListOf<Closeable>()
        fun <T: Closeable> add(c:T): T {
            list.add(c)
            return c
        }
        override fun close() {
            list.forEach { it.close() }
        }
    }

    // endregion

    // region Progress

    /**
     * 進捗報告用ハンドラクラス
     */
    private class ProgressHandler(private val onProgress:((IProgress)->Unit)?) : IProgress {
        private val startTick = System.currentTimeMillis()
        private var videoLength = 0L        // ms
        private var audioLength = 0L        // ms

        private var videoAvailable = false
        private var audioAvailable = false

        override var total: Long = 0L       // ms
            private set
        override val current: Long
            get() = min(if(videoAvailable) videoLength else Long.MAX_VALUE, if(audioAvailable) audioLength else Long.MAX_VALUE)
        override var remainingTime: Long = -1L
            private set

        fun initialize(totalUs:Long, video:Boolean, audio:Boolean) {
            total = totalUs.us2ms()
            videoLength = 0L
            audioLength = 0L
            videoAvailable = video
            audioAvailable = audio
        }

        private fun updateRemainingTime() {
            if (percentage>10) {
                remainingTime = (System.currentTimeMillis() - startTick) * (100 - percentage) / percentage
            }
        }

        fun updateVideoUs(videoUs:Long) {
            val prev = current
            videoLength = videoUs.us2ms()
            if (prev!=current && total>0 && total!=Long.MAX_VALUE) {
                updateRemainingTime()
                onProgress?.invoke(this)
            }
        }

        fun updateAudioUs(audioUs:Long) {
            val prev = current
            audioLength = audioUs.us2ms()
            if (prev!=current) {
                updateRemainingTime()
                onProgress?.invoke(this)
            }
        }
    }

    // 進捗報告
    private val progress = ProgressHandler(onProgress)

    // endregion


    // キャンセル
    private var isCancelled: Boolean = false
    override fun cancel() {
        isCancelled = true
    }

//    /**
//     * Muxerに付加情報（Rotation、Location)を設定する
//     */
//    private fun setupMuxer(muxer: MediaMuxer, metaData: MetaData, rotation: Rotation?) {
//        val metaRotation = metaData.rotation
//        if (metaRotation != null) {
//            val r = rotation?.rotate(metaRotation) ?: metaRotation
//            muxer.setOrientationHint(r)
//            logger.info("metadata: rotation=$metaRotation --> $r")
//        } else if(rotation!=null){
//            muxer.setOrientationHint(rotation.rotate(0))
//        }
//        val locationString = metaData.location
//        if (locationString != null) {
//            val location: FloatArray? = ISO6709LocationParser.parse(locationString)
//            if (location != null) {
//                muxer.setLocation(location[0], location[1])
//                logger.info("metadata: latitude=${location[0]}, longitude=${location[1]}")
//            } else {
//                Companion.logger.error("metadata: failed to parse the location metadata: $locationString")
//            }
//        }
//    }

    /**
     * 指定範囲をextractorから読み出してmuxerに書き込む
     */
    private fun extractRange(videoTrack: ITrack, audioTrack: ITrack, rangeUs: RangeUs, actualSoughtMap: ActualSoughtMapImpl) {
        val posVideo = videoTrack.startRange(rangeUs.startUs)
        audioTrack.startRange(if(posVideo>=0) posVideo else rangeUs.startUs)
        while (!videoTrack.done || !audioTrack.done) {
            if (isCancelled) {
                throw CancellationException()
            }
            if (!videoTrack.done && (audioTrack.done || videoTrack.presentationTimeUs <= audioTrack.presentationTimeUs)) {
                // video track を処理
                videoTrack.readAndWrite(rangeUs)
                progress.updateVideoUs(videoTrack.presentationTimeUs)
            } else if (!audioTrack.done) {
                // audio track を処理
                val dealt = audioTrack.readAndWrite(rangeUs)
                progress.updateAudioUs(audioTrack.presentationTimeUs)
            }
        }
        videoTrack.endRange()
        audioTrack.endRange()
        actualSoughtMap.addPosition(rangeUs.startUs, posVideo) // [rangeUs.start] = if(posVideo>=0) posVideo else rangeUs.start
    }



    /**
     * 指定範囲をファイルに出力
     * @param inPath 入力ファイル
     * @param outPath 出力ファイル
     * @param rangesUs 出力する範囲のリスト
     * @param limitDurationUs 最大動画長 (us) / <=0 または、Long.MAX_VALUE を指定すると、rangesUsの最後までコンバートする。
     * @param rotation 回転
     * @param renderOption RenderOption
     */
    fun trimming(inPath: IInputMediaFile, outPath: IOutputMediaFile, rangesUs:List<RangeUs>, limitDurationUs:Long, rotation:Rotation?, renderOption:RenderOption?, actualSoughtMapImpl: ActualSoughtMapImpl, report: Report) {
        isCancelled = false
        Closeables().use { closer ->
            // Extractorの準備
            val trackSelector = TrackSelector(inPath, limitDurationUs, report, bufferSize, videoStrategy, audioStrategy).apply { closer.add(this) }
            val videoTrack = trackSelector.openVideoTrack(renderOption?:RenderOption.DEFAULT).apply { closer.add(this) }
            val audioTrack = trackSelector.openAudioTrack().apply { closer.add(this) }
            if (!videoTrack.isAvailable && !audioTrack.isAvailable) throw IllegalStateException("no track available")

            // Muxerの準備
            val muxer = SyncMuxer(outPath, containerFormat, videoTrack.isAvailable, audioTrack.isAvailable).apply {
                setup(trackSelector.inputMetaData, rotation)
                closer.add(this)
            }
            videoTrack.setup(muxer)
            audioTrack.setup(muxer)

            // Progress情報を初期化
            val totalUs = rangesUs.totalLengthUs(trackSelector.inputMetaData.durationUs ?: Long.MAX_VALUE)
            progress.initialize(if (limitDurationUs>0) min(limitDurationUs, totalUs) else totalUs, videoTrack.isAvailable, audioTrack.isAvailable)

            // Extractorから要求された範囲を読み上げてMuxerへ書き込む
            for (rangeUs in rangesUs) {
                if (isCancelled) throw CancellationException()
                extractRange(videoTrack, audioTrack, rangeUs, actualSoughtMapImpl)
            }
            // ファイナライズ
            videoTrack.finalize()
            audioTrack.finalize()
            muxer.stop()
            report.updateOutputFileInfo(outPath.getLength(), progress.current)
        }
    }
}