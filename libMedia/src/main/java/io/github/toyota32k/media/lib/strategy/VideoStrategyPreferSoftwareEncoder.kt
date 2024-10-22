package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

class VideoStrategyPreferSoftwareEncoder(baseStrategy: VideoStrategy) : VideoStrategy(
    baseStrategy.codec,
    baseStrategy.profile,
    baseStrategy.level,
    baseStrategy.fallbackProfiles,
    baseStrategy.sizeCriteria,
    baseStrategy.bitRate,
    baseStrategy.frameRate,
    baseStrategy.iFrameInterval,
    baseStrategy.colorFormat,
    baseStrategy.bitRateMode,
) {
    private fun isSoftwareEncoder(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            !info.isHardwareAccelerated
        } else {
            false
        }
    }

    override fun createEncoder(): MediaCodec {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        for (info in list) {
            if (info.isEncoder && isSoftwareEncoder(info)) {
                try {
                    val cap = info.getCapabilitiesForType(codec.mime)
                    val pls = cap.profileLevels
                    for (pl in pls) {
                        if (pl.profile == profile.value && (level == null || pl.level > level.value)) {
                            return MediaCodec.createByCodecName(info.name)
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e)
                }
            }
        }
        val supported = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter {
                it.isEncoder
                && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) !it.isHardwareAccelerated else true
                && it.getCapabilitiesForType(codec.mime).profileLevels.find {pl->pl.profile==profile.value && (level==null || pl.level>level.value) } != null
            }
        val codec = supported.firstOrNull() ?: throw IllegalStateException("no encoder found")
        return MediaCodec.createByCodecName(codec.name)
    }
}