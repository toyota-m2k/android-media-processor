package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Size
import io.github.toyota32k.media.lib.format.BitRateMode
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.getBitRate
import io.github.toyota32k.media.lib.format.getBitRateMode
import io.github.toyota32k.media.lib.format.getFrameRate
import io.github.toyota32k.media.lib.format.getHeight
import io.github.toyota32k.media.lib.format.getIFrameInterval
import io.github.toyota32k.media.lib.format.getMime
import io.github.toyota32k.media.lib.format.getWidth
import io.github.toyota32k.utils.UtLog
import kotlin.math.min
import kotlin.math.roundToInt

open class VideoStrategy(
    codec: Codec,
    profile: Profile,
    level: Level? = null,
    levelCritical:Boolean = false,
    fallbackProfiles: Array<ProfileLevel>? = null,

    val sizeCriteria: SizeCriteria?,
    val bitRate: MaxDefault, // = Int.MAX_VALUE,
    val frameRate: MaxDefault, // = Int.MAX_VALUE,
    val iFrameInterval:MinDefault, // = DEFAULT_IFRAME_INTERVAL,
    val colorFormat:ColorFormat?, // = DEFAULT_COLOR_FORMAT,
    val bitRateMode: BitRateMode?,
) : AbstractStrategy(codec,profile,level,levelCritical, fallbackProfiles), IVideoStrategy {

//    override fun createOutputFormat(inputFormat: MediaFormat, encoder: MediaCodec): MediaFormat {
//        return createOutputFormat(MediaFormatCompat(inputFormat), encoder)
//    }

//    private fun hex(v:Int?):String {
//        return if(v!=null) String.format("0x%x",v) else "n/a"
//    }
    /**
     * 既存のVideoStrategy（Preset*とか）から、必要なパラメータを書き換えて、新しいVideoStrategyを作成する。
     */
    fun derived(
        codec: Codec = this.codec,
        profile: Profile = this.profile,
        level: Level? = this.level,
        levelCritical:Boolean = this.levelCritical,
        fallbackProfiles:Array<ProfileLevel>? = this.fallbackProfiles,
        sizeCriteria: SizeCriteria? = this.sizeCriteria,
        bitRate: MaxDefault = this.bitRate, // = Int.MAX_VALUE,
        frameRate: MaxDefault = this.frameRate, // = Int.MAX_VALUE,
        iFrameInterval:MinDefault = this.iFrameInterval, // = DEFAULT_IFRAME_INTERVAL,
        colorFormat:ColorFormat? = this.colorFormat, // = DEFAULT_COLOR_FORMAT,
        bitRateMode: BitRateMode? = this.bitRateMode,
    ):VideoStrategy {
        return VideoStrategy(
            codec,
            profile,
            level,
            levelCritical,
            fallbackProfiles,
            sizeCriteria,
            bitRate,
            frameRate,
            iFrameInterval,
            colorFormat,
            bitRateMode)
    }

    fun preferSoftwareEncoder(): VideoStrategy {
        return VideoStrategyPreferSoftwareEncoder(this)
    }

    override fun createOutputFormat(inputFormat: MediaFormat, metaData: MetaData, encoder: MediaCodec): MediaFormat {
        // bitRate は、MediaFormat に含まれず、MetaDataにのみ含まれるケースがあるようなので、
        // 両方をチェックするようにしてみた。
        val bitRate = this.bitRate.value(inputFormat.getBitRate(), metaData.bitRate)
        val frameRate = this.frameRate.value(inputFormat.getFrameRate(), metaData.frameRate)
        val iFrameInterval = this.iFrameInterval.value(inputFormat.getIFrameInterval())
        var width = inputFormat.getWidth() ?: metaData.width ?: throw IllegalArgumentException("inputFormat have no size params.")
        var height = inputFormat.getHeight() ?: metaData.height ?: throw IllegalArgumentException("inputFormat have no size params.")
        if(sizeCriteria!=null) {
            val size = calcVideoSize(width, height, sizeCriteria)
            width = size.width
            height = size.height
        }
        val pl = supportedProfileByEncoder(encoder) ?: throw IllegalStateException("no supported profile.")
        val brm = if(bitRateMode!=null && isBitrateModeSupported(encoder, bitRateMode)) bitRateMode else null

        logger.info("Video Format ------------------------------------------------------")
        logger.info("- Type           ${inputFormat.getMime()?:"n/a"} --> ${codec.mime}")
        logger.info("- Profile        ${Profile.fromFormat(inputFormat)?:"n/a"} --> ${Profile.fromValue(codec, pl.profile)?:"n/a"}")
        logger.info("- Level          ${Level.fromFormat(inputFormat)} --> ${Level.fromValue(codec,pl.level)?:"n/a"}")
        logger.info("- Width          ${inputFormat.getWidth()?:"n/a"}")
        logger.info("- Width(MT)      ${metaData.width?:"n/a"} --> $width")
        logger.info("- Height         ${inputFormat.getHeight()?:"n/a"}")
        logger.info("- Height(MT)     ${metaData.height?:"n/a"} --> $height")
        logger.info("- BitRate        ${inputFormat.getBitRate()?:"n/a"}")
        logger.info("- BitRate(MT)    ${metaData.bitRate?:"n/a"} --> $bitRate")
        logger.info("- BitRateMode    ${inputFormat.getBitRateMode()?:"n/a"} --> ${brm?:"n/a"}")
        logger.info("- FrameRate      ${inputFormat.getFrameRate()?:"n/a"}")
        logger.info("- FrameRate(MT)  ${metaData.frameRate?:"n/a"} --> $frameRate")
        logger.info("- iFrameInterval ${inputFormat.getIFrameInterval()?:"n/a"} --> $iFrameInterval")
        logger.info("- colorFormat    ${ColorFormat.fromFormat(inputFormat)?:"n/a"} --> ${colorFormat?:"n/a"}")
        logger.info("- Duration       ${metaData.duration?:"n/a"}")
        logger.info("-------------------------------------------------------------------")

        return MediaFormat.createVideoFormat(codec.mime, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, (colorFormat?:ColorFormat.COLOR_FormatSurface).value)
            setInteger(MediaFormat.KEY_PROFILE, pl.profile)
            if (levelCritical && level!=null) {
                setInteger(MediaFormat.KEY_LEVEL, level.value)
            } else {
                setInteger(MediaFormat.KEY_LEVEL, pl.level)
            }
            if(brm!=null) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, brm.value)
            }
//            setInteger(MediaFormat.KEY_MAX_HEIGHT, height)
//            setInteger(MediaFormat.KEY_MAX_WIDTH, width)
        }
    }

    /**
     * エンコーダーがサポートしている Profile/Level のリストを返す。
     */
    protected fun supportedProfileByEncoder(encoder: MediaCodec): MediaCodecInfo.CodecProfileLevel? {
        val cap = capabilities(encoder) ?: return null
        val supported = cap.profileLevels
            .sortedWith { v1, v2 ->
                val r = v1.profile - v2.profile
                if(r!=0) r else v1.level - v2.level
            }
        IStrategy.logger.info("Supported Profiles by [${encoder.name}] ---")
        supported.forEach { IStrategy.logger.info("  ${Profile.fromValue(codec, it.profile)?:"?"}@${Level.fromValue(codec, it.level)?:"?"}") }
        IStrategy.logger.info("-------------------------------------------")
        return selectMostSuitableProfile(supported)
    }

    fun dumpCodecs() {
        dumpEncodingCodecs(codec)
    }

    /**
     * Profile/Levelリストの中から、VideoStrategyに対して、最も適切なProfileLevelを返す。
     * これがEncoderのMediaFormatに設定するパラメータとなる。
     */
    protected fun selectMostSuitableProfile(supported: List<MediaCodecInfo.CodecProfileLevel>) : MediaCodecInfo.CodecProfileLevel? {
        fun findProfile(profile:Profile, level:Level?): MediaCodecInfo.CodecProfileLevel? {
            return supported.firstOrNull { it.profile == profile.value && (level==null || it.level>=level.value) }
        }

        var pl = findProfile(profile, level)
        if(pl!=null) {
            // profile / level ともに一致するものが見つかった
            return pl
        }

        if(level!=null||!levelCritical) {
            pl = findProfile(profile, null)
            if(pl!=null) {
                // レベルは無視してプロファイルだけが一致するものを見つけた
                return pl
            }
        }

        // 以上で、対応するプロファイルが見つからなければ、
        // フォールバックプロファイルに一致するものを探す
        if(fallbackProfiles!=null) {
            var hasLevel = false
            for (v in fallbackProfiles) {
                hasLevel = hasLevel || v.level!=null
                pl = findProfile(v.profile, v.level)
                if(pl!=null) {
                    return pl
                }
            }
            if (hasLevel && !levelCritical) {
               // levelCriticalでなければ、レベル指定なしで再チェック
                for (v in fallbackProfiles) {
                    pl = findProfile(v.profile, null)
                    if(pl!=null) {
                        return pl
                    }
                }
            }
        }
        if(levelCritical) {
            // levelCritical を false にして再試行
            if(level!=null) {
                pl = findProfile(profile, null)
                if(pl!=null) {
                    // レベルは無視してプロファイルだけが一致するものを見つけた
                    return pl
                }
            }
            if(fallbackProfiles!=null) {
                for (v in fallbackProfiles) {
                    pl = findProfile(v.profile, null)
                    if (pl != null) {
                        return pl
                    }
                }
            }
        }
        // どうやっても無理
        return null
    }

    protected fun capabilities(encoder: MediaCodec): CodecCapabilities? {
        return try { encoder.codecInfo.getCapabilitiesForType(codec.mime) } catch(_:Throwable) { null }
    }

    protected fun isBitrateModeSupported(encoder: MediaCodec, mode: BitRateMode):Boolean {
        return capabilities(encoder)?.encoderCapabilities?.isBitrateModeSupported(mode.value) ?: false
    }

//    protected fun supportedProfile(): MediaCodecInfo.CodecProfileLevel? {
////        dumpCodecs()
//        val supported = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
//            .flatMap { info->
//                try { info.getCapabilitiesForType(codec.mime).profileLevels.toList() } catch(_:Throwable) { emptyList()}
//            }
//            .sortedWith { v1, v2 ->
//                val r = v1.profile - v2.profile
//                if(r!=0) r else v1.level - v2.level
//            }
//        return supportedProfile(supported)
//    }

    /**
     * 出力動画サイズの計算
     *
     * 元動画のサイズ(width/height) が、この VideoStrategy によるトランスコードで、
     * どのようなサイズに変更されるかを確認するために使用する。
     */
    @Suppress("unused")
    fun calcSize(width:Int, height:Int) : Size {
        return calcVideoSize(width, height, sizeCriteria ?: throw IllegalStateException("sizeCriteria is null."))
    }

    data class SizeCriteria(val shortSize:Int, val longSide:Int)

    protected fun getCapabilitiesOf(info: MediaCodecInfo): MediaCodecInfo.CodecCapabilities? {
        return try {
            info.getCapabilitiesForType(codec.mime)
        } catch (e: Exception) {
            null
        }
    }


    override fun createEncoder(): MediaCodec {
        val supported = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter {
                it.isEncoder
            }
        var codec = supported.firstOrNull { getCapabilitiesOf(it)?.profileLevels?.find { pl -> pl.profile == profile.value && (level == null || pl.level >= level.value) }!=null}
        return if(codec!=null) {
            MediaCodec.createByCodecName(codec.name)
        } else {
            super.createEncoder()
        }
    }

    companion object {
        val logger = UtLog("Video", IStrategy.logger)

        const val HD720_S_SIZE = 720
        const val HD720_L_SIZE = 1280
        const val FHD1080_S_SIZE = 1080
        const val FHD1080_L_SIZE = 1920

        fun calcVideoSize(width:Int, height:Int, criteria:SizeCriteria) : Size {
            var r = if (width > height) { // 横長
                min(criteria.longSide.toFloat() / width, criteria.shortSize.toFloat() / height)
            } else { // 縦長
                min(criteria.shortSize.toFloat() / width, criteria.longSide.toFloat() / height)
            }
            if (r > 1) { // 拡大はしない
                r = 1f
            }
            val w = (width * r).roundToInt()
            val h = (height * r).roundToInt()

            // widthは4の倍数でないとgoogleのエンコーダーはエラー、QualcommはWidth指定を無視する。
            // heightは2の倍数でないとエラーになるエンコーダーがあるらしい。
            return Size(w-w%4, h-h%2)
        }

        fun dumpEncodingCodecs(codec:Codec) {
            logger.info("#### dump codecs ####")
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }.forEach { ci->
                val cap = try { ci.getCapabilitiesForType(codec.mime) } catch(_:Throwable) { return@forEach }
                val hw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if(ci.isHardwareAccelerated()) "HW" else "SW"
                } else {
                    ""
                }
                cap.profileLevels.forEach { pl->
                    logger.info("${ci.name} : ${Profile.fromValue(codec, pl.profile)?:"?"}@${Level.fromValue(codec, pl.level)?:"?"} ($hw)")
                }
            }
        }
    }
}

// ToDo: 可変ビットレート対応
// コーデックが可変ビットレートに対応しているかどうかの確認
// MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(index); // indexはコーデックのインデックス
//MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height); // widthとheightはエンコードする動画の幅と高さ
//MediaCodecInfo.VideoCapabilities videoCaps = codecInfo.getCapabilitiesForType("video/avc").getVideoCapabilities();
//MediaCodecInfo.EncoderCapabilities encoderCaps = codecInfo.getCapabilitiesForType("video/avc").getEncoderCapabilities();
//if (encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
//    // 可変ビットレートモードがサポートされている場合の処理
//} else {
//    // サポートされていない場合の処理
//}
// 可変ビットレートの要求
// MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
//MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
//mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 400000); // ビットレートの指定
//mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR); // 可変ビットレートモードの指定
//
// KEY_BIT_RATEの指定は、目標（平均）ビットレートとして使用される。
// 上限値はKEY_MAX_BIT_RATE で指定する。



