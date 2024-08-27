package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.utils.UtLog

interface IStrategy {
    fun createEncoder(): MediaCodec
    companion object {
        val logger = UtLog("Strategy", Converter.logger)
    }
}

interface IAudioStrategy : IStrategy {
    fun createOutputFormat(inputFormat: MediaFormat, encoder:MediaCodec): MediaFormat
}
interface IVideoStrategy : IStrategy {
    fun createOutputFormat(inputFormat: MediaFormat, metaData: MetaData, encoder:MediaCodec): MediaFormat
}

