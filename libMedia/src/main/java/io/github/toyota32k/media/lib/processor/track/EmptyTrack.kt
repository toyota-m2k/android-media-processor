package io.github.toyota32k.media.lib.processor.track

import io.github.toyota32k.media.lib.processor.contract.ITrack
import io.github.toyota32k.media.lib.types.RangeUs

/**
 * 何もしないITrack
 * PresetAudioStrategy.NoAudioで、音声トラックを削除するために使用。
 */
object EmptyTrack : ITrack {
    override val isAvailable: Boolean = false
    override val done: Boolean = true
    override val presentationTimeUs: Long = 0L
    override var currentRangeStartPresentationTimeUs: Long = 0L
    override fun setup(muxer: SyncMuxer) {}
    override fun startRange(startFromUS: Long): Long = startFromUS
    override fun endRange() {}
    override fun finalize() {}
    override fun readAndWrite(rangeUs: RangeUs): Boolean = false
    override fun close() {}
}