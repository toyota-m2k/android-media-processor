package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.github.toyota32k.media.lib.format.ContainerFormat
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Converterの入・出力に利用できるファイルクラス
 *
 * Androidのファイルパス指定を抽象化
 * java.io.Fileによる指定と、android.net.Uri (+Context) による指定をサポート
 * ただし、Uriによる指定は、 android.provider.DocumentsProvider ベースのuri、すなわち、
 * Intent.ACTION_OPEN_DOCUMENTなどによって、取得されたuriであることを前提としている。
 */
class AndroidFile : IInputMediaFile, IOutputMediaFile {
    val uri:Uri?
    val context:Context?
    val path: File?

    constructor(uri: Uri, context: Context) {
        this.uri = uri
        this.context = context
        this.path = null
    }
    constructor(file:File) {
        uri = null
        context = null
        path = file
    }

    val hasPath:Boolean get() = path!=null
    val hasUri:Boolean get() = uri!=null

//    val fileDescriptor:FileDescriptor
//        get() =
//        if (hasUri) {
//            context!!.contentResolver.openAssetFileDescriptor(uri!!, "w")!!.fileDescriptor
//        } else {
//            RandomAccessFile(path, "rws").apply {
//                setLength(0)
//            }.fd
//        }

    private fun getFileSizeFromUri(uri: Uri): Long {

        val contentResolver = context?.contentResolver ?: return -1L
        val mimeType = contentResolver.getType(uri) ?: return -1L

        // ContentResolverから取得を試みる
        if (mimeType.startsWith("image/") || mimeType.startsWith("video/") ||
            mimeType.startsWith("audio/") || mimeType == "application/octet-stream") {
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    return it.getLong(sizeIndex)
                }
            }
        }
        // 取得できなければファイルを開いて取得する
        contentResolver.openFileDescriptor(uri, "r")?.use {
            return it.statSize
        }
        return -1L
    }
    override fun getLength():Long {
        return if(hasPath) {
            path!!.length()
        } else if(hasUri){
            getFileSizeFromUri(uri!!)
        } else {
            -1L
        }
    }

    private fun <T> withFileDescriptor(mode:String, fn:(FileDescriptor)->T):T {
        return if(hasUri) {
            context!!.contentResolver.openAssetFileDescriptor(uri!!, mode)!!.use {
                fn(it.fileDescriptor)
            }
        } else {
            ParcelFileDescriptor.open(path!!, ParcelFileDescriptor.parseMode(mode)).use {
                fn(it.fileDescriptor)
            }
        }
    }

    private fun <T> fileDescriptorToRead(fn:(FileDescriptor)->T):T = withFileDescriptor("r", fn)

    private fun <T> fileDescriptorToWrite(fn:(FileDescriptor)->T):T = withFileDescriptor("rw", fn)

    fun <T> fileInputStream(fn:(FileInputStream)->T):T {
        return fileDescriptorToRead {
            FileInputStream(it).use(fn)
        }
    }
    fun <T> fileOutputStream(fn:(FileOutputStream)->T):T {
        return fileDescriptorToWrite {
            FileOutputStream(it).use(fn)
        }
    }

    private fun openParcelFileDescriptor(mode:String):ParcelFileDescriptor {
        return if(hasUri) {
            context!!.contentResolver.openFileDescriptor(uri!!, mode )!!
        } else { // Use RandomAccessFile so we can open the file with RW access;
            ParcelFileDescriptor.open(path!!, ParcelFileDescriptor.parseMode(mode))
        }
    }

    private fun openParcelFileDescriptorToRead() = openParcelFileDescriptor("r")
    private fun openParcelFileDescriptorToWrite() = openParcelFileDescriptor("rw")


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

    override fun toString(): String {
        return path?.toString() ?: uri?.toString() ?: "*invalid-path*"
    }

    override fun delete() {
        if (hasPath) {
            path!!.delete()
        } else if (hasUri) {
            DocumentFile.fromSingleUri(context!!, uri!!)?.delete()
        }
    }

    override val seekable: Boolean = true

    fun safeDelete() {
        try {
            delete()
        } catch(_:Throwable) {

        }
    }

    fun getFileName() : String? {
        return if(hasPath) {
            path?.name
        } else {
            when(uri?.scheme) {
                "content"-> {
                    val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
                    val cursor: Cursor? = context?.contentResolver?.query(uri, projection, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                        } else null
                    }
                }
                "file"-> uri.path?.let { File(it).name }
                else -> null
            }
        }
    }

    fun exists(): Boolean {
        return if(hasPath) {
            path?.exists()
        } else {
            when(uri?.scheme) {
                "content"-> {
                    val cursor: Cursor? = context?.contentResolver?.query(uri, null, null, null, null)
                    cursor?.use {
                        it.count > 0
                    }
                }
                "file"-> uri.path?.let { File(it).exists() }
                else -> false
            }
        } ?: false

    }


    val safeUri:Uri
        get() = uri ?: path!!.toUri()

    fun copyFrom(src:AndroidFile) {
        src.fileInputStream { input->
            this.fileOutputStream { output->
                input.channel.transferTo(0, input.channel.size(), output.channel)
            }
        }
    }

//
//    fun mediaMuxer(format:Int=MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4): MediaMuxer {
//        return if(hasUri) {
//            MediaMuxer(context!!.contentResolver.openAssetFileDescriptor(uri!!, "rws" )!!.fileDescriptor, format)
//        } else {
//            MediaMuxer(path!!.toString(), format)
//        }
//    }
}

fun MediaExtractor.setDataSource(source:AndroidFile) {
    if(source.hasUri) {
        this.setDataSource(source.context!!, source.uri!!, null)
    } else {
        this.setDataSource(source.path!!.toString())
    }
}

fun Uri.toAndroidFile(context:Context):AndroidFile {
    return AndroidFile(this, context)
}

fun File.toAndroidFile():AndroidFile {
    return AndroidFile(this)
}