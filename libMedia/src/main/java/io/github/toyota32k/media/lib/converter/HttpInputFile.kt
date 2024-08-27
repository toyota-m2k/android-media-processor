package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import androidx.annotation.WorkerThread
import io.github.toyota32k.utils.FlowableEvent
import io.github.toyota32k.utils.GenericCloseable
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
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

    private val context = context.applicationContext

    /**
     * java.net.HttpURLConnection を使った IHttpStreamSource の実装クラス
     */
    class HttpStreamSource(private val url:String): IHttpStreamSource {
        override var length: Long = -1
        private var connection: HttpURLConnection? = null


        override fun open(): InputStream {
            return (URL(url).openConnection() as HttpURLConnection).run {
                connect()
                connection = this
                length = contentLengthLong
                inputStream
            }
        }

        override fun close() {
            connection?.disconnect()
            connection = null
        }
    }

    /**
     * IHttpStreamSourceを入力とする HTTPベースの MediaDataSource の実装クラス
     */
    private class HttpMediaDataSource(context: Context, private val streamSource: IHttpStreamSource) : MediaDataSource() {
        private val tempFile = File.createTempFile("amp_", ".tmp", context.cacheDir)
        private val logger = UtLog("HMD", Converter.logger)
        var totalLength: Long = -1
        private var currentLength: Long = -1
        private var error: Throwable? = null
        private val completed = FlowableEvent()

        private data class WaitingEvent(val requiredLength:Long) {
            private val event = FlowableEvent()
            suspend fun notify(length: Long) {
                if (length >= requiredLength) {
                    event.set()
                }
            }
            suspend fun error() {
                event.set()
            }
            suspend fun wait() {
                event.waitOne()
            }
        }
        private val waitingEvents = mutableListOf<WaitingEvent>()

        init {
            startLoading()
        }

        private suspend fun onError(e:Throwable) {
            val events = synchronized(this) {
                error = e
                waitingEvents.toList()
            }
            events.forEach {
                it.error()
            }
        }
        private suspend fun onReceived(length: Long) {
            val total:Long
            val events = synchronized(this) {
                currentLength += length
                total = currentLength
                waitingEvents.toList()
            }
//        logger.debug("onReceived $length ($total")
            events.forEach {
                it.notify(total)
            }
        }

        private fun startLoading() {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    tempFile.outputStream().use { output ->
                        streamSource.open().use { input ->
                            totalLength = streamSource.length
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var bytes = input.read(buffer)
                            while (bytes > 0) {
                                output.write(buffer, 0, bytes)
                                bytes = input.read(buffer)
                                onReceived(bytes.toLong())
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logger.error(e)
                    onError(e)
                } finally {
                    streamSource.close()
                    completed.set()
                }
            }
        }

        private fun checkError() {
            synchronized(this) {
                if (error != null) {
                    throw error!!
                }
            }
        }

        private fun waitFor(position:Long, size: Long) : Long {
            val total = getSize()
            val length = if (total < position+size) {
                total
            } else {
                position+size
            }
            val awaiter = synchronized(this) {
                checkError()
                if (currentLength >= length) {
                    return length - position
                } else {
                    WaitingEvent(length).apply {
                        waitingEvents.add(this)
                    }
                }
            }
            runBlocking { awaiter.wait() }
            checkError()
            return length - position
        }

        override fun close() {
            logger.debug()
        }

        fun dispose() {
            streamSource.close()
            runBlocking { completed.waitOne() }
            try {
                tempFile.delete()
            } catch (e: Throwable) {
                logger.error(e)
            }
        }

        @WorkerThread
        override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
            logger.debug("readAt $position $size")
            val length = waitFor(position, size.toLong())
            checkError()
            return tempFile.inputStream().use { input ->
                input.skip(position)
                input.read(buffer, offset, length.toInt())
            }
        }

        @WorkerThread
        override fun getSize(): Long {
            val awaiter = synchronized(this) {
                checkError()
                if(totalLength > 0) {
                    return totalLength
                } else {
                    WaitingEvent(0).apply {
                        waitingEvents.add(this)
                    }
                }
            }
            runBlocking { awaiter.wait() }
            checkError()
            return totalLength
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
                    length = dataSource?.getSize() ?: -1
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
                dataSource?.totalLength ?: -1L
            }
        }
    }

}