package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.bitRate
import io.github.toyota32k.media.lib.format.channelCount
import io.github.toyota32k.media.lib.format.mime
import io.github.toyota32k.media.lib.format.sampleRate

open class AudioStrategy (
    codec:Codec,
    profile: Profile,                            // profile は AAC Profile として扱う。AAC以外のAudioコーデックは知らん。
    fallbackProfiles: Array<ProfileLv>?,
    override val sampleRate:MaxDefault,
    override val channelCount: Int,      // 1 or 2,  ... 0なら入力と同じチャネル数で出力
    override val bitRatePerChannel:MaxDefault,         //      // 1ch当たりのビットレート
) : AbstractStrategy(codec, profile, null, fallbackProfiles), IAudioStrategy {

    /**
     * 既存のAudioStrategyから、必要なパラメータを書き換えて新しいAudioStrategyを作成する。
     */
    override fun derived(
        codec:Codec,
        profile: Profile,
        fallbackProfiles: Array<ProfileLv>?,
        sampleRate:MaxDefault,
        channelCount: Int,
        bitRatePerChannel:MaxDefault,
    ) : IAudioStrategy {
        return AudioStrategy(codec, profile, fallbackProfiles, sampleRate, channelCount, bitRatePerChannel)
    }

    override fun resolveOutputChannelCount(inputFormat: MediaFormat): Int {
        return if(this.channelCount==0) {
            inputFormat.channelCount?:1
        } else {
            this.channelCount
        }
    }

    override fun resolveOutputSampleRate(inputFormat: MediaFormat, inputChannelCount:Int, outputChannelCount:Int): Int {
        val inputBitRatePerChannel = (inputFormat.bitRate?:0)/inputChannelCount
        return this.bitRatePerChannel.value(inputBitRatePerChannel) * outputChannelCount
    }

    override fun createOutputFormat(inputFormat: MediaFormat, encoder:MediaCodec): MediaFormat {
        val sampleRate = this.sampleRate.value(inputFormat.sampleRate)
        val channelCount = resolveOutputChannelCount(inputFormat)
//        val inputBitRatePerChannel = (inputFormat.bitRate?:0)/channelCount
        val bitRate = resolveOutputSampleRate(inputFormat, inputFormat.channelCount?:1, channelCount)

        VideoStrategy.logger.info("Audio Format ------------------------------------------------------")
        VideoStrategy.logger.info("- Type           ${inputFormat.mime?:"n/a"} --> ${codec.mime}")
        VideoStrategy.logger.info("- Profile        ${Profile.fromFormat(inputFormat)?:"n/a"} --> $profile")
        VideoStrategy.logger.info("- SampleRate     ${inputFormat.sampleRate?:"n/a"} --> $sampleRate")
        VideoStrategy.logger.info("- Channels       ${inputFormat.channelCount?:"n/a"} --> $channelCount")
        VideoStrategy.logger.info("- BitRate        ${inputFormat.bitRate?:"n/a"} --> $bitRate")
        VideoStrategy.logger.info("-------------------------------------------------------------------")

        return MediaFormat.createAudioFormat(codec.mime, sampleRate, this.channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, profile.value)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        }
    }


    //
    // AACObjectMain       = 1;      最初に開発されたプロファイルで、高い音質を提供しますが、再生負荷が大きい
    // AACObjectLC         = 2;      最も基本的なプロファイルで、低い計算量で高品質な音声を提供します。
    // AACObjectSSR        = 3;      周波数帯域の拡張性を持たせるため、4つの帯域に分割して符号化する方式です。同じビットストリームから帯域の異なる復号結果を得ることができます。
    // AACObjectLTP        = 4;      予測符号化の精度を向上させるため、長期的な信号の相関を利用する方式です。AAC-Mainよりも再生負荷が小さい
    // AACObjectHE         = 5;      AAC-LCにSBR（Spectral Band Replication）という技術を追加したプロファイルで、低いビットレートでも高品質な音声を提供
    // AACObjectScalable   = 6;      ビットレートの拡張性を持たせるため、複数のサブストリームに分割して符号化する方式です。同じビットストリームからビットレートの異なる復号結果を得ることができます。
    // AACObjectERLC       = 17;     エラー耐性を持たせるため、パケット損失やビットエラーに対応する技術を追加したプロファイル
    // AACObjectERScalable = 20;     エラー耐性とビットレートの拡張性を両立させるため、AAC-Scalableにエラー耐性技術を追加したプロファイル
    // AACObjectLD         = 23;     遅延時間を短くするため、フレーム長やフィルタバンクなどを最適化したプロファイルです。主に音声通信に使用されます。
    // AACObjectHE_PS      = 29;     HE-AACにPS（Parametric Stereo）という技術を追加したプロファイルで、さらに低いビットレートでも高品質なステレオ音声を提供します。
    // AACObjectELD        = 39;     AAC-LDにSBRやPSなどの技術を追加したプロファイルで、さらに遅延時間を短くしながら高品質な音声を提供します。主に高度な音声通信に使用されます。
    // AACObjectXHE        = 42;     HE-AAC v2にMPEG-D USACという技術を追加したプロファイルで、最も高い圧縮効率と音質を提供します。主にストリーミングや放送などの用途に使用されます。

    fun dumpCodecs() {
        logger.info("#### Dump Audio Codecs ####")
        logger.info(DeviceCapabilities.availableCodecs(codec, encoder = true).toString())
        logger.info(DeviceCapabilities.availableCodecs(codec, encoder = false).toString())
    }

    companion object {
        val logger = UtLog("Audio", IStrategy.logger)
    }
}