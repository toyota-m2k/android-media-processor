package io.github.toyota32k.sample.media.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.ReliableCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.progressBarBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.media.lib.processor.contract.IConvertResult
import io.github.toyota32k.media.lib.processor.contract.IMultiPhaseProgress
import io.github.toyota32k.media.lib.processor.contract.IProcessor
import io.github.toyota32k.media.lib.processor.contract.IProcessorOptions
import io.github.toyota32k.sample.media.databinding.DialogProgressBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class ProgressDialog : UtDialogEx() {
    class ProgressViewModel : UtDialogViewModel() {
        lateinit var processor: IProcessor
        val progress = MutableStateFlow(0)
        val progressText = MutableStateFlow("")
        val message = MutableStateFlow("")
        val cancelCommand = LiteUnitCommand { processor.cancel() }
        val closeCommand = ReliableCommand<Boolean>()
    }

    private val viewModel by lazy { getViewModel<ProgressViewModel>() }
    lateinit var controls: DialogProgressBinding

    override fun preCreateBodyView() {
        gravityOption = GravityOption.CENTER
        noHeader = true
        noFooter = true
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        cancellable = false
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogProgressBinding.inflate(inflater.layoutInflater)
        return controls.root.also { _->
            binder
                .textBinding(controls.message, viewModel.message)
                .textBinding(controls.progressText, viewModel.progressText)
                .progressBarBinding(controls.progressBar, viewModel.progress)
                .bindCommand(viewModel.cancelCommand, controls.cancelButton)
                .bindCommand(viewModel.closeCommand) { if(it) onPositive() else onNegative() }
        }
    }

    companion object {
        suspend fun processWithProgressDialog(taskName:String="withProgressDialog",initialMessage:String, processor: IProcessor, options: IProcessorOptions): IConvertResult {
            val vmf = MutableStateFlow<ProgressViewModel?>(null)
            UtImmortalTask.launchTask(taskName) {
                vmf.value = createViewModel<ProgressViewModel> {
                    this.processor = processor
                    progress.value = 0
                    progressText.value = ""
                    message.value = initialMessage
                }
                showDialog(taskName) { ProgressDialog() }.status.ok
            }
            return vmf.filterNotNull().first().let { vm ->
                try {
                    processor.process(options) { progress ->
                        vm.progress.value = progress.percentage
                        vm.progressText.value = progress.format()
                        if (progress is IMultiPhaseProgress) {
                            vm.message.value = progress.phase.description
                        }
                    }
                } finally {
                    vm.closeCommand.invoke(true)
                }
            }
        }
    }
}