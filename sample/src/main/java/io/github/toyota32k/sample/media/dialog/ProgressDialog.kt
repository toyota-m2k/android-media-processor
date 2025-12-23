package io.github.toyota32k.sample.media.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.ReliableCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.progressBarBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import io.github.toyota32k.sample.media.databinding.DialogProgressBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

interface IProgressSetter {
    var progress: Int   // percentage
    var progressText: String
    var message: String
    val cancelled: Flow<Boolean>
}

class ProgressDialog : UtDialogEx() {
    class ProgressViewModel : UtDialogViewModel() {
        val progress = MutableStateFlow(0)
        val progressText = MutableStateFlow("")
        val message = MutableStateFlow("")
        val cancelled = MutableStateFlow(false)
        val cancelCommand = LiteUnitCommand { cancelled.value = true}
        val closeCommand = ReliableCommand<Boolean>()

        private inner class ProgressSetter : IProgressSetter {
            override var progress: Int
                get() = this@ProgressViewModel.progress.value
                set(value) {
                    this@ProgressViewModel.progress.value = value
                }
            override var progressText: String
                get() = this@ProgressViewModel.progressText.value
                set(value) {
                    this@ProgressViewModel.progressText.value = value
                }
            override var message: String
                get() = this@ProgressViewModel.message.value
                set(value) {
                    this@ProgressViewModel.message.value = value
                }
            override val cancelled: Flow<Boolean> = this@ProgressViewModel.cancelled
        }
        val progressSetter:IProgressSetter = ProgressSetter()
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
        suspend fun <T> withProgressDialog(taskName:String="withProgressDialog", block: suspend (IProgressSetter)->T):T {
            val vmf = MutableStateFlow<ProgressViewModel?>(null)
            UtImmortalTask.launchTask(taskName) {
                vmf.value = createViewModel<ProgressViewModel> {
                    progress.value = 0
                    progressText.value = ""
                    message.value = ""
                    cancelled.value = false
                }
                showDialog(taskName) { ProgressDialog() }.status.ok
            }
            return vmf.filterNotNull().first().let { vm ->
                try {
                    block(vm.progressSetter)
                } finally {
                    vm.closeCommand.invoke(true)
                }
            }
        }
    }
}