package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.GenericCloseable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTPサーバーからのデータ読み込み処理を抽象化する i/f定義
 * OkHttpが使いたい、というような場合は、これを実装して、HttpInputFile に渡す。
 */
interface IHttpStreamSource {
    val length:Long
    fun open(): InputStream
    fun close()
}

/**
 * HTTP サーバー上のメディアファイルをソースとしてトランスコードを実行するため IInputMediaFile実装クラス
 */
class HttpInputFile(context: Context, private val streamSource: IHttpStreamSource) : IInputMediaFile {
    constructor(context: Context, url:String) : this(context, HttpStreamSource(url))

    companion object {
        /**
         * 一時ファイルの全クリア
         * 強制終了したりすると、一時ファイルがゴミとして残ることがあるので、ときどき呼ぶとよいかも。
         */
        @Suppress("unused")
        fun deleteAllTempFile(context:Context) {
            HttpMediaDataSource.deleteAllTempFile(context)
        }
    }


    private val context = context.applicationContext

    /**
     * java.net.HttpURLConnection を使った IHttpStreamSource の実装クラス
     */
    class HttpStreamSource(private val url:String): IHttpStreamSource {
        override var length: Long = -1
        private var connection: HttpURLConnection? = null
        private var inputStream: InputStream? = null

        override fun open(): InputStream {
            return (URL(url).openConnection() as HttpURLConnection).run {
                connect()
                connection = this
                length = contentLengthLong
                inputStream.apply { this@HttpStreamSource.inputStream = this }
            }
        }

        override fun close() {
            inputStream?.close()    // このcloseが必要なのかどうか不明だが念のため。
            inputStream = null
            connection?.disconnect()
            connection = null
        }
    }

    override val seekable: Boolean = true
    private var refCount  = 0
    private var dataSource: HttpMediaDataSource? = null
    private val logger = UtLog("HIF", Converter.logger)
    private var length: Long = -1

    /**
     * HttpMediaDataSource を準備する
     */
    private fun prepare(): HttpMediaDataSource {
        return synchronized(this) {
            if (dataSource == null) {
                dataSource = HttpMediaDataSource(context, streamSource)
                length = dataSource!!.getSize()
            }
            refCount++
            dataSource!!
        }
    }

    /**
     * 参照カウンタを１つ上げる
     */
    @Suppress("unused")
    fun addRef() {
        prepare()
    }

    /**
     * 参照カウンタを下げる
     */
    fun release() {
        synchronized(this) {
            if(refCount>0) {
                refCount--
                if (refCount == 0) {
//                    length = dataSource?.getSize() ?: -1
                    dataSource?.dispose()
                    dataSource = null
                }
            } else {
                logger.assert(false, "refCount is already zero")
            }
        }
    }

    override fun openExtractor(): CloseableExtractor {
        val extractor = MediaExtractor().apply { setDataSource(prepare())}
        return CloseableExtractor(extractor, GenericCloseable { release() })
    }

    override fun openMetadataRetriever(): CloseableMediaMetadataRetriever {
        val retriever = MediaMetadataRetriever().apply { setDataSource(prepare())}
        return CloseableMediaMetadataRetriever(retriever, GenericCloseable { release() })
    }

    /**
     * データサイズをベストエフォートで(w)取得
     */
    override fun getLength(): Long {
        return synchronized(this) {
            if(length>=0) {
                length
            } else {
                dataSource?.contentLength ?: -1L
            }
        }
    }

}