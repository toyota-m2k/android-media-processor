package io.github.toyota32k.media.lib.track

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.codec.VideoDecoder
import io.github.toyota32k.media.lib.codec.VideoEncoder
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.mime
import io.github.toyota32k.media.lib.misc.ICancellation
import io.github.toyota32k.media.lib.misc.MediaConstants
import io.github.toyota32k.media.lib.report.Report
import io.github.toyota32k.media.lib.strategy.DeviceCapabilities
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.strategy.VideoStrategy.Companion.logger
import io.github.toyota32k.media.lib.surface.RenderOption

class VideoTrack
    private constructor(strategy:IVideoStrategy, extractor:Extractor, inputFormat:MediaFormat, decoder: MediaCodec, outputFormat:MediaFormat, encoder: MediaCodec, renderOption:RenderOption, report: Report, val metaData: MetaData, cancellation: ICancellation)
        : Track(extractor, Muxer.SampleType.Video,cancellation) {

    // 必ず、Encoder-->Decoder の順に初期化＆開始する。そうしないと、Decoder側の inputSurfaceの初期化に失敗する。
    override val encoder: VideoEncoder = VideoEncoder(strategy, outputFormat, encoder,report,cancellation).apply { start() }
    override val decoder: VideoDecoder = VideoDecoder(strategy, inputFormat, decoder, renderOption, report, cancellation).apply { start() }

    companion object {
        private fun createSoftwareDecoder(inputFormat: MediaFormat):MediaCodec {
            fun isSoftwareOnly(info: MediaCodecInfo): Boolean {
                return DeviceCapabilities.isSoftwareOnly(info)
            }
            fun isSupportedType(info: MediaCodecInfo, mime: String): Boolean {
                return DeviceCapabilities.capabilities(info,mime) != null
            }
            val supported = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter {
                    !it.isEncoder
                    && isSoftwareOnly(it)
                    && isSupportedType(it,inputFormat.mime!!)
                }
            var codec = supported.firstOrNull()
            return if(codec!=null) {
                logger.info("using software decoder: ${codec.name}")
                MediaCodec.createByCodecName(codec.name)
            } else {
                MediaCodec.createDecoderByType(inputFormat.mime!!).apply {
                    logger.info("no software decoder --> default decoder: $name")
                }
            }
        }

        fun create(inPath: IInputMediaFile, strategy: IVideoStrategy, report: Report, cancellation: ICancellation, preferSoftwareDecode:Boolean, renderOption: RenderOption) : VideoTrack {
            val extractor = Extractor.create(inPath, Muxer.SampleType.Video, cancellation)
            val metaData = MetaData.fromFile(inPath)
//            val trackIdx = findTrackIdx(extractor.extractor, "video")
            if (extractor==null) {
                UtLog("Track(Video)", Converter.logger).info("no video truck")
                throw UnsupportedOperationException("no video track")
            }
            val inputFormat = extractor.getMediaFormat()
            if (inputFormat.containsKey(MediaConstants.KEY_ROTATION_DEGREES)) { // Decoded video is rotated automatically in Android 5.0 lollipop.
                // Turn off here because we don't want to encode rotated one.
                // refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
                inputFormat.setInteger(MediaConstants.KEY_ROTATION_DEGREES, 0)
            }
            val decoder = if(preferSoftwareDecode) {
                createSoftwareDecoder(inputFormat)
            } else {
                MediaCodec.createDecoderByType(inputFormat.mime!!).apply {
                    logger.info("using default decoder :$name")
                }
            }

            val encoder = strategy.createEncoder()
            val outputFormat = strategy.createOutputFormat(inputFormat, metaData, encoder, renderOption)

            report.updateVideoStrategyName(strategy.name)
            report.updateVideoDecoder(decoder)
            report.updateVideoEncoder(encoder)
            report.updateInputSummary(inputFormat, metaData)
            report.updateOutputSummary(outputFormat)

            return VideoTrack(strategy, extractor, inputFormat, decoder, outputFormat, encoder, renderOption, report, metaData, cancellation)
        }
    }
}

