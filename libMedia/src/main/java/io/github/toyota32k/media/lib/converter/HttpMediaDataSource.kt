package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.media.MediaDataSource
import androidx.annotation.WorkerThread
import io.github.toyota32k.utils.FlowableEvent
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * IHttpStreamSourceを入力とする HTTPベースの MediaDataSource の実装クラス
 */
class HttpMediaDataSource(context: Context, private val streamSource: IHttpStreamSource) : MediaDataSource() {
    companion object {
        private const val TEMP_FILE_PREFIX = "amp_"
        private const val TEMP_FILE_SUFFIX = ".tmp"

        /**
         * 一時ファイルを全クリア
         */
        fun deleteAllTempFile(context:Context) {
            val tempFiles = context.cacheDir.listFiles()?.filter { it.name.startsWith(TEMP_FILE_PREFIX) && it.name.endsWith(TEMP_FILE_SUFFIX) } ?: return
            Converter.logger.info("deleting ${tempFiles.size} temp files.")
            tempFiles.forEach {
                try {
                    it.delete()
                    Converter.logger.debug("deleted: ${it.name}")
                } catch (e:Throwable) {
                    Converter.logger.error(e)
                }
            }
        }
    }
    private val tempFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, context.cacheDir)
    private val logger = UtLog("HMD", Converter.logger)
    var contentLength: Long = -1
    private var receivedLength: Long = 0
    private var eos:Boolean = false
    private var error: Throwable? = null
    private val completed = FlowableEvent()

    /**
     * 必要バイト数のデータ読み込みを待ち合わせるための同期オブジェクト
     */
    private data class WaitingEvent(val requiredLength:Long) {
        private val event = FlowableEvent()
        private var actualLength:Long = -1
        suspend fun notify(length: Long, eos:Boolean) {
            if (length >= requiredLength||eos) {
                actualLength = length
                event.set()
            }
        }
        suspend fun error() {
            event.set()
        }
        suspend fun wait():Long {
            event.waitOne()
            return actualLength
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

    /**
     * Java の　InputStream の EOSは、0ではなく-1らしい
     * @param length    -1: EOS
     */
    private suspend fun onReceived(length: Long) {
        val total:Long
        val eos:Boolean
        val events = synchronized(this) {
            if(length<0L) {
                logger.debug("reached to eos ($receivedLength / $contentLength)")
                this.eos = true
            } else {
                receivedLength += length
//                    logger.debug("received $length ($receivedLength / $contentLength)")
            }
            eos = this.eos
            total = receivedLength
            waitingEvents.toList()
        }
//        logger.debug("onReceived $length ($total")
        events.forEach {
            it.notify(total,eos)
        }
    }

    private fun startLoading() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                tempFile.outputStream().use { output ->
                    streamSource.open().use { input ->
                        contentLength = streamSource.length
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            onReceived(bytes.toLong())
                            bytes = input.read(buffer)
                        }
                        onReceived(-1L)  // eos
                        output.flush()
                    }
                }
            } catch (e: Throwable) {
                logger.error(e)
                onError(e)
            } finally {
                streamSource.close()
                completed.set()
                logger.debug("completed.")
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
            if (receivedLength >= length) {
//                    logger.debug("already received $receivedLength")
                return max(0L, length - position)
            } else if (eos) {
                logger.debug("already reached to eos")
                return max(0L,receivedLength - position)
            } else {
                WaitingEvent(length).apply {
                    waitingEvents.add(this)
                }
            }
        }
        val actualLength = runBlocking { awaiter.wait() }
        checkError()
        return max(0, actualLength - position)
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
//            logger.debug("readAt $position $size")
        val length = waitFor(position, size.toLong())
        checkError()
        if(length<=0) {
            logger.debug("no more data.")
            return 0
        }
        return tempFile.inputStream().use { input ->
            input.skip(position)
            input.read(buffer, offset, min(size,length.toInt()))
        }
    }

    @WorkerThread
    override fun getSize(): Long {
        val awaiter = synchronized(this) {
            checkError()
            if(contentLength > 0) {
                return contentLength
            } else {
                WaitingEvent(0).apply {
                    waitingEvents.add(this)
                }
            }
        }
        runBlocking { awaiter.wait() }
        checkError()
        return contentLength
    }
}