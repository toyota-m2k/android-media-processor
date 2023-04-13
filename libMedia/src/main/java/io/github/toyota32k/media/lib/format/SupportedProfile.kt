package io.github.toyota32k.media.lib.format

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList

data class SupportedProfile(val profile:Int, val level:Int) {
    companion object {
        fun getSupportedCodefInfo(profileLevels: Array<MediaCodecInfo.CodecProfileLevel>, profile:Int/*, level: Int*/) : MediaCodecInfo.CodecProfileLevel? {
            return profileLevels.firstOrNull { it.profile == profile /*&& it.level == level*/ }
        }

        fun getRegularProfile(encoder: MediaCodec, mimeType:String, choices:Array<Int>): SupportedProfile? {
            val profileLevels = encoder.codecInfo.getCapabilitiesForType(mimeType).profileLevels
            val p = choices.mapNotNull { getSupportedCodefInfo(profileLevels, it) }.firstOrNull() ?: return null
            return SupportedProfile(p.profile, p.level)
        }

        const val MimeTypeAVC = "video/avc"
        val profilesAVCLow = arrayOf(MediaCodecInfo.CodecProfileLevel.AVCProfileMain,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)

        fun getRegularProfileOfAVC(encoder: MediaCodec):SupportedProfile? {
            return getRegularProfile(encoder, MimeTypeAVC, profilesAVCLow)
        }
    }
}