package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.getBitRate
import io.github.toyota32k.media.lib.format.getChannelCount
import io.github.toyota32k.media.lib.format.getMime
import io.github.toyota32k.media.lib.format.getSampleRate
import io.github.toyota32k.utils.UtLog

open class AudioStrategy (
    codec:Codec,
    profile: Profile,                            // profile は AAC Profile として扱う。AAC以外のAudioコーデックは知らん。
    fallbackProfiles: Array<Profile>?,
    val sampleRate:MaxDefault,
    val channelCount: MaxDefault,
    val bitRatePerChannel:MaxDefault,         //      // 1ch当たりのビットレート
) : AbstractStrategy(codec, profile, null, fallbackProfiles), IAudioStrategy {

    override fun createOutputFormat(inputFormat: MediaFormat, encoder:MediaCodec): MediaFormat {
        val sampleRate = this.sampleRate.value(inputFormat.getSampleRate())
        val channelCount = this.channelCount.value(inputFormat.getChannelCount())
        val inputBitRatePerChannel = (inputFormat.getBitRate()?:0)/channelCount
        val bitRate = this.bitRatePerChannel.value(inputBitRatePerChannel) * channelCount

        VideoStrategy.logger.info("Audio Format ------------------------------------------------------")
        VideoStrategy.logger.info("- Type           ${inputFormat.getMime()?:"n/a"} --> ${codec.mime}")
        VideoStrategy.logger.info("- Profile        ${Profile.fromFormat(inputFormat)?:"n/a"} --> $profile")
        VideoStrategy.logger.info("- SampleRate     ${inputFormat.getSampleRate()} --> $sampleRate")
        VideoStrategy.logger.info("- Channels       ${inputFormat.getChannelCount()?:"n/a"} --> $channelCount")
        VideoStrategy.logger.info("- BitRate        ${inputFormat.getBitRate()?:"n/a"} --> $bitRate")
        VideoStrategy.logger.info("-------------------------------------------------------------------")

        return MediaFormat.createAudioFormat(codec.mime, sampleRate, channelCount).apply {
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

    companion object {
        val logger = UtLog("Audio", IStrategy.logger)
    }
}