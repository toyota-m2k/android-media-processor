package io.github.toyota32k.media.lib.io

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.utils.android.IUtFileEx
import io.github.toyota32k.utils.android.UtFile
import io.github.toyota32k.utils.android.toUtFile
import java.io.File

/**
 * Converterの入・出力に利用できるファイルクラス
 *
 * Androidのファイルパス指定を抽象化
 * java.io.Fileによる指定と、android.net.Uri (+Context) による指定をサポート
 * ただし、Uriによる指定は、 android.provider.DocumentsProvider ベースのuri、すなわち、
 * Intent.ACTION_OPEN_DOCUMENTなどによって、取得されたuriであることを前提としている。
 */
class AndroidFile(val utFile:UtFile) : IInputMediaFile, IOutputMediaFile, IUtFileEx by utFile {
    constructor(file:File) : this(file.toUtFile())
    constructor(uri:Uri, context:Context) : this(uri.toUtFile(context))

    val hasPath:Boolean get() = utFile.safeUri.scheme == "file"
    val hasUri:Boolean get() = true

    override fun openExtractor(): CloseableExtractor {
        val pfd = openParcelFileDescriptorToRead()
        val extractor = MediaExtractor().apply { setDataSource(pfd.fileDescriptor) }
        return CloseableExtractor(extractor, pfd)
    }

    override fun openMetadataRetriever(): CloseableMediaMetadataRetriever {
        val pfd = openParcelFileDescriptorToRead()
        val retriever = MediaMetadataRetriever().apply { setDataSource(pfd.fileDescriptor) }
        return CloseableMediaMetadataRetriever(retriever, pfd)
    }

    override fun openMuxer(format: ContainerFormat): CloseableMuxer {
        val pfd = openParcelFileDescriptorToWrite()
        val muxer = MediaMuxer(pfd.fileDescriptor, format.mof)
        return CloseableMuxer(muxer, pfd)
    }

    override val seekable: Boolean = true
}

fun Uri.toAndroidFile(context:Context):AndroidFile {
    if (this.scheme == "content") {
        return AndroidFile(this.toUtFile(context))
    } else {
        val path = this.path ?: throw IllegalArgumentException("invalid uri: $this")
        return AndroidFile(File(path).toUtFile())
    }
}

@Suppress("unused")
fun File.toAndroidFile():AndroidFile {
    return AndroidFile(this.toUtFile())
}