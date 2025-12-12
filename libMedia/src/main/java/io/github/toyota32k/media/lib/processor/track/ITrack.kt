package io.github.toyota32k.media.lib.processor.track

import android.media.MediaCodec
import android.media.MediaExtractor
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.processor.misc.RangeUs
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.track.Muxer
import java.io.Closeable
import java.nio.ByteBuffer

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
     * コンバート/トリミング完了
     */
    fun finalize()

//    /**
//     * 読み込み位置までシークする
//     * @param seekToUs  シーク先 (us)
//     * @return 実際にシークした位置 (us)
//     */
//    fun initialSeek(seekToUs:Long):Long

    /**
     * extractorから読み込んで、（decode/encodeして）muxerへ書き込む
     * @param rangeUs
     */
    fun readAndWrite(rangeUs: RangeUs) : Boolean

    /**
     * Report ... input summary
     */
    fun inputSummary(report: Report, metaData: MetaData)
    /**
     * Report ... output summary
     */
    fun outputSummary(report: Report)
}