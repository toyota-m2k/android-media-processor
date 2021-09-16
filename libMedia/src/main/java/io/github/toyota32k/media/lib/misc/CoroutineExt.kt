package io.github.toyota32k.media.lib.misc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive

fun CoroutineScope?.check() {
    if(this==null) return
    if(!this.isActive) throw CancellationException("cancelled")
}

