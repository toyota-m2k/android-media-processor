package io.github.toyota32k.media.lib.converter

import io.github.toyota32k.utils.IUtExternalLogger

/**
 * 利用側で、android-utilities への依存関係を持たなくてよいように、IUtExternalLogger 互換のi/fを再定義する。
 */
interface IAmpLogger {
    fun debug(msg:String)
    fun warn(msg:String)
    fun error(msg:String)
    fun info(msg:String)
    fun verbose(msg:String)
}

fun IAmpLogger.asUtExternalLogger(): IUtExternalLogger
    = object: IUtExternalLogger {
        override fun debug(msg: String) = this@asUtExternalLogger.debug(msg)
        override fun warn(msg: String) = this@asUtExternalLogger.warn(msg)
        override fun error(msg: String) = this@asUtExternalLogger.error(msg)
        override fun info(msg: String) = this@asUtExternalLogger.info(msg)
        override fun verbose(msg: String) = this@asUtExternalLogger.verbose(msg)
    }
