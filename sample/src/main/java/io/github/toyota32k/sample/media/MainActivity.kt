package io.github.toyota32k.sample.media

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.UnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.dialog.broker.pickers.UtFilePickerStore
import io.github.toyota32k.dialog.task.UtImmortalAndroidViewModel
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.lib.player.common.TpTempFile
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.toAndroidFile
import io.github.toyota32k.sample.media.databinding.ActivityMainBinding
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

class MainActivity : UtMortalActivity() {
    override val logger = UtLog("Main")
    private val pickers = UtFilePickerStore(this)
    private val binder = Binder()
    private lateinit var controls: ActivityMainBinding

    class MainViewModel(application: Application) : UtImmortalAndroidViewModel(application) {
        val selectInputFileCommand = LiteUnitCommand()
        val analyzeInputFileCommand = LiteUnitCommand {
            val input = inputFile.value ?: return@LiteUnitCommand
            val summary = Converter.analyze(input.toAndroidFile(application))
            analyzedResult.value = summary.toString()
        }
        val inputFile: MutableStateFlow<Uri?> = MutableStateFlow(null)
        val inputFileAvailable = inputFile.map { it!=null }
        val analyzedResult = MutableStateFlow<String>("")
    }

    private val viewModel:MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        binder
            .owner(this)
            .bindCommand(viewModel.selectInputFileCommand, this::selectInputFile)
    }

    private fun selectInputFile() {
        UtImmortalSimpleTask.run {
            viewModel.inputFile.value = pickers.openFilePicker.selectFile(arrayOf("video/*"))
            true
        }
    }
}