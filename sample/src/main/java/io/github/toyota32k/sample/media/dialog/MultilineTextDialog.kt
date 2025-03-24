package io.github.toyota32k.sample.media.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import io.github.toyota32k.sample.media.databinding.DialogMultilineTextBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MultilineTextDialog : UtDialogEx() {
    class MultilineTextViewModel : UtDialogViewModel() {
        val label = MutableStateFlow("")
        val message = MutableStateFlow("")
    }

    private val viewModel by lazy { getViewModel<MultilineTextViewModel>() }
    private lateinit var controls: DialogMultilineTextBinding

    override fun preCreateBodyView() {
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        rightButtonType = ButtonType.CLOSE
    }
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogMultilineTextBinding.inflate(inflater.layoutInflater)
        return controls.root.also {_->
            binder
                .textBinding(controls.label, viewModel.label)
                .textBinding(controls.message, viewModel.message)
                .visibilityBinding(controls.label, viewModel.label.map { it.isNotBlank()})
        }
    }

    companion object {
        fun show(label:String, message:String) {
            UtImmortalTask.launchTask {
                createViewModel<MultilineTextViewModel> {
                    this.label.value = label
                    this.message.value = message
                }
                showDialog(MultilineTextDialog())
            }
        }
    }

}