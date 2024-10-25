package io.github.toyota32k.media.lib.misc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive

interface ICancellation {
    val isCancelled: Boolean
}

class CoroutineCancellation(val coroutineScope: CoroutineScope) : ICancellation {
    override val isCancelled: Boolean
        get() = !coroutineScope.isActive
}
