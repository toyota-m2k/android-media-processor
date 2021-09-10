package io.github.toyota32k.media

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.toyota32k.media.lib.Transcoder
import io.github.toyota32k.media.lib.Transcoder.Companion.logger
import io.github.toyota32k.media.lib.misc.MediaFile
import io.github.toyota32k.media.ui.theme.AndroidMediaProcessorTheme
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    companion object {
        var sourceUri:Uri? = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidMediaProcessorTheme { // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    MainPanel()
                }
            }
        }
    }

    private val openMediaFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri->
        if(uri!=null) {
            onMediaFileSelected(uri)
        }
    }
    private val selectOutputFile = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        if (uri != null) {
            onOutputFileSelected(uri)
        }
    }

    private fun onOutputFileSelected(outputUri: Uri) {
        val inputUri = sourceUri ?: return
        future = executor.submit {
            try {
                Transcoder(MediaFile(inputUri, this@MainActivity), MediaFile(outputUri, this@MainActivity)).use { transcoder ->
                    transcoder.convert()
                    future = null
                }
            } catch(e:Throwable) {
                logger.stackTrace(e)
                future = null
            }
        }

    }

    val executor = ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable -> Thread(runnable, "transcoder-thread") }
    var future: Future<*>? = null

    private fun onMediaFileSelected(uri: Uri) {
        sourceUri = uri
        selectOutputFile.launch("Output File")
    }


    @Composable
    fun MainPanel() {
        Column(modifier= Modifier.padding(5.dp)) {
            Button(onClick= {openMediaFile.launch("video/*") }) {
                Text("Open Video File")
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
            MainPanel()
        }
    }
}

