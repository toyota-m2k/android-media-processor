package io.github.toyota32k.media.lib.converter

interface IAwaiter<T> {
    suspend fun await():T
    fun cancel()
}