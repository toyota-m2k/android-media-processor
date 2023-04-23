package io.github.toyota32k.media.lib.format

import android.annotation.SuppressLint
import android.media.MediaFormat

@SuppressLint("InlinedApi")
enum class Codec(val media: Media, val mime:String, val alias:String?=null) {
    Invalid(Media.Video, "n/a"),

    AVC(Media.Video, MediaFormat.MIMETYPE_VIDEO_AVC),
    H263(Media.Video, MediaFormat.MIMETYPE_VIDEO_H263),
    MPEG2(Media.Video, MediaFormat.MIMETYPE_VIDEO_MPEG2),
    MPEG4(Media.Video, MediaFormat.MIMETYPE_VIDEO_MPEG4),
    HEVC(Media.Video, MediaFormat.MIMETYPE_VIDEO_HEVC),

    VP8(Media.Video, MediaFormat.MIMETYPE_VIDEO_VP8),
    VP9(Media.Video, MediaFormat.MIMETYPE_VIDEO_VP9),
    DolbyVision(Media.Video, MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION),
    AV1(Media.Video, MediaFormat.MIMETYPE_VIDEO_AV1),


    AAC(Media.Audio, MediaFormat.MIMETYPE_AUDIO_AAC),
    AAC_HE_V1(Media.Audio, MediaFormat.MIMETYPE_AUDIO_AAC_HE_V1, MediaFormat.MIMETYPE_AUDIO_AAC),
    AAC_HE_V2(Media.Audio, MediaFormat.MIMETYPE_AUDIO_AAC_HE_V2, MediaFormat.MIMETYPE_AUDIO_AAC),
    AAC_HE_LC(Media.Audio, MediaFormat.MIMETYPE_AUDIO_AAC_LC, MediaFormat.MIMETYPE_AUDIO_AAC),
    AAC_HE_ELD(Media.Audio, MediaFormat.MIMETYPE_AUDIO_AAC_ELD, MediaFormat.MIMETYPE_AUDIO_AAC),
    AAC_HE_XHE(Media.Audio, MediaFormat.MIMETYPE_AUDIO_AAC_XHE, MediaFormat.MIMETYPE_AUDIO_AAC),

    ;

    companion object {
        fun codecOf(type:String): Codec? {
            return values().firstOrNull {
                it.mime.compareTo(type, true) == 0 || it.alias?.compareTo(type, true)==0
            }
        }
        fun codecOf(format: MediaFormat): Codec? {
            val mime = format.getMime()?:return null
            return codecOf(mime)
        }
    }
}