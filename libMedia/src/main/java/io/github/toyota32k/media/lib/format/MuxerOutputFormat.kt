package io.github.toyota32k.media.lib.format

import android.media.MediaMuxer

enum class ContainerFormat(val mof: Int) {
    MPEG_4(MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),
    WEBM(MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM),
    THREE_GPP(MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP),
    HEIF(MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF),
    OGG(MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG),
}