package io.github.toyota32k.media.lib.track

import android.media.MediaFormat
import io.github.toyota32k.media.lib.codec.VideoDecoder
import io.github.toyota32k.media.lib.codec.VideoEncoder
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.extractor.Extractor
import io.github.toyota32k.media.lib.format.IVideoStrategy
import io.github.toyota32k.media.lib.misc.MediaConstants
import io.github.toyota32k.media.lib.utils.UtLog
import java.lang.UnsupportedOperationException

class VideoTrack
    private constructor(extractor:Extractor, inputFormat:MediaFormat, strategy: IVideoStrategy, trackIdx:Int)
        : Track(extractor, inputFormat, strategy.createOutputFormat(inputFormat), trackIdx, Muxer.SampleType.Video) {

    // 必ず、Encoder-->Decoder の順に初期化＆開始する。そうしないと、Decoder側の inputSurfaceの初期化に失敗する。
    override val encoder: VideoEncoder = VideoEncoder(outputFormat).apply { start() }
    override val decoder: VideoDecoder = VideoDecoder(inputFormat).apply { start() }

    companion object {
        fun create(inPath: AndroidFile, strategy: IVideoStrategy) : VideoTrack {
            val extractor = Extractor(inPath)
            val trackIdx = findTrackIdx(extractor.extractor, "video")
            if (trackIdx < 0) {
                UtLog("Track(Video)", Converter.logger, ).info("no video truck")
                throw UnsupportedOperationException("no video track")
            }
            val inputFormat = getMediaFormat(extractor.extractor, trackIdx)
            if (inputFormat.containsKey(MediaConstants.KEY_ROTATION_DEGREES)) { // Decoded video is rotated automatically in Android 5.0 lollipop.
                // Turn off here because we don't want to encode rotated one.
                // refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
                inputFormat.setInteger(MediaConstants.KEY_ROTATION_DEGREES, 0)
            }
            return VideoTrack(extractor, inputFormat, strategy, trackIdx)
        }
    }
}

