package io.github.toyota32k.sample.media

import android.app.Application
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.spinnerBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.broker.UtActivityBrokerStore
import io.github.toyota32k.dialog.broker.pickers.UtCreateFilePicker
import io.github.toyota32k.dialog.broker.pickers.UtOpenFilePicker
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.ChapterEditor
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import io.github.toyota32k.lib.player.model.skipChapter
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.logger.UtLogConfig
import io.github.toyota32k.media.lib.io.AndroidFile
import io.github.toyota32k.media.lib.types.ConvertResult
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.media.lib.processor.optimizer.FastStart
import io.github.toyota32k.media.lib.types.IConvertResult
import io.github.toyota32k.media.lib.types.RangeMs
import io.github.toyota32k.media.lib.types.Rotation
import io.github.toyota32k.media.lib.legacy.converter.Splitter
import io.github.toyota32k.media.lib.processor.contract.format
import io.github.toyota32k.media.lib.io.toAndroidFile
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.getHeight
import io.github.toyota32k.media.lib.format.getWidth
import io.github.toyota32k.media.lib.processor.CompatConverter
import io.github.toyota32k.media.lib.strategy.DeviceCapabilities
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.strategy.VideoStrategy
import io.github.toyota32k.sample.media.MainActivity.MainViewModel.SourceIndex
import io.github.toyota32k.sample.media.databinding.ActivityMainBinding
import io.github.toyota32k.sample.media.dialog.DetailMessageDialog
import io.github.toyota32k.sample.media.dialog.MultilineTextDialog
import io.github.toyota32k.sample.media.dialog.ProgressDialog
import io.github.toyota32k.utils.lifecycle.disposableObserve
import io.github.toyota32k.utils.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class NamedVideoStrategy(val name: String, val strategy: IVideoStrategy) {
    override fun toString(): String {
        return name
    }
}
data class NamedAudioStrategy(val name: String, val strategy: IAudioStrategy) {
    override fun toString(): String {
        return name
    }
}

class MainActivity : UtMortalActivity() {
    companion object {
        val logger = UtLog("Main")
        val videoStrategies = listOf(
            NamedVideoStrategy("AVC HD720-Low", PresetVideoStrategies.AVC720LowProfile),
            NamedVideoStrategy("AVC HD720-Medium", PresetVideoStrategies.AVC720Profile),
            NamedVideoStrategy("AVC HD720-High", PresetVideoStrategies.AVC720HighProfile),
            NamedVideoStrategy("AVC Full HD", PresetVideoStrategies.AVC1080Profile),
            NamedVideoStrategy("AVC Full HD-High", PresetVideoStrategies.AVC1080HighProfile),
            NamedVideoStrategy("HEVC HD720-Low", PresetVideoStrategies.HEVC720LowProfile),
            NamedVideoStrategy("HEVC HD720", PresetVideoStrategies.HEVC720Profile),
            NamedVideoStrategy("HEVC Full HD-Low", PresetVideoStrategies.HEVC1080LowProfile),
            NamedVideoStrategy("HEVC Full HD-Medium", PresetVideoStrategies.HEVC1080Profile),
            NamedVideoStrategy("HEVC Full HD-High", PresetVideoStrategies.HEVC1080HighProfile),
        )
        val audioStrategies = listOf(
            NamedAudioStrategy("AAC LC", PresetAudioStrategies.AACDefault),
            NamedAudioStrategy("AAC HE-PS", PresetAudioStrategies.AACLowHEv2),
            NamedAudioStrategy("No Audio", PresetAudioStrategies.NoAudio),
            NamedAudioStrategy("AAC LC (Mono)", PresetAudioStrategies.AACMono),
        )
    }
    private val activityBrokers = UtActivityBrokerStore(this, UtOpenFilePicker(), UtCreateFilePicker())
    private val binder = Binder()
    private lateinit var controls: ActivityMainBinding




    class VideoSource(val file:AndroidFile) : IMediaSourceWithChapter {
        val chapterList: IChapterList = MutableChapterList()
        override val id: String
            get() = file.safeUri.toString()
        override val name: String
            get() = file.getFileName() ?: "unnamed"
        override val uri: String
            get() = file.safeUri.toString()
        override var startPosition = AtomicLong()
        override val trimming: Range = Range.empty
        override val type: String = "mp4"
        override suspend fun getChapterList(): IChapterList {
            return chapterList
        }
    }

    class MainViewModel(application: Application) : AndroidViewModel(application) {
        companion object {
            val logger = UtLog("VM", MainActivity.logger)
        }
        val playerControllerModel = PlayerControllerModel.Builder(application, viewModelScope)
            .supportChapter()
            .enableRotateLeft()
            .enableRotateRight()
//            .relativeSeekDuration(Settings.Player.spanOfSkipForward, Settings.Player.spanOfSkipBackward)
            .enableSeekSmall(0,0)
            .enableSeekMedium(1000, 3000)
            .enableSeekLarge(5000, 10000)
            .enableSliderLock(true)
            .build()
        val playerModel get() = playerControllerModel.playerModel
//        val videoSource: VideoSource?
//            get() = playerModel.currentSource.value as VideoSource?

        val analyzeInputFileCommand = LiteUnitCommand {
            val input = inputFile.value ?: return@LiteUnitCommand
            val summary = Converter.analyze(input.toAndroidFile(application))
            MultilineTextDialog.show("Input File", summary.toString())
        }
        val analyzeOutputFileCommand = LiteUnitCommand {
            val output = outputFile.value ?: return@LiteUnitCommand
            val summary = Converter.analyze(output.toAndroidFile(application))
            MultilineTextDialog.show("Output File", summary.toString())
        }
        val analyzeOutputFile2Command = LiteUnitCommand {
            val output = outputFile2.value ?: return@LiteUnitCommand
            val summary = Converter.analyze(output.toAndroidFile(application))
            MultilineTextDialog.show("Output File 2", summary.toString())
        }
        val videoDeviceCapabilitiesCommand = LiteUnitCommand {
            val enc = DeviceCapabilities.availableCodecs(namedVideoStrategy.value.strategy.codec, true)
            val dec = DeviceCapabilities.availableCodecs(namedVideoStrategy.value.strategy.codec, false)
            val summary = StringBuilder().apply {
                appendLine(enc.toString())
                appendLine(dec.toString())
            }
            MultilineTextDialog.show("Video Capabilities", summary.toString())
        }
        val audioDeviceCapabilitiesCommand = LiteUnitCommand {
            val enc = DeviceCapabilities.availableCodecs(PresetAudioStrategies.AACDefault.codec, true)
            val dec = DeviceCapabilities.availableCodecs(PresetAudioStrategies.AACDefault.codec, false)
            val summary = StringBuilder().apply {
                appendLine(enc.toString())
                appendLine(dec.toString())
            }
            MultilineTextDialog.show("Audio Capabilities", summary.toString())
        }

        val inputFile: MutableStateFlow<Uri?> = MutableStateFlow(null)
        val outputFile: MutableStateFlow<Uri?> = MutableStateFlow(null)
        val outputFile2: MutableStateFlow<Uri?> = MutableStateFlow(null)

        val inputFileAvailable = inputFile.map { it!=null }
        val outputFileAvailable = outputFile.map { it!=null }
        val outputFile2Available = outputFile2.map { it!=null }
        val inputFileName = inputFile.map { it?.toAndroidFile(application)?.getFileName() ?: "select input file"}
        val outputFileName = outputFile.map {it?.toAndroidFile(application)?.getFileName() ?: "select output file"}
        val outputFile2Name = outputFile2.map {it?.toAndroidFile(application)?.getFileName() ?: "select output file"}
        val readyToConvert = combine(inputFileAvailable, outputFileAvailable) {i,o-> i && o }
        val converted = MutableStateFlow(false)
        val splitted = MutableStateFlow(false)

        val softwareEncode: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val softwareDecode: MutableStateFlow<Boolean> = MutableStateFlow(false)
        enum class SourceIndex {
            Input,
            Output,
            Output2,
        }
        val playSource: MutableStateFlow<SourceIndex> = MutableStateFlow(SourceIndex.Input)

        val namedVideoStrategy = MutableStateFlow<NamedVideoStrategy>(videoStrategies[0])
        val namedAudioStrategy = MutableStateFlow<NamedAudioStrategy>(audioStrategies[0])

        fun updatePlayerSource() {
            val src = when (playSource.value) {
                SourceIndex.Input -> {
                    // Inputを再生
                    inputFile.value?.toAndroidFile(getApplication())
                }
                SourceIndex.Output -> {
                    if (converted.value || splitted.value) outputFile.value?.toAndroidFile(getApplication()) else null
                }
                SourceIndex.Output2 -> {
                    if (splitted.value) outputFile2.value?.toAndroidFile(getApplication()) else null
                }
            }
            if(src!=null) {
                setSource(src)
                playerModel.rotate(io.github.toyota32k.lib.player.model.Rotation.NONE)
            } else {
                playerModel.reset()
            }
        }

        val selectInputFileCommand = LiteUnitCommand {
            UtImmortalTask.launchTask {
                withOwner {
                    val activity = it.asActivity() as MainActivity
                    val file = activity.activityBrokers.openFilePicker.selectFile(arrayOf("video/*"))
                    if (file!=null) {
                        inputFile.value = file
                        playSource.value = SourceIndex.Input
                        converted.value = false
                        splitted.value = false
                        updatePlayerSource()
                    }
                }
                true
            }
        }

        val selectOutputFileCommand = LiteUnitCommand {
            UtImmortalTask.launchTask {
                withOwner {
                    val activity = it.asActivity() as MainActivity
                    val inFile = inputFile.value?.let { AndroidFile(it, application).getFileName() }
                    val outFile = if(inFile.isNullOrBlank()) "output.mp4" else "output-$inFile"
                    val file = activity.activityBrokers.createFilePicker.selectFile(outFile, "video/mp4")
                    if(file!=null) {
                        outputFile.value = file
                        converted.value = false
                        splitted.value = false
                        playSource.value = SourceIndex.Input
                        updatePlayerSource()
                    }
                }
                true
            }
        }
        val selectOutputFile2Command = LiteUnitCommand {
            UtImmortalTask.launchTask {
                withOwner {
                    val activity = it.asActivity() as MainActivity
                    val inFile = inputFile.value?.let { AndroidFile(it, application).getFileName() }
                    val outFile = if(inFile.isNullOrBlank()) "output-2.mp4" else "output-2-$inFile"
                    val file = activity.activityBrokers.createFilePicker.selectFile(outFile, "video/mp4")
                    if(file!=null) {
                        outputFile2.value = file
                        converted.value = false
                        splitted.value = false
                        playSource.value = SourceIndex.Input
                        updatePlayerSource()
                    }
                }
                true
            }
        }

//        val outputPlayCommand = LiteUnitCommand {
//            if(!converted.value) return@LiteUnitCommand
//            val uri = outputFile.value ?: return@LiteUnitCommand
//            UtImmortalSimpleTask.run {
//                withOwner {
//                    val activity = it.asActivity() ?: return@withOwner false
//                    val intent = Intent(Intent.ACTION_VIEW)
//                    intent.setDataAndType(uri, "video/mp4")
////                    if (intent.resolveActivity(activity.packageManager) != null) {
//                        activity.startActivity(intent)
////                    }
//                    true
//                }
//            }
//        }


        override fun onCleared() {
            logger.debug()
            super.onCleared()
            playerControllerModel.close()
        }

        // chapter editing
        private lateinit var chapterEditor: ChapterEditor

        val commandAddChapter = LiteUnitCommand {
            chapterEditor.addChapter(playerModel.currentPosition, "", null)
        }
        val commandAddSkippingChapter = LiteUnitCommand {
            val neighbor = chapterEditor.getNeighborChapters(playerModel.currentPosition)
            val prev = neighbor.getPrevChapter(chapterEditor)
            if(neighbor.hit<0) {
                // 現在位置にチャプターがなければ追加する
                if(!chapterEditor.addChapter(playerModel.currentPosition, "", null)) {
                    return@LiteUnitCommand
                }
            }
            // ひとつ前のチャプターを無効化する
            if(prev!=null) {
                chapterEditor.skipChapter(prev, true)
            }
        }
        val commandRemoveChapter = LiteUnitCommand {
            val neighbor = chapterEditor.getNeighborChapters(playerModel.currentPosition)
            chapterEditor.removeChapterAt(neighbor.next)
        }
        val commandRemoveChapterPrev = LiteUnitCommand {
            val neighbor = chapterEditor.getNeighborChapters(playerModel.currentPosition)
            chapterEditor.removeChapterAt(neighbor.prev)
        }
        val commandToggleSkip = LiteUnitCommand {
            val chapter = chapterEditor.getChapterAround(playerModel.currentPosition) ?: return@LiteUnitCommand
            chapterEditor.skipChapter(chapter, !chapter.skip)
        }

        private fun stringInKb(size: Long): String {
            return String.format(Locale.US, "%,d KB", size / 1000L)
        }

        val useNewProcessor = MutableStateFlow(true)

        val commandConvert = LiteUnitCommand() {
            val srcFile = AndroidFile(inputFile.value ?: return@LiteUnitCommand, application)
            val optFile = AndroidFile(outputFile.value ?: return@LiteUnitCommand, application)
            val trimFile = AndroidFile( File(application.cacheDir ?: return@LiteUnitCommand, "trimming"))

            val ranges = chapterEditor.enabledRanges(Range.empty)

            UtImmortalTask.launchTask("trimming") {
                splitted.value = false
                converted.value = false
                val result = ProgressDialog.withProgressDialog<IConvertResult> { sink ->
                    withContext(Dispatchers.IO) {
                        val videoSize = srcFile.openMetadataRetriever().use {
                            Size(it.obj.getWidth()?:0, it.obj.getHeight()?:0)
                        }
                        val subWidth = (videoSize.width*0.5).toInt()
                        val subHeight = (videoSize.height*0.5).toInt()
                        val sx = videoSize.width-subWidth
                        val sy = videoSize.height-subHeight
                        val crop = Rect(sx, sy, sx+subWidth, sy+subHeight)
//                        val crop = Rect(
//                            532,
//                             203,
//                             1663,
//                             1052)

                        sink.message = "Trimming Now"
                        val rotation = if (playerModel.rotation.value != 0) Rotation(playerModel.rotation.value, relative = true) else Rotation.nop
                        val converter = if (!useNewProcessor.value) {
                            Converter.Builder()
                                .input(srcFile)
                                .output(trimFile)
                                .audioStrategy(namedAudioStrategy.value.strategy)
                                .rotate(rotation)
                                .crop(crop)
                                .brightness(1.3f)
                                .trimming {
                                    addRangesMs(ranges.map { RangeMs(it.start, it.end) })
                                }
                                .setProgressHandler {
                                    sink.progress = it.percentage
                                    sink.progressText = it.format()
                                }
                                .preferSoftwareDecoder(softwareDecode.value)
                                .apply {
                                    val s = namedVideoStrategy.value.strategy as VideoStrategy
                                    if (softwareEncode.value) {
                                        videoStrategy(s.preferSoftwareEncoder())
                                    } else {
                                        videoStrategy(s)
                                    }
                                }
                                .build()
                        } else {
                            CompatConverter.Builder()
                                .input(srcFile)
                                .output(trimFile)
                                .audioStrategy(namedAudioStrategy.value.strategy)
//                                .audioStrategy(PresetAudioStrategies.NoAudio)
                                .rotate(rotation)
                                .crop(crop)
//                                .brightness(1.3f)
                                .trimming {
                                    addRangesMs(ranges.map { RangeMs(it.start, it.end) })
                                }
                                .setProgressHandler {
                                    sink.progress = it.percentage
                                    sink.progressText = it.format()
                                }
                                .preferSoftwareDecoder(softwareDecode.value)
                                .apply {
                                    val s = namedVideoStrategy.value.strategy as VideoStrategy
                                    if (softwareEncode.value) {
                                        videoStrategy(s.preferSoftwareEncoder())
                                    } else {
                                        videoStrategy(s)
                                    }
                                }
                                .build()
                        }

                        sink.cancelled.disposableObserve { cancelled ->
                            if (cancelled) {
                                converter.cancel()
                            }
                        }.use {
                            try {
                                converter.execute().also { convertResult ->
                                    if (convertResult.succeeded) {
                                        logger.debug(convertResult.toString())
                                        sink.message = "Optimizing Now..."
                                        if (!FastStart.process(inFile = trimFile, outFile = optFile, removeFree=true) {
                                                sink.progress = it.percentage
                                                sink.progressText = it.format()
                                            }) {
                                            // 変換不要
                                            optFile.copyFrom(trimFile)
                                        }
                                        converted.value = true
                                    }
                                }
                            } catch (e: Throwable) {
                                ConvertResult.error(e)
                            } finally {
                                trimFile.safeDelete()
                            }
                        }
                    }
                }
                if (result.succeeded) {
                    // 変換成功
                    val srcLen = srcFile.getLength()
                    val dstLen = optFile.getLength()
                    DetailMessageDialog.showMessage("Completed.", "${stringInKb(srcLen)} → ${stringInKb(dstLen)}", result.report?.toString() ?: "no information")
                } else if (!result.cancelled) {
                    showConfirmMessageBox("Error.", result.errorMessage ?: result.exception?.message ?: "unknown")
                }
            }
        }

        val commandTrimming = LiteUnitCommand() {
            val srcFile = AndroidFile(inputFile.value ?: return@LiteUnitCommand, application)
            val optFile = AndroidFile(outputFile.value ?: return@LiteUnitCommand, application)
            val trimFile = AndroidFile( File(application.cacheDir ?: return@LiteUnitCommand, "trimming"))

            val ranges = chapterEditor.enabledRanges(Range.empty)

            UtImmortalTask.launchTask("trimming") {
                splitted.value = false
                converted.value = false
                val result = ProgressDialog.withProgressDialog { sink ->
                    withContext(Dispatchers.IO) {
                        sink.message = "Trimming Now"
                        val rotation = if (playerModel.rotation.value != 0) Rotation(playerModel.rotation.value, relative = true) else Rotation.nop
                        val splitter = Splitter.Builder()
                            .rotate(rotation)
                            .setProgressHandler {
                                sink.progress = it.percentage
                                sink.progressText = it.format()
                            }
                            .build()
                        sink.cancelled.disposableObserve { cancelled ->
                            if (cancelled) {
                                splitter.cancel()
                            }
                        }.use {
                            try {
                                splitter.trim(srcFile,trimFile, ranges.map { RangeMs(it.start, it.end) }).also { result ->
                                    if (result.succeeded) {
                                        sink.message = "Optimizing Now..."
                                        if (!FastStart.process(inFile = trimFile, outFile = optFile, removeFree=true) {
                                                sink.progress = it.percentage
                                                sink.progressText = it.format()
                                            }) {
                                            // 変換不要
                                            optFile.copyFrom(trimFile)
                                        }
                                        converted.value = true
                                    }
                                }
                            } catch (e: Throwable) {
                                Splitter.Result.error(e)
                            } finally {
                                trimFile.safeDelete()
                            }
                        }
                    }
                }
                if (result.succeeded) {
                    // 変換成功
                    val srcLen = srcFile.getLength()
                    val dstLen = optFile.getLength()
                    showConfirmMessageBox("Trimming without ReEncoding", "Completed")
                } else if (!result.cancelled) {
                    showConfirmMessageBox("Error.", result.exception?.message ?: "unknown")
                }
            }

        }

        val commandChop = LiteUnitCommand() {
            val opt2File = outputFile2.value?.run { AndroidFile(this, application) } ?: return@LiteUnitCommand
            val srcFile = AndroidFile(inputFile.value ?: return@LiteUnitCommand, application)
            val opt1File = AndroidFile(outputFile.value ?: return@LiteUnitCommand, application)
            val trim1File = AndroidFile( File(application.cacheDir ?: return@LiteUnitCommand, "chop1"))
            val trim2File = AndroidFile( File(application.cacheDir ?: return@LiteUnitCommand, "chop2"))

            val position = playerControllerModel.playerModel.currentPosition
            UtImmortalTask.launchTask("chopping") {
                splitted.value = false
                converted.value = false
                val result = ProgressDialog.withProgressDialog<IConvertResult> { sink ->
                    withContext(Dispatchers.IO) {
                        sink.message = "Splitting Now"
                        val rotation = if (playerModel.rotation.value != 0) Rotation(playerModel.rotation.value, relative = true) else Rotation.nop
                        val splitter = Splitter.Builder()
                            .rotate(rotation)
                            .setProgressHandler {
                                sink.progress = it.percentage
                                sink.progressText = it.format()
                            }
                            .build()
                        sink.cancelled.disposableObserve { cancelled ->
                            if (cancelled) {
                                splitter.cancel()
                            }
                        }.use {
                            try {
                                splitter.chop(srcFile, trim1File, trim2File, position)[0].also { result ->
                                    if (result.succeeded) {
                                        sink.message = "Optimizing First File..."
                                        if (!FastStart.process(inFile = trim1File, outFile = opt1File, removeFree=true) {
                                                sink.progress = it.percentage
                                                sink.progressText = it.format()
                                            }) {
                                            // 変換不要
                                            opt1File.copyFrom(trim1File)
                                        }

                                        sink.message = "Optimizing Last File..."
                                        if (!FastStart.process(inFile = trim2File, outFile = opt2File, removeFree=true) {
                                                sink.progress = it.percentage
                                                sink.progressText = it.format()
                                            }) {
                                            // 変換不要
                                            opt2File.copyFrom(trim2File)
                                        }
                                        splitted.value = true
                                    }
                                }
                            } catch (e: Throwable) {
                                Splitter.Result.error(e)
                            } finally {
                                trim1File.safeDelete()
                                trim2File.safeDelete()
                            }
                        }
                    }
                }
                if (result.succeeded) {
                    // 変換成功
                    showConfirmMessageBox("Split media file", "Completed.")
                } else if (!result.cancelled) {
                    showConfirmMessageBox("Error.", result.exception?.message ?: "unknown")
                }
            }
        }



        private fun setSource(file:AndroidFile) {
            val videoSource = VideoSource(file)
            playerModel.setSource(videoSource)
            chapterEditor = ChapterEditor(videoSource.chapterList as IMutableChapterList)
        }

        val commandUndo = LiteUnitCommand {
            chapterEditor.undo()
        }
        val commandRedo = LiteUnitCommand {
            chapterEditor.redo()
        }


    }

    private val viewModel:MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UtLogConfig.logLevel = Log.VERBOSE
        enableEdgeToEdge()
//        UtDialogConfig.useLegacyTheme()

        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        ViewCompat.setOnApplyWindowInsetsListener(controls.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binder
            .owner(this)
            .bindCommand(viewModel.selectInputFileCommand, controls.inputFileButton)
            .bindCommand(viewModel.analyzeInputFileCommand, controls.inputAnalyzeButton)
            .bindCommand(viewModel.selectOutputFileCommand, controls.outputFileButton)
            .bindCommand(viewModel.analyzeOutputFileCommand, controls.outputAnalyzeButton)
            .bindCommand(viewModel.selectOutputFile2Command, controls.output2FileButton)
            .bindCommand(viewModel.analyzeOutputFile2Command, controls.output2AnalyzeButton)
            .bindCommand(viewModel.commandAddChapter, controls.makeChapter)
            .bindCommand(viewModel.commandAddSkippingChapter, controls.makeChapterAndSkip)
            .bindCommand(viewModel.commandRemoveChapter, controls.removeNextChapter)
            .bindCommand(viewModel.commandRemoveChapterPrev, controls.removePrevChapter)
            .bindCommand(viewModel.commandRedo, controls.redo)
            .bindCommand(viewModel.commandUndo, controls.undo)
            .bindCommand(viewModel.commandToggleSkip, controls.makeRegionSkip)
            .multiVisibilityBinding(arrayOf(controls.chapterButtons, controls.videoViewer), viewModel.inputFileAvailable, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
            .enableBinding(controls.inputAnalyzeButton, viewModel.inputFileAvailable)
            .enableBinding(controls.outputAnalyzeButton, combine(viewModel.outputFileAvailable, viewModel.converted) {o,c->o&&c})
            .enableBinding(controls.saveVideo, viewModel.readyToConvert)
            .multiEnableBinding(arrayOf(controls.buttonOutput2,controls.output2AnalyzeButton, controls.chopVideo),  viewModel.outputFile2Available)
            .textBinding(controls.inputFileButton, viewModel.inputFileName)
            .textBinding(controls.outputFileButton, viewModel.outputFileName)
            .textBinding(controls.output2FileButton, viewModel.outputFile2Name)
            .materialRadioButtonGroupBinding(controls.playSelector, viewModel.playSource, object: IIDValueResolver<SourceIndex> {
                override fun id2value(id: Int): SourceIndex {
                    return when(id) {
                        controls.buttonOutput.id -> SourceIndex.Output
                        controls.buttonOutput2.id -> SourceIndex.Output2
                        else -> SourceIndex.Input
                    }
                }
                override fun value2id(v: SourceIndex): Int {
                    return when(v) {
                        SourceIndex.Output -> controls.buttonOutput.id
                        SourceIndex.Output2 -> controls.buttonOutput2.id
                        else -> controls.buttonInput.id
                    }
                }
            })
            .observe(viewModel.playSource) {
                viewModel.updatePlayerSource()
            }
            .checkBinding(controls.useSoftwareDecoder, viewModel.softwareDecode)
            .checkBinding(controls.useSoftwareEncoder, viewModel.softwareEncode)
            .checkBinding(controls.useProcessor, viewModel.useNewProcessor)
            .spinnerBinding(controls.videoStrategy, viewModel.namedVideoStrategy, videoStrategies)
            .spinnerBinding(controls.audioStrategy, viewModel.namedAudioStrategy, audioStrategies)
            .bindCommand(viewModel.videoDeviceCapabilitiesCommand, controls.videoCapabilityButton)
            .bindCommand(viewModel.audioDeviceCapabilitiesCommand, controls.audioCapabilityButton)
            .bindCommand(viewModel.commandConvert, controls.saveVideo)
            .bindCommand(viewModel.commandChop, controls.chopVideo)
            .bindCommand(viewModel.commandTrimming, controls.trimVideo)

        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)

        DeviceCapabilities.availableCodecs(Codec.AAC, false).apply {
            logger.info("Available Decoders for AAC:")
            logger.info(this.toString())
        }
//        VideoStrategy.availableDecoders(Codec.AVC).apply {
//            logger.debug("Available Decoders for AVC:")
//            logger.debug(this.toString())
//        }
//        VideoStrategy.availableDecoders(Codec.HEVC).apply {
//            logger.debug("Available Decoders for HEVC:")
//            logger.debug(this.toString())
//        }
//        VideoStrategy.availableEncoders(Codec.AVC).apply {
//            logger.debug("Available Encoders for AVC:")
//            logger.debug(this.toString())
//        }
//        VideoStrategy.availableEncoders(Codec.HEVC).apply {
//            logger.debug("Available Encoders for HEVC:")
//            logger.debug(this.toString())
//        }
        logger.debug("ok.")
    }
}