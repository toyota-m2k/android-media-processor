package io.github.toyota32k.media.lib.converter

/**
 * キャンセル可能な待ち合わせ用 i/f
 */
interface IAwaiter<T> {
    suspend fun await():T
    fun cancel()
}