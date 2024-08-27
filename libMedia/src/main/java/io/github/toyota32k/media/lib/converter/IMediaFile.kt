package io.github.toyota32k.media.lib.converter

import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import io.github.toyota32k.media.lib.format.ContainerFormat
import java.io.Closeable

//interface IMediaFile {
//}

open class CloseableObject<T>(
    val obj: T,
    private val closeable: Closeable?,
    private val releaseObj: (T) -> Unit
    ) : Closeable {

    override fun close() {
        closeable?.close()
        releaseObj(obj)
    }

    inline fun <R> useObj(block: (T) -> R):R {
        return this.use {
            block(obj)
        }
    }
}

class CloseableExtractor(extractor:MediaExtractor, closeable:Closeable?)
    : CloseableObject<MediaExtractor>(extractor, closeable, { it.release() })

class CloseableMediaMetadataRetriever(mediaMetadataRetriever: MediaMetadataRetriever, closeable:Closeable?)
    : CloseableObject<MediaMetadataRetriever>(mediaMetadataRetriever, closeable, { it.release() })

class CloseableMuxer(muxer: MediaMuxer, closeable:Closeable?)
    : CloseableObject<MediaMuxer>(muxer, closeable, { it.release() })

interface IMediaFile {
    fun getLength(): Long       // 不明の場合は -1L
}

interface IInputMediaFile : IMediaFile {
    val seekable: Boolean
    fun openExtractor(): CloseableExtractor
    fun openMetadataRetriever(): CloseableMediaMetadataRetriever
}

interface IOutputMediaFile : IMediaFile {
    fun openMuxer(format:ContainerFormat): CloseableMuxer
    fun delete()
}

