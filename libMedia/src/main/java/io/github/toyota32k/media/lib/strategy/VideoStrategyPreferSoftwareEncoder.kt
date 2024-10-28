package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList

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
        fun supportedCodec(optionalFilter:(MediaCodecInfo)->Boolean): List<MediaCodecInfo> {
            return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter {
                    it.isEncoder
                    && optionalFilter(it)
                    && getCapabilitiesOf(it)?.profileLevels?.find { pl -> pl.profile == profile.value && (maxLevel == null || pl.level >= maxLevel.value) }!=null
                }
        }

        var codec = supportedCodec { isSoftwareOnly(it) }.firstOrNull()     //
            ?: supportedCodec { !isHardwareAccelerated(it) }.firstOrNull()  // software only ではないが、hardware accelerated ででもない？
        return if(codec!=null) {
            logger.info("using software encoder: ${codec.name}")
            MediaCodec.createByCodecName(codec.name)
        } else {
            logger.info("using default encoder")
            super.createEncoder()
        }
    }
}