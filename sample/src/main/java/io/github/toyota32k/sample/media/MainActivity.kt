package io.github.toyota32k.sample.media

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.alphaBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.UtDialogConfig
import io.github.toyota32k.dialog.broker.pickers.UtFilePickerStore
import io.github.toyota32k.dialog.task.UtImmortalAndroidViewModel
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
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
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.sample.media.databinding.ActivityMainBinding
import io.github.toyota32k.sample.media.dialog.MultilineTextDialog
import io.github.toyota32k.sample.media.dialog.ProgressDialog
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.disposableObserve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class MainActivity : UtMortalActivity() {
    companion object {
        val logger = UtLog("Main")
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
        val videoSource: VideoSource?
            get() = playerModel.currentSource.value as VideoSource?


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


        val selectInputFileCommand = LiteUnitCommand {
            UtImmortalSimpleTask.run {
                withOwner {
                    val activity = it.asActivity() as MainActivity
                    inputFile.value = activity.pickers.openFilePicker.selectFile(arrayOf("video/*"))?.apply {
                        converted.value = false
                        outputFile.value = null
                        setSource(AndroidFile(this, application))
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
                    converted.value = false
                    outputFile.value = activity.pickers.createFilePicker.selectFile(outFile, "video/mp4")
                }
                true
            }
        }

        val outputPlayCommand = LiteUnitCommand {
            if(!converted.value) return@LiteUnitCommand
            val uri = outputFile.value ?: return@LiteUnitCommand
            UtImmortalSimpleTask.run {
                withOwner {
                    val activity = it.asActivity() ?: return@withOwner false
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "video/mp4")
//                    if (intent.resolveActivity(activity.packageManager) != null) {
                        activity.startActivity(intent)
//                    }
                    true
                }
            }
        }


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

        val commandSave = LiteUnitCommand() {
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
                        .videoStrategy(PresetVideoStrategies.HEVC1080Profile)
                        .rotate(rotation)
                        .addTrimmingRanges(*ranges.map { Converter.Factory.RangeMs(it.start, it.end) }.toTypedArray())
                        .setProgressHandler {
                            sink.progress = it.percentage
                            sink.progressText = it.format()
                        }
                        .build()
                    val awaiter = converter.executeAsync()
                    sink.cancelled.disposableObserve(currentCoroutineContext()) { cancelled->
                        if(cancelled) {
                            awaiter.cancel()
                        }
                    }
                    withContext(Dispatchers.IO) {
                        try {
                            awaiter.await().also { convertResult ->
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
                    showConfirmMessageBox("Completed.", "${stringInKb(srcLen)} → ${stringInKb(dstLen)}")
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
            .bindCommand(viewModel.selectInputFileCommand, controls.inputRefButton)
            .bindCommand(viewModel.analyzeInputFileCommand, controls.inputAnalyzeButton)
            .bindCommand(viewModel.selectOutputFileCommand, controls.outputRefButton)
            .bindCommand(viewModel.analyzeOutputFileCommand, controls.outputAnalyzeButton)
            .bindCommand(viewModel.outputPlayCommand, controls.outputPlayButton)
            .bindCommand(viewModel.commandAddChapter, controls.makeChapter)
            .bindCommand(viewModel.commandAddSkippingChapter, controls.makeChapterAndSkip)
            .bindCommand(viewModel.commandRemoveChapter, controls.removeNextChapter)
            .bindCommand(viewModel.commandRemoveChapterPrev, controls.removePrevChapter)
            .bindCommand(viewModel.commandRedo, controls.redo)
            .bindCommand(viewModel.commandUndo, controls.undo)
            .bindCommand(viewModel.commandSave, controls.saveVideo)
            .bindCommand(viewModel.commandToggleSkip, controls.makeRegionSkip)
            .multiVisibilityBinding(arrayOf(controls.chapterButtons, controls.videoViewer), viewModel.inputFileAvailable, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
            .multiEnableBinding(arrayOf(controls.inputAnalyzeButton, controls.outputRefButton), viewModel.inputFileAvailable)
            .multiEnableBinding(arrayOf(controls.outputAnalyzeButton, controls.outputPlayButton), combine(viewModel.outputFileAvailable, viewModel.converted) {o,c->o&&c})
            .enableBinding(controls.saveVideo, viewModel.readyToConvert)
            .textBinding(controls.inputFileName, viewModel.inputFileName)
            .textBinding(controls.outputFileName, viewModel.outputFileName)
            .alphaBinding(controls.inputFileName, viewModel.inputFileAvailable.map { if(it) 1f else 0.5f }.asLiveData())
            .alphaBinding(controls.outputFileName, viewModel.outputFileAvailable.map { if(it) 1f else 0.5f }.asLiveData())

        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)

    }
}