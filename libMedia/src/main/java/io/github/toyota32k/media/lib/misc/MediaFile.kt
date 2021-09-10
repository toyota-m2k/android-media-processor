package io.github.toyota32k.media.lib.misc

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile

class MediaFile {
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

    fun <T> fileDescriptorToRead(fn:(FileDescriptor)->T):T {
        return if(hasUri) {
            fn(context!!.contentResolver.openAssetFileDescriptor(uri!!, "r" )!!.fileDescriptor)
        } else { // Use RandomAccessFile so we can open the file with RW access;
            // RW access allows the native writer to memory map the output file.
            RandomAccessFile(path, "r").use {
                fn(it.fd)
            }
        }
    }

    fun <T> fileDescriptorToWrite(fn:(FileDescriptor)->T):T {
        return if(hasUri) {
            context!!.contentResolver.openAssetFileDescriptor(uri!!, "rw" )!!.use {
                fn(it.fileDescriptor)
            }
        } else { // Use RandomAccessFile so we can open the file with RW access;
            // RW access allows the native writer to memory map the output file.
            RandomAccessFile(path, "rws").use {
                it.setLength(0)
                fn(it.fd)
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

fun MediaExtractor.setDataSource(source:MediaFile) {
    if(source.hasUri) {
        this.setDataSource(source.context!!, source.uri!!, null)
    } else {
        this.setDataSource(source.path!!.toString())
    }
}