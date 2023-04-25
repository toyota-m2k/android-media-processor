package io.github.toyota32k.media

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import io.github.toyota32k.media.lib.converter.ConvertResult
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IAwaiter
import io.github.toyota32k.media.lib.converter.IProgress
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.utils.UtLog
import io.github.toyota32k.media.ui.theme.AndroidMediaProcessorTheme
import kotlinx.coroutines.launch

class MainViewModel(savedStateHandle: SavedStateHandle): ViewModel() {
    val inputUri = savedStateHandle.getLiveData<Uri>("inputUri", Uri.EMPTY)
    val outputUri = savedStateHandle.getLiveData<Uri>("outputUri", Uri.EMPTY)
    val trimStart = savedStateHandle.getLiveData<Long>("trimStart", 0L)
    val trimEnd = savedStateHandle.getLiveData<Long>("trimEnd", 0L)

    val processing = MutableLiveData<Boolean>(false)
    val progress = MutableLiveData<String>()
    val message = MutableLiveData<String>("")
    var job: IAwaiter<ConvertResult>? = null

    var repeat:Int = 0
    private fun onProgress(p: IProgress) {
        val percent = p.percentage
        val remain = p.remainingTime / 1000
        val min = remain / 60
        val sec = remain % 60
        progress.value = if(percent>0) {
            if(remain>0) {
                "<$repeat> $percent % (ramain = $min min $sec sec)"
            } else {
                "<$repeat> $percent %"
            }
        } else {
            (p.current / 1000L).toString() + " ms"
        }
    }

    /**
     * コンバートを１００回繰り返す耐久テスト用
     */
    fun convert__(context:Context) {
            if(job!=null) return
            val input = inputUri.value ?: return
            val output = outputUri.value ?: return
            processing.value = true
            message.value = "Processing..."

            viewModelScope.launch {
                for(i in 0..100) {
                    repeat = i+1
                    val result = Converter.factory
                        .input(input, context)
                        .output(output, context)
                        .setProgressHandler(this@MainViewModel::onProgress)
                        .videoStrategy(PresetVideoStrategies.AVC720LowProfile)
//                        .trimmingStartFrom(trimStart.value ?: 0L)
//                        .trimmingEndTo(trimEnd.value ?: 0L)
                        .executeAsync(viewModelScope).apply { job = this }
                        .await()
                    progress.value = ""
                    job = null
                    message.value = when {
                        result.succeeded -> "Completed"
                        result.cancelled -> "Cancelled"
                        else -> result.errorMessage
                    }
                    if(!result.succeeded) {
                        break
                    }
                }
                processing.value = false
            }
    }

    /**
     * 普通のコンバートテスト用
     */
    fun convert(context: Context) {
        if(job!=null) return
        val input = inputUri.value ?: return
        val output = outputUri.value ?: return
        processing.value = true
        message.value = "Processing..."
        repeat = 1
        viewModelScope.launch {
            val result = Converter.factory
                .input(input, context)
                .output(output, context)
                .setProgressHandler(this@MainViewModel::onProgress)
                .audioStrategy(PresetAudioStrategies.AACDefault)
                .videoStrategy(PresetVideoStrategies.AVC720LowProfile)
                .trimmingStartFrom(trimStart.value?:0L)
                .trimmingEndTo(trimEnd.value?:0L)
                .executeAsync(viewModelScope).apply { job = this }
                .await()
            processing.value = false
            progress.value = ""
            job = null
            message.value = when {
                result.succeeded -> "Completed"
                result.cancelled -> "Cancelled"
                else -> result.errorMessage
            }
        }
    }

    fun cancel() {
        job?.cancel()
        message.value = "Cancelling..."
    }

    override fun onCleared() {
        super.onCleared()
    }

    companion object {
        fun instanceFor(activity: MainActivity):MainViewModel {
            return ViewModelProvider(activity, SavedStateViewModelFactory(activity.application, activity)).get(MainViewModel::class.java)
        }
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        val logger = UtLog("Main")
    }
    lateinit var viewModel: MainViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = MainViewModel.instanceFor(this)
        setContent {
            AndroidMediaProcessorTheme { // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    MainPanel(viewModel)
                }
            }
        }
    }

    private val openInputFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri->
        if(uri!=null) {
            viewModel.message.value = ""
            viewModel.inputUri.value = uri
        }
    }
    private val openOutputFile = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        if(uri!=null) {
            viewModel.message.value = ""
            viewModel.outputUri.value = uri
        }
    }

//    private fun onOutputFileSelected(outputUri: Uri) {
//        val inputUri = sourceUri ?: return
//        CoroutineScope(Dispatchers.Default).launch {
//            Converter(AndroidFile(inputUri, this@MainActivity), AndroidFile(outputUri, this@MainActivity)).use { transcoder ->
//                transcoder.trimming(10000, 20000)
//            }
//        }

//        future = executor.submit {
//            try {
//                Converter(MediaFile(inputUri, this@MainActivity), MediaFile(outputUri, this@MainActivity)).use { transcoder ->
//                    transcoder.transcode()
//                    future = null
//                }
//            } catch(e:Throwable) {
//                logger.stackTrace(e)
//                future = null
//            }
//        }

//    }

//    val executor = ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable -> Thread(runnable, "transcoder-thread") }
//    var future: Future<*>? = null

    fun Uri?.isValid():Boolean {
        return this != null && this != Uri.EMPTY
    }

//    private fun onMediaFileSelected(uri: Uri) {
//        sourceUri = uri
//        selectOutputFile.launch("Output File")
//    }

    @Composable
    fun UriDisplay(uri:Uri?) {
        if(uri!=null && uri!=Uri.EMPTY) {
            Text("$uri")
        }
    }
    @Composable
    fun TrimmingPosition(label:String, value:Long, onChange:(String)->Unit) {
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.Start) {
            Text("$label:", Modifier.width(100.dp))
            TextField(value = value.toString(), onValueChange = onChange, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number) )
        }
    }

    @Composable
    fun MainPanel(viewModel: MainViewModel) {
//        val ld = MutableLiveData<String>()
//        val ld = MutableLiveData<Uri?>()
        val input by viewModel.inputUri.observeAsState()
        val output by viewModel.outputUri.observeAsState()
        val trimStart by viewModel.trimStart.observeAsState()
        val trimEnd by viewModel.trimEnd.observeAsState()
        val processing by viewModel.processing.observeAsState()
        val message by viewModel.message.observeAsState()
        val progress by viewModel.progress.observeAsState()

        Column(modifier= Modifier.padding(5.dp)) {
            Button(onClick= {openInputFile.launch("video/*") }) {
                Text("Input Video File")
            }
            UriDisplay(input)
            Spacer(Modifier.height(5.dp))
            Button(onClick={openOutputFile.launch("output.mp4")}) {
                Text("Output Video File")
            }
            UriDisplay(output)

            if(input.isValid()&&output.isValid()) {
                TrimmingPosition(label = "Start", value = trimStart?:0L) {
                    viewModel.trimStart.value = try { it.toLong() } catch (_:Throwable) { 0L }
                }
                TrimmingPosition(label = "End", value = trimEnd?:0) {
                    viewModel.trimEnd.value = try { it.toLong() } catch (_:Throwable) { 0L }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Button(onClick={viewModel.convert(this@MainActivity)}, enabled = processing==false) {
                    Text("Convert")
                }
            }
            Text(message?:"")

            if(processing==true) {
                Button(onClick={viewModel.cancel()}) {
                    Text("Cancel")
                }
                if(progress!=null) {
                    Text("  ${progress}")
                }
            }



//            Spacer(Modifier.height(5.dp))
//            Button(onClick=this@MainActivity::unregister) {
//                Text("Unregister")
//            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        AndroidMediaProcessorTheme {
            MainPanel(MainViewModel(SavedStateHandle()))
        }
    }
}

