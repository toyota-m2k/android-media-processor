package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

class VideoStrategyPreferSoftwareEncoder(baseStrategy: VideoStrategy) : VideoStrategy(
    baseStrategy.codec,
    baseStrategy.profile,
    baseStrategy.maxLevel,
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
            false   // どうせわからんからなんでも false
        }
    }

    override fun createEncoder(): MediaCodec {
//        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
//        for (info in list) {
//            if (info.isEncoder && isSoftwareEncoder(info)) {
//                try {
//                    val cap = info.getCapabilitiesForType(codec.mime)
//                    val pl = cap.profileLevels.find { pl -> pl.profile == profile.value && (level == null || pl.level > level.value) }
//                    if (pl != null) {
//                        return MediaCodec.createByCodecName(info.name)
//                    }
//                } catch (e: Exception) {
//                    logger.error(e)
//                }
//            }
//        }
        val supported = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter {
                it.isEncoder
                && isSoftwareEncoder(it)
            }
        var codec = supported.firstOrNull { getCapabilitiesOf(it)?.profileLevels?.find { pl -> pl.profile == profile.value && (maxLevel == null || pl.level >= maxLevel.value) }!=null}
        return if(codec!=null) {
            MediaCodec.createByCodecName(codec.name)
        } else {
            super.createEncoder()
        }
    }
}