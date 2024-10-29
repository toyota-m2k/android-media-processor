package io.github.toyota32k.sample.media

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.spinnerBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.broker.pickers.UtFilePickerStore
import io.github.toyota32k.dialog.task.UtImmortalAndroidViewModel
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.dialog.task.application
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.ChapterEditor
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import io.github.toyota32k.lib.player.model.skipChapter
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.converter.ConvertResult
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.FastStart
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.converter.format
import io.github.toyota32k.media.lib.converter.toAndroidFile
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.strategy.VideoStrategy
import io.github.toyota32k.sample.media.databinding.ActivityMainBinding
import io.github.toyota32k.sample.media.dialog.DetailMessageDialog
import io.github.toyota32k.sample.media.dialog.MultilineTextDialog
import io.github.toyota32k.sample.media.dialog.ProgressDialog
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.disposableObserve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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

class MainActivity : UtMortalActivity() {
    companion object {
        val logger = UtLog("Main")
        val strategyList = listOf(
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
    }
    private val pickers = UtFilePickerStore(this)
    private val binder = Binder()
    private lateinit var controls: ActivityMainBinding




    class VideoSource(val file:AndroidFile) : IMediaSourceWithChapter {
        override val chapterList: IChapterList = MutableChapterList()
        override val id: String
            get() = file.safeUri.toString()
        override val name: String
            get() = file.getFileName() ?: "unnamed"
        override val uri: String
            get() = file.safeUri.toString()
        override var startPosition = AtomicLong()
        override val trimming: Range = Range.empty
        override val type: String = "mp4"
    }

    class MainViewModel(application: Application) : UtImmortalAndroidViewModel(application) {
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

        val inputFile: MutableStateFlow<Uri?> = MutableStateFlow(null)
        val outputFile: MutableStateFlow<Uri?> = MutableStateFlow(null)

        val inputFileAvailable = inputFile.map { it!=null }
        val outputFileAvailable = outputFile.map { it!=null }
        val inputFileName = inputFile.map { it?.toAndroidFile(application)?.getFileName() ?: "select input file"}
        val outputFileName = outputFile.map {it?.toAndroidFile(application)?.getFileName() ?: "select output file"}
        val readyToConvert = combine(inputFileAvailable, outputFileAvailable) {i,o-> i && o }
        val converted = MutableStateFlow(false)

        val softwareEncode: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val softwareDecode: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val playOutput: MutableStateFlow<Boolean> = MutableStateFlow(false)

        val namedVideoStrategy = MutableStateFlow<NamedVideoStrategy>(strategyList[0])

        fun updatePlayerSource() {
            val src = if(!playOutput.value) {
                // Inputを再生
                inputFile.value?.toAndroidFile(application)
            } else if(converted.value){
                outputFile.value?.toAndroidFile(application)
            } else {
                null
            }
            if(src!=null) {
                setSource(src)
            } else {
                playerModel.reset()
            }
        }

        val selectInputFileCommand = LiteUnitCommand {
            UtImmortalSimpleTask.run {
                withOwner {
                    val activity = it.asActivity() as MainActivity
                    val file = activity.pickers.openFilePicker.selectFile(arrayOf("video/*"))
                    if (file!=null) {
                        inputFile.value = file
                        playOutput.value = false
                        updatePlayerSource()
                    }
                }
                true
            }
        }

        val selectOutputFileCommand = LiteUnitCommand {
            UtImmortalSimpleTask.run {
                withOwner {
                    val activity = it.asActivity() as MainActivity
                    val inFile = inputFile.value?.let { AndroidFile(it, application).getFileName() }
                    val outFile = if(inFile.isNullOrBlank()) "output.mp4" else "output-$inFile"
                    val file = activity.pickers.createFilePicker.selectFile(outFile, "video/mp4")
                    if(file!=null) {
                        outputFile.value = file
                        converted.value = false
                        playOutput.value = false
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

        val commandConvert = LiteUnitCommand() {
            val srcFile = AndroidFile(inputFile.value ?: return@LiteUnitCommand, application)
            val optFile = AndroidFile(outputFile.value ?: return@LiteUnitCommand, application)
            val trimFile = AndroidFile( File(application.cacheDir ?: return@LiteUnitCommand, "trimming"))

            val ranges = chapterEditor.enabledRanges(Range.empty)

            UtImmortalSimpleTask.run("trimming") {
                val result = ProgressDialog.withProgressDialog<ConvertResult> { sink ->
                    sink.message = "Trimming Now"
                    val rotation = if(playerModel.rotation.value!=0) Rotation(playerModel.rotation.value, relative = true) else Rotation.nop
                    val converter = Converter.Factory()
                        .input(srcFile)
                        .output(trimFile)
                        .audioStrategy(PresetAudioStrategies.AACDefault)
                        .rotate(rotation)
                        .addTrimmingRanges(*ranges.map { Converter.Factory.RangeMs(it.start, it.end) }.toTypedArray())
                        .setProgressHandler {
                            sink.progress = it.percentage
                            sink.progressText = it.format()
                        }
                        .apply {
                            if(softwareDecode.value) {
                                preferSoftwareDecoder(true)
                            }
                            val s = namedVideoStrategy.value.strategy as VideoStrategy
                            if(softwareEncode.value) {
                                videoStrategy(s.preferSoftwareEncoder())
                            } else {
                                videoStrategy(s)
                            }
                        }
                        .build()
                    sink.cancelled.disposableObserve(currentCoroutineContext()) { cancelled->
                        if(cancelled) {
                            converter.cancel()
                        }
                    }
                    withContext(Dispatchers.IO) {
                        try {
                            converter.execute().also { convertResult ->
                                if (convertResult.succeeded) {
                                    logger.debug(convertResult.toString())
                                    sink.message = "Optimizing Now..."
                                    if (!FastStart.process(inFile = trimFile, outFile = optFile) {
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
                if (result.succeeded) {
                    // 変換成功
                    val srcLen = srcFile.getLength()
                    val dstLen = optFile.getLength()
                    DetailMessageDialog.showMessage("Completed.", "${stringInKb(srcLen)} → ${stringInKb(dstLen)}", result.report?.toString() ?: "no information")
                } else if (!result.cancelled) {
                    showConfirmMessageBox("Error.", result.errorMessage ?: result.exception?.message ?: "unknown")
                }
                true
            }
        }

        private fun setSource(file:AndroidFile) {
            val videoSource = VideoSource(file)
            playerModel.setSource(videoSource, autoPlay = false)
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
        enableEdgeToEdge()
        UtDialogConfig.useLegacyTheme()
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
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
            .bindCommand(viewModel.commandAddChapter, controls.makeChapter)
            .bindCommand(viewModel.commandAddSkippingChapter, controls.makeChapterAndSkip)
            .bindCommand(viewModel.commandRemoveChapter, controls.removeNextChapter)
            .bindCommand(viewModel.commandRemoveChapterPrev, controls.removePrevChapter)
            .bindCommand(viewModel.commandRedo, controls.redo)
            .bindCommand(viewModel.commandUndo, controls.undo)
            .bindCommand(viewModel.commandConvert, controls.saveVideo)
            .bindCommand(viewModel.commandToggleSkip, controls.makeRegionSkip)
            .multiVisibilityBinding(arrayOf(controls.chapterButtons, controls.videoViewer), viewModel.inputFileAvailable, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
            .enableBinding(controls.inputAnalyzeButton, viewModel.inputFileAvailable)
            .enableBinding(controls.outputAnalyzeButton, combine(viewModel.outputFileAvailable, viewModel.converted) {o,c->o&&c})
            .enableBinding(controls.saveVideo, viewModel.readyToConvert)
            .textBinding(controls.inputFileButton, viewModel.inputFileName)
            .textBinding(controls.outputFileButton, viewModel.outputFileName)
            .materialRadioButtonGroupBinding(controls.playSelector, viewModel.playOutput, object: IIDValueResolver<Boolean> {
                override fun id2value(id: Int): Boolean? {
                    return id==controls.buttonOutput.id
                }
                override fun value2id(v: Boolean): Int {
                    return if(v) controls.buttonOutput.id else controls.buttonInput.id
                }
            })
            .observe(viewModel.playOutput) {
                viewModel.updatePlayerSource()
            }
            .checkBinding(controls.useSoftwareDecoder, viewModel.softwareDecode)
            .checkBinding(controls.useSoftwareEncoder, viewModel.softwareEncode)
            .spinnerBinding(controls.encodeQuality, viewModel.namedVideoStrategy, strategyList)

        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)

        /*
        VideoStrategy.availableDecoders(Codec.AVC).apply {
            logger.debug("Available Decoders for AVC:")
            logger.debug(this.toString())
        }
        VideoStrategy.availableDecoders(Codec.HEVC).apply {
            logger.debug("Available Decoders for HEVC:")
            logger.debug(this.toString())
        }
        VideoStrategy.availableEncoders(Codec.AVC).apply {
            logger.debug("Available Encoders for AVC:")
            logger.debug(this.toString())
        }
        VideoStrategy.availableEncoders(Codec.HEVC).apply {
            logger.debug("Available Encoders for HEVC:")
            logger.debug(this.toString())
        }
        logger.debug("ok.")
        */
    }
}