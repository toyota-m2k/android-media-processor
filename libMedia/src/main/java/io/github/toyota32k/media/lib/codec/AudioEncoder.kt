package io.github.toyota32k.media.lib.codec

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.format.channelCount
import io.github.toyota32k.media.lib.format.sampleRate
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.track.Muxer
import io.github.toyota32k.utils.UtLog

class AudioEncoder(strategy: IAudioStrategy, format: MediaFormat, encoder: MediaCodec, report: Report, cancellation: ICancellation)
    :BaseEncoder(strategy, format, encoder, report, cancellation)  {
    override val sampleType = Muxer.SampleType.Audio
    override val logger = UtLog("Encoder(Audio)", Converter.logger)

    // #17124
    //  前提として、このコンバーターでは、現在、音声トラックのトランスコーダーは、サンプリングレートの変更はサポートしていない。
    //  通常は、inputFormatのサンプリングレートを outputFormat にセットして MediaCodecをconfigureすることで、input/outputのサンプルレートを一致させていた。
    //  しかし、メタ情報に書かれているサンプリングレートと、音声トラックを読み込んでデコーダーが検出した「真の」サンプリングレートが異なる動画ファイルが見つかり、
    //  結果的に、音声データを（真のサンプリングレートと）異なるサンプリングレートでファイルに書き込んでしまい、間違ったdurationの音声トラックができてしまう。
    //  これを回避するには、サンプリングレートの変換をちゃんと実装するか、あるいは、EncoderのMediaCodecを、真のサンプリングレートで Configure する必要がある。
    //  前者は将来の課題として棚上げするとして、とりあえず後者を実装する。
    //  具体的には、初期化時にはまだ、start/configure しないで、音声トラックから（真の）サンプリングレートが読み込めた時点で start/configureするように修正した。

    /**
     * 真のサンプリングレート&チャネル数が判明したとき（デコーダーがMediaCodec.INFO_OUTPUT_FORMAT_CHANGEDを受け取った時）に、
     * エンコーダーを configure / start する。
     */
    fun configureWithActualSampleRate(sampleRate:Int?, channelCount:Int?) {
        var modified = false
        if(sampleRate!=null && sampleRate != mediaFormat.sampleRate) {
            mediaFormat.sampleRate = sampleRate
            modified = true
        }
        if(channelCount!=null && channelCount != mediaFormat.channelCount) {
            if(channelCount!=1 && channelCount!=2) {
                throw UnsupportedOperationException("Input channel count ($channelCount) not supported.")
            }
            mediaFormat.channelCount = channelCount
            modified = true
        }
        report.updateOutputSummary(mediaFormat)
        start()
        logger.debug("AudioEncoder configured.")
    }
}
