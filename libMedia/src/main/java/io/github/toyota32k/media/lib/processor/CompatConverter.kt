package io.github.toyota32k.media.lib.processor

import android.content.Context
import android.net.Uri
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.converter.HttpInputFile
import io.github.toyota32k.media.lib.converter.IConvertResult
import io.github.toyota32k.media.lib.converter.IHttpStreamSource
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.IOutputMediaFile
import io.github.toyota32k.media.lib.converter.IProgress
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.format.ContainerFormat
import io.github.toyota32k.media.lib.processor.misc.IFormattable.Companion.dump
import io.github.toyota32k.media.lib.processor.misc.RangeUsListBuilder
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.utils.RangeUs
import io.github.toyota32k.media.lib.utils.RangeUs.Companion.ms2us
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration

/**
 * Processor を 旧Converterと互換APIで利用するためのラッパークラス
 */
class CompatConverter(
    val processor: Processor,
    val options: ProcessorOptions,
    val deleteOutputOnError: Boolean = true
) : IExecutor {
    companion object {
        val logger = UtLog("TrimmingExecutor", Processor.logger)
    }
    val report = Report()
    override suspend fun execute(): IConvertResult {
        return withContext(Dispatchers.IO) {
            try {
                processor.process(options).toConvertResult()
            } catch(e:Throwable) {
                logger.error(e)
                if (deleteOutputOnError) {
                    options.outPath.safeDelete()
                }
                ProcessorResult.error(e)
            }
        }
    }

    override fun cancel() {
        processor.cancel()
    }

    class Builder(
        val processorBuilder:Processor.Builder = Processor.Builder(),
        val optionBuilder: ProcessorOptions.Builder = ProcessorOptions.Builder(),) {
        private var mDeleteOutputOnError = true

        /**
         * 入力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * @param IInputMediaFile
         */
        fun input(src: IInputMediaFile) = apply {
            optionBuilder.input(src)
        }

        /**
         * 入力ファイルを設定
         * @param File
         */
        fun input(path: File) = input(AndroidFile(path))

        /**
         * 入力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun input(uri: Uri, context: Context) = input(AndroidFile(uri, context))

        /**
         * 入力ファイルを設定
         * @param String URL (http/https)
         * @param Context
         */
        fun input(url: String, context: Context) = apply {
            if (!url.startsWith("http")) throw IllegalArgumentException("url must be http or https")
            input(HttpInputFile(context, url))
        }

        /**
         * 入力ファイルを設定
         * @param IHttpStreamSource
         * @param Context
         */
        fun input(source: IHttpStreamSource, context: Context) = input(HttpInputFile(context, source))

        /**
         * 出力ファイルを設定（必須）
         * 通常は、適当な型の引数をとるバリエーションを利用する。
         * ただし、inputと異なり、HttpFileは利用不可
         * @param IOutputMediaFile
         */
        fun output(dst: IOutputMediaFile) = apply {
            optionBuilder.output(dst)
        }

        /**
         * 出力ファイルを設定
         * @param File
         */
        fun output(path: File) = output(AndroidFile(path))

        /**
         * 出力ファイルを設定
         * @param Uri
         * @param Context
         */
        fun output(uri: Uri, context: Context) = output(AndroidFile(uri, context))

        // endregion

        // region Setter: Trimming

        fun trimming(fn: RangeUsListBuilder.()->Unit) = apply {
            optionBuilder.trimming(fn)
        }

        // trimming に被せて、切り取る範囲を指定。
        // trimming + splitting に利用する想定

        fun clipStartUs(us:Long) = apply {
            optionBuilder.clipStartUs(us)
        }
        fun clipEndUs(us:Long) = apply {
            optionBuilder.clipEndUs(us)
        }
        fun clipStartMs(ms:Long) =
            clipStartUs(ms.ms2us())

        fun clipEndMs(ms:Long) =
            clipEndUs(ms.ms2us())

        fun clipRangeUs(rangeUs: RangeUs) = apply {
            clipStartUs(rangeUs.startUs)
            clipEndUs(rangeUs.endUs)
        }
        fun clipReset() = apply {
            clipStartUs(0L)
            clipEndUs(0L)
        }

        /**
         * 動画の向きを指定
         */
        fun rotate(rotation: Rotation?) = apply {
            optionBuilder.rotate(rotation)
        }


        /**
         * 最大動画長を指定 (ms)
         * 0以下なら制限なし
         */
        fun limitDuration(durationMs:Long) = apply {
            optionBuilder.limitDuration(durationMs)
        }

        /**
         * 最大動画長を指定 (Duration)
         * nullまたは0以下なら制限なし
         */
        fun limitDuration(duration: Duration?)
                = limitDuration(duration?.inWholeMilliseconds ?: 0L)

        // endregion

        // region Processor Properties

        /**
         * コンテナフォーマットを指定
         * MPEG_4 以外はテストしていません。
         */
        fun containerFormat(containerFormat: ContainerFormat) = apply {
            processorBuilder.containerFormat(containerFormat)
        }

        /**
         * 作業バッファサイズ（NoReEncodeの場合にのみ使用）
         */
        fun bufferSize(sizeInBytes:Int) = apply {
            processorBuilder.bufferSize(sizeInBytes)
        }

        /**
         * 進捗報告ハンドラを設定
         */
        fun setProgressHandler(proc:(IProgress)->Unit) = apply {
            processorBuilder.onProgress(proc)
        }

        /**
         * video strategy を指定
         */
        fun videoStrategy(strategy: IVideoStrategy) = apply {
            optionBuilder.videoStrategy(strategy)
        }

        /**
         * audio strategy を指定
         */
        fun audioStrategy(strategy: IAudioStrategy) = apply {
            optionBuilder.audioStrategy(strategy)
        }

        // endregion




        // region Executor Properties

        /**
         * エラー発生時に出力ファイルを削除するかどうか
         * デフォルトは true
         */
        fun deleteOutputOnError(flag:Boolean) = apply {
            mDeleteOutputOnError = flag
        }

        fun preferSoftwareDecoder(flag:Boolean) = apply {
            optionBuilder.preferSoftwareDecoder(flag)
        }


        // endregion

        // region Build TrimmingExecutor


        fun build(): CompatConverter {
            try {
                val processor = processorBuilder.build()
                val options = optionBuilder.build()
                logger.dump(options)
                logger.dump(processor)
                return CompatConverter(processor, options)
            } catch(e:Throwable) {
                logger.error(e)
                if (mDeleteOutputOnError) {
                    optionBuilder.output?.safeDelete()
                }
                throw e
            }
        }
        // endregion
    }
}