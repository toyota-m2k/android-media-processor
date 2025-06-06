package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.Profile

interface IStrategy {
    val name:String
    val codec: Codec
    val profile: Profile
    fun createEncoder(): MediaCodec
    companion object {
        val logger = UtLog("Strategy", Converter.logger)
    }
}

interface IAudioStrategy : IStrategy {
    fun createOutputFormat(inputFormat: MediaFormat, encoder:MediaCodec): MediaFormat
    fun resolveOutputChannelCount(inputFormat: MediaFormat):Int
    fun resolveOutputSampleRate(inputFormat: MediaFormat, inputChannelCount:Int, outputChannelCount:Int):Int
}
interface IVideoStrategy : IStrategy {
    fun createOutputFormat(inputFormat: MediaFormat, metaData: MetaData, encoder:MediaCodec): MediaFormat
}

