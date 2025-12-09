package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.format.BitRateMode
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.strategy.VideoStrategy.EncoderType
import io.github.toyota32k.media.lib.strategy.VideoStrategy.SizeCriteria
import io.github.toyota32k.media.lib.surface.RenderOption

interface IStrategy {
    val name:String
    val codec: Codec
    val profile: Profile
    val maxLevel:Level?
    val fallbackProfiles: Array<ProfileLv>?
    fun createEncoder(): MediaCodec
    companion object {
        val logger = UtLog("Strategy", Converter.logger)
    }
}

interface IAudioStrategy : IStrategy {
    val sampleRate:MaxDefault
    val channelCount: Int      // 1 or 2,  ... 0なら入力と同じチャネル数で出力
    val bitRatePerChannel:MaxDefault         //      // 1ch当たりのビットレート

    fun createOutputFormat(inputFormat: MediaFormat, encoder:MediaCodec): MediaFormat
    fun resolveOutputChannelCount(inputFormat: MediaFormat):Int
    fun resolveOutputSampleRate(inputFormat: MediaFormat, inputChannelCount:Int, outputChannelCount:Int):Int

    fun derived(
        codec:Codec = this.codec,
        profile: Profile = this.profile,                            // profile は AAC Profile として扱う。AAC以外のAudioコーデックは知らん。
        fallbackProfiles: Array<ProfileLv>? = this.fallbackProfiles,
        sampleRate:MaxDefault = this.sampleRate,
        channelCount: Int = this.channelCount,
        bitRatePerChannel:MaxDefault = this.bitRatePerChannel,         //      // 1ch当たりのビットレート
    ): IAudioStrategy
}

interface IVideoStrategy : IStrategy {
    val sizeCriteria: SizeCriteria
    val bitRate: MaxDefault // = Int.MAX_VALUE,
    val frameRate: MaxDefault // = Int.MAX_VALUE,
    val iFrameInterval:MinDefault // = DEFAULT_IFRAME_INTERVAL,
    val colorFormat:ColorFormat? // = DEFAULT_COLOR_FORMAT,
    val bitRateMode: BitRateMode?
    val encoderType: EncoderType

    fun createOutputFormat(inputFormat: MediaFormat, metaData: MetaData, encoder:MediaCodec, renderOption: RenderOption): MediaFormat

    fun derived(
        codec: Codec = this.codec,
        profile: Profile = this.profile,
        level: Level? = this.maxLevel,
        fallbackProfiles:Array<ProfileLv>? = this.fallbackProfiles,
        sizeCriteria: SizeCriteria = this.sizeCriteria,
        bitRate: MaxDefault = this.bitRate, // = Int.MAX_VALUE,
        frameRate: MaxDefault = this.frameRate, // = Int.MAX_VALUE,
        iFrameInterval:MinDefault = this.iFrameInterval, // = DEFAULT_IFRAME_INTERVAL,
        colorFormat:ColorFormat? = this.colorFormat, // = DEFAULT_COLOR_FORMAT,
        bitRateMode: BitRateMode? = this.bitRateMode,
        encoderType: EncoderType = this.encoderType,
    ): IVideoStrategy
}

