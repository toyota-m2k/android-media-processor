package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Size
import io.github.toyota32k.media.lib.utils.UtLog
import kotlin.math.min
import kotlin.math.roundToInt

open class VideoStrategy(
    mimeType: String,
    profile:Int,
    level:Int,
    fallbackProfiles: Array<Int>? = null,

    val sizeCriteria: SizeCriteria?,
    val bitRate: MaxDefault, // = Int.MAX_VALUE,
    val frameRate: MaxDefault, // = Int.MAX_VALUE,
    val iFrameInterval:MinDefault, // = DEFAULT_IFRAME_INTERVAL,
    val colorFormat:Int, // = DEFAULT_COLOR_FORMAT,
) : AbstractStrategy(mimeType,profile,level,fallbackProfiles), IVideoStrategy {

    override fun createOutputFormat(inputFormat: MediaFormat): MediaFormat {
        return createOutputFormat(MediaFormatCompat(inputFormat))
    }

    private fun hex(v:Int?):String {
        return if(v!=null) String.format("0x%x",v) else "n/a"
    }

    private fun createOutputFormat(inputFormat: MediaFormatCompat): MediaFormat {
        val bitRate = this.bitRate.value(inputFormat.getBitRate())
        val frameRate = this.frameRate.value(inputFormat.getFrameRate())
        val iFrameInterval = this.iFrameInterval.value(inputFormat.getIFrameInterval())
        var width = inputFormat.getWidth() ?: throw IllegalArgumentException("inputFormat have no size params.")
        var height = inputFormat.getHeight() ?: throw IllegalArgumentException("inputFormat have no size params.")
        if(sizeCriteria!=null) {
            val size = calcVideoSize(width, height, sizeCriteria)
            width = size.width
            height = size.height
        }
        val pl = supportedProfile() ?: throw IllegalStateException("no supported profile.")

        logger.info("-------------------------------------------------------------------")
        logger.info("Video Format")
        logger.info("- Type           ${inputFormat.getMime()?:"n/a"} --> $mimeType")
        logger.info("- Profile        ${hex(inputFormat.getProfile())} --> ${hex(pl.profile)}")
        logger.info("- Level          ${hex(inputFormat.getLevel())} --> ${hex(pl.level)}")
        logger.info("- Width          ${inputFormat.getWidth(0)} --> $width")
        logger.info("- Height         ${inputFormat.getHeight(0)} --> $height")
        logger.info("- BitRate        ${inputFormat.getBitRate()?:"n/a"} --> $bitRate")
        logger.info("- FrameRate      ${inputFormat.getFrameRate()?:"n/a"} --> $frameRate")
        logger.info("- iFrameInterval ${inputFormat.getIFrameInterval()?:"n/a"} --> $iFrameInterval")
        logger.info("- colorFormat    ${hex(inputFormat.getColorFormat())} --> ${hex(colorFormat)}")
        logger.info("-------------------------------------------------------------------")

        return MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_PROFILE, pl.profile)
            setInteger(MediaFormat.KEY_LEVEL, pl.level)
        }
    }

    protected fun supportedProfile(): MediaCodecInfo.CodecProfileLevel? {
        val supported = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
            .flatMap { info->
                try { info.getCapabilitiesForType(mimeType).profileLevels.toList() } catch(_:Throwable) { emptyList()}
            }
            .sortedWith { v1, v2 ->
                val r = v1.profile - v2.profile
                if(r!=0) r else v1.level - v2.level
            }
        fun findProfile(profile:Int, level:Int=0): MediaCodecInfo.CodecProfileLevel? {
            return supported.firstOrNull { it.profile == profile && (level==0 || it.level>=level) }
        }

        var pl = findProfile(profile, level)
        if(pl!=null) return pl

        if(level>0) {
            pl = findProfile(profile, 0)
            if(pl!=null) return pl
        }

        if(fallbackProfiles!=null) {
            for (v in fallbackProfiles) {
                pl = findProfile(profile,0)
                if(pl!=null) return pl
            }
        }
        return null
    }


    data class SizeCriteria(val shortSize:Int, val longSide:Int)

    companion object {
        val logger = UtLog("Video", IStrategy.logger)

        val HD720_S_SIZE = 720
        val HD720_L_SIZE = 1280
        val FHD1080_S_SIZE = 1080
        val FHD1080_L_SIZE = 1920

        fun calcVideoSize(width:Int, height:Int, criteria:SizeCriteria) : Size {
            var r = if (width > height) { // 横長
                min(criteria.longSide.toFloat() / width, criteria.shortSize.toFloat() / height)
            } else { // 縦長
                min(criteria.shortSize.toFloat() / width, criteria.longSide.toFloat() / height)
            }
            if (r > 1) { // 拡大はしない
                r = 1f
            }
            return Size((width * r).roundToInt(), (height * r).roundToInt())
        }
    }
}