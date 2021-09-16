package io.github.toyota32k.media.lib.converter

import io.github.toyota32k.media.lib.track.Muxer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class Progress(muxer: Muxer, val onProgress:((IProgress)->Unit)?): IProgress {
    val durationUs: Long = muxer.durationUs
    var audioProgressInUs: Long = 0L
        set(v) {
            if (field < v) {
                field = v
                progressInUs = min(videoProgressInUs, v)
            }
        }
    var videoProgressInUs: Long = 0L
        set(v) {
            if (field < v) {
                field = v
                progressInUs = min(audioProgressInUs, v)
            }
        }
    fun finish() {
        if(percent!=100) {
            percent = 100
            notifyProgerss()
        }
    }

    private var busy = AtomicBoolean(false)
    fun notifyProgerss() {
        val onProgress = this.onProgress?:return
        if(busy.get()) return
        CoroutineScope(Dispatchers.Main).launch {
            busy.set(true)
            onProgress(this@Progress)
            busy.set(false)
        }
    }

    var progressInUs: Long = 0L
        set(v) {
            if (field < v) {
                field = v
                if(durationUs>0) {
                    val p = max(0, min(100, (v * 100L / durationUs).toInt()))
                    if (percent<p) {
                        percent = p
                        notifyProgerss()
                    }
                } else {
                    notifyProgerss()
                }
            }
        }

    override var percent: Int = 0
        private set
    override val total: Long
        get() = durationUs
    override val current: Long
        get() = progressInUs
}