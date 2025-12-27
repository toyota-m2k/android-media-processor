package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.processor.track.SyncMuxer
import io.github.toyota32k.media.lib.types.RangeUs
import java.io.Closeable

interface ITrack : Closeable {
    /**
     * トラックはあるか？
     * 音声トラックがない場合を想定
     */
    val isAvailable: Boolean

    /**
     * 処理済みか？
     * EOSに達していれば true
     */
    val done: Boolean

    /**
     * 処理済みの積算再生時間 (us)
     */
    val presentationTimeUs: Long

    /**
     * trackをMuxerに接続して処理を開始する
     */
    fun setup(muxer: SyncMuxer)

    /**
     * 範囲の先頭位置までシークして、範囲読み込みを開始する。
     * @param seekToUs  シーク先 (us)
     * @return 実際にシークした位置 (us)
     */
    fun startRange(startFromUS:Long):Long

    /**
     * 範囲読み込みを終了する。
     */
    fun endRange()

    /**
     * extractorから読み込んで、（decode/encodeして）muxerへ書き込む
     * @param rangeUs
     */
    fun readAndWrite(rangeUs: RangeUs) : Boolean

    /**
     * コンバート/トリミング完了
     */
    fun finalize()
}