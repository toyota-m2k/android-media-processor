package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.utils.UtLog
import java.lang.NullPointerException

interface IStrategy {
    fun createEncoder(): MediaCodec
    fun createOutputFormat(inputFormat: MediaFormat, encoder:MediaCodec): MediaFormat
    companion object {
        val logger = UtLog("Strategy", Converter.logger)
    }
}

interface IAudioStrategy : IStrategy
interface IVideoStrategy : IStrategy

