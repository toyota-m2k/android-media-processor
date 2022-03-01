package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile

/**
 * Androidのファイルパス指定を抽象化するためのクラス
 * java.io.Fileによる指定と、android.net.Uri (+Context) による指定をサポート
 * ただし、Uriによる指定は、 android.provider.DocumentsProvider ベースのuri、すなわち、
 * Intent.ACTION_OPEN_DOCUMENTなどによって、取得されたuriであることを前提としている。
 */
class AndroidFile {
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

    fun <T> withFileDescriptor(mode:String, fn:(FileDescriptor)->T):T {
        return if(hasUri) {
            context!!.contentResolver.openAssetFileDescriptor(uri!!, mode)!!.use {
                fn(it.fileDescriptor)
            }
        } else {
            ParcelFileDescriptor.open(path!!, ParcelFileDescriptor.parseMode("r")).use {
                fn(it.fileDescriptor)
            }
        }
    }

    fun <T> fileDescriptorToRead(fn:(FileDescriptor)->T):T = withFileDescriptor("r", fn)

    fun <T> fileDescriptorToWrite(fn:(FileDescriptor)->T):T = withFileDescriptor("rw", fn)

    fun openParcelFileDescriptor(mode:String):ParcelFileDescriptor {
        return if(hasUri) {
            context!!.contentResolver.openFileDescriptor(uri!!, mode )!!
        } else { // Use RandomAccessFile so we can open the file with RW access;
            ParcelFileDescriptor.open(path!!, ParcelFileDescriptor.parseMode(mode))
        }
    }

    fun openParcelFileDescriptorToRead() = openParcelFileDescriptor("r")
    fun openParcelFileDescriptorToWrite() = openParcelFileDescriptor("rw")

    override fun toString(): String {
        return path?.toString() ?: uri?.toString() ?: "*invalid-path*"
    }

    fun delete() {
        if (hasPath) {
            path!!.delete()
        } else if (hasUri) {
            DocumentFile.fromSingleUri(context!!, uri!!)?.delete()
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