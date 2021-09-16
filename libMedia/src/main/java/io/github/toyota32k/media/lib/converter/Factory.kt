package io.github.toyota32k.media.lib.converter

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import io.github.toyota32k.media.lib.format.DefaultAudioStrategy
import io.github.toyota32k.media.lib.format.HD720VideoStrategy
import io.github.toyota32k.media.lib.format.IAudioStrategy
import io.github.toyota32k.media.lib.format.IVideoStrategy
import io.github.toyota32k.media.lib.misc.AndroidFile
import io.github.toyota32k.media.lib.misc.TrimmingRange
import io.github.toyota32k.media.lib.utils.UtLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.IllegalStateException

class Factory {
    companion object {
        val logger = UtLog("Factory", Converter.logger, Converter.logger.omissionNamespace)
    }
    private var inPath: AndroidFile? = null
    private var outPath: AndroidFile? = null
    private var videoStrategy:IVideoStrategy = HD720VideoStrategy
    private var audioStrategy:IAudioStrategy = DefaultAudioStrategy
    private var trimStart:Long = 0L
    private var trimEnd:Long = 0L
    private var deleteOutputOnError = true
    private var onProgress:((IProgress)->Unit)? = null

    fun input(path: File): Factory {
        inPath = AndroidFile(path)
        return this
    }

    fun input(uri: Uri, context: Context): Factory {
        inPath = AndroidFile(uri, context)
        return this
    }

    fun output(path: File): Factory {
        outPath = AndroidFile(path)
        return this
    }

    fun output(uri: Uri, context: Context): Factory {
        outPath = AndroidFile(uri, context)
        return this
    }

    fun videoStrategy(s:IVideoStrategy):Factory {
        videoStrategy = s
        return this
    }
    fun audioStrategy(s:IAudioStrategy):Factory {
        audioStrategy = s
        return this
    }

    fun trimmingStartFrom(timeMs:Long):Factory {
        trimStart = timeMs*1000L
        return this
    }

    fun trimmingEndTo(timeMs:Long):Factory {
        trimEnd = timeMs*1000L
        return this
    }

    fun setProgressHandler(proc:(IProgress)->Unit):Factory {
        onProgress = proc
        return this
    }

    fun deleteOutputOnError(flag:Boolean):Factory {
        this.deleteOutputOnError = flag
        return this
    }


    fun build():Converter {
        val input = inPath ?: throw IllegalStateException("input file is not specified.")
        val output = outPath ?: throw IllegalStateException("output file is not specified.")

        logger.info("### media converter information ###")
        logger.info("input : $input")
        logger.info("output: $output")
        logger.info("video strategy: ${videoStrategy.javaClass.name}")
        logger.info("audio strategy: ${audioStrategy.javaClass.name}")

        val trimmingRange = if(trimStart>0||trimEnd>0) {
            logger.info("trimming start: ${trimStart / 1000} ms")
            logger.info("trimming end  : ${trimEnd / 1000} ms")
            TrimmingRange(trimStart,trimEnd)
        } else {
            TrimmingRange.Empty
        }

        logger.info("delete output on error = $deleteOutputOnError")
        if(onProgress==null) {
            logger.info("no progress handler")
        }

        return Converter(input, output, videoStrategy, audioStrategy, trimmingRange).also { cv->
            cv.deleteOutputOnError = deleteOutputOnError
            cv.onProgress = onProgress
        }
    }

    suspend fun execute() : Result {
        return build().execute()
    }

    fun executeAsync():Converter.Awaiter {
        return build().executeAsync()
    }
}