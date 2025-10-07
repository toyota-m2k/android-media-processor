package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Size
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.format.BitRateMode
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.HDR
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.bitRate
import io.github.toyota32k.media.lib.format.bitRateMode
import io.github.toyota32k.media.lib.format.frameRate
import io.github.toyota32k.media.lib.format.height
import io.github.toyota32k.media.lib.format.iFrameInterval
import io.github.toyota32k.media.lib.format.isHDR
import io.github.toyota32k.media.lib.format.mime
import io.github.toyota32k.media.lib.format.safeGetInteger
import io.github.toyota32k.media.lib.format.safeGetIntegerOrNull
import io.github.toyota32k.media.lib.format.width
import io.github.toyota32k.media.lib.strategy.DeviceCapabilities.capabilities
import io.github.toyota32k.media.lib.strategy.DeviceCapabilities.isHardwareAccelerated
import io.github.toyota32k.media.lib.strategy.DeviceCapabilities.isSoftwareOnly
import kotlin.math.min
import kotlin.math.roundToInt

open class VideoStrategy(
    codec: Codec,
    profile: Profile,
    level: Level? = null,
    fallbackProfiles: Array<ProfileLv>? = null,

    override val sizeCriteria: SizeCriteria?,
    override val bitRate: MaxDefault, // = Int.MAX_VALUE,
    override val frameRate: MaxDefault, // = Int.MAX_VALUE,
    override val iFrameInterval:MinDefault, // = DEFAULT_IFRAME_INTERVAL,
    override val colorFormat:ColorFormat?, // = DEFAULT_COLOR_FORMAT,
    override val bitRateMode: BitRateMode?,
    override val encoderType: EncoderType = EncoderType.HARDWARE,
) : AbstractStrategy(codec,profile,level, fallbackProfiles), IVideoStrategy {
    enum class EncoderType {
        DEFAULT,
        HARDWARE,
        SOFTWARE,
    }

//    override fun createOutputFormat(inputFormat: MediaFormat, encoder: MediaCodec): MediaFormat {
//        return createOutputFormat(MediaFormatCompat(inputFormat), encoder)
//    }

//    private fun hex(v:Int?):String {
//        return if(v!=null) String.format("0x%x",v) else "n/a"
//    }
    /**
     * 既存のVideoStrategy（Preset*とか）から、必要なパラメータを書き換えて、新しいVideoStrategyを作成する。
     */
    override fun derived(
        codec: Codec,
        profile: Profile,
        level: Level?,
        fallbackProfiles:Array<ProfileLv>?,
        sizeCriteria: SizeCriteria?,
        bitRate: MaxDefault,
        frameRate: MaxDefault,
        iFrameInterval:MinDefault,
        colorFormat:ColorFormat?,
        bitRateMode: BitRateMode?,
        encoderType: EncoderType,
    ): IVideoStrategy {
        return VideoStrategy(
            codec,
            profile,
            level,
            fallbackProfiles,
            sizeCriteria,
            bitRate,
            frameRate,
            iFrameInterval,
            colorFormat,
            bitRateMode)
    }

    private fun preferEncoderType(encoderType:EncoderType):IVideoStrategy {
        return if(this.encoderType==encoderType) {
            this
        } else {
            derived(encoderType=encoderType)
        }
    }
    fun preferSoftwareEncoder(): IVideoStrategy {
        return preferEncoderType(EncoderType.SOFTWARE)
    }
    fun preferHardwareEncoder(): IVideoStrategy {
        return preferEncoderType(EncoderType.HARDWARE)
    }
    fun preferDefaultEncoder(): IVideoStrategy {
        return preferEncoderType(EncoderType.DEFAULT)
    }

    override fun createOutputFormat(inputFormat: MediaFormat, metaData: MetaData, encoder: MediaCodec): MediaFormat {
        // bitRate は、MediaFormat に含まれず、MetaDataにのみ含まれるケースがあるようなので、
        // 両方をチェックするようにしてみた。
        val bitRate = this.bitRate.value(inputFormat.bitRate, metaData.bitRate)
        val frameRate = this.frameRate.value(inputFormat.frameRate, metaData.frameRate)
        val iFrameInterval = this.iFrameInterval.value(inputFormat.iFrameInterval)
        var width = inputFormat.width ?: metaData.width ?: throw IllegalArgumentException("inputFormat have no size params.")
        var height = inputFormat.height ?: metaData.height ?: throw IllegalArgumentException("inputFormat have no size params.")
        val sizeCriteria = this.sizeCriteria
        val bitRateMode = this.bitRateMode
        if(sizeCriteria!=null) {
            val size = calcVideoSize(width, height, sizeCriteria)
            width = size.width
            height = size.height
        }
        val pl = supportedProfileByEncoder(encoder) ?: throw IllegalStateException("no supported profile.")
        val brm = if(bitRateMode!=null && isBitrateModeSupported(encoder, bitRateMode)) bitRateMode else null

        // Support HDR
        val inputProfile = Profile.fromFormat(inputFormat)
        val outputProfile = Profile.fromValue(codec, pl.profile)
        val hdrInfo = if (inputProfile?.isHDR()==true && outputProfile?.isHDR()==true) {
            // input/output ともにHDR対応プロファイル なら、HDR情報を引き継ぐ。
            HDR.Info.fromFormat(inputFormat)
        } else null
//        val colorStandard = inputFormat.safeGetIntegerOrNull(MediaFormat.KEY_COLOR_STANDARD)
//        val colorRange = inputFormat.safeGetIntegerOrNull(MediaFormat.KEY_COLOR_RANGE)
//        val colorTransfer = inputFormat.safeGetIntegerOrNull(MediaFormat.KEY_COLOR_TRANSFER)
//        val hdrStaticInfo = inputFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)
//        val hdrPludInfo = inputFormat.getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO)

        logger.info("Video Format ------------------------------------------------------")
        logger.info("- Type            ${inputFormat.mime?:"n/a"} --> ${codec.mime}")
        logger.info("- Profile         ${inputProfile?:"n/a"} --> ${Profile.fromValue(codec, pl.profile)?:"n/a"}")
        logger.info("- Level           ${Level.fromFormat(inputFormat)} --> ${Level.fromValue(codec,pl.level)?:"n/a"}")
        logger.info("- Width           ${inputFormat.width?:"n/a"}")
        logger.info("- Width(Meta)     ${metaData.width?:"n/a"} --> $width")
        logger.info("- Height          ${inputFormat.height?:"n/a"}")
        logger.info("- Height(Meta)    ${metaData.height?:"n/a"} --> $height")
        logger.info("- BitRate         ${inputFormat.bitRate?:"n/a"}")
        logger.info("- BitRate(Meta)   ${metaData.bitRate?:"n/a"} --> $bitRate")
        logger.info("- BitRateMode     ${inputFormat.bitRateMode?:"n/a"} --> ${brm?:"n/a"}")
        logger.info("- FrameRate       ${inputFormat.frameRate?:"n/a"}")
        logger.info("- FrameRate(Meta) ${metaData.frameRate?:"n/a"} --> $frameRate")
        logger.info("- iFrameInterval  ${inputFormat.iFrameInterval?:"n/a"} --> $iFrameInterval")
        logger.info("- colorFormat     ${ColorFormat.fromFormat(inputFormat)?:"n/a"} --> ${colorFormat?:"n/a"}")
        logger.info("- Duration(Meta)  ${metaData.duration?:"n/a"}")
        if (hdrInfo?.isHDR==true) {
            logger.info("- ColorStandard   ${hdrInfo.colorStandard?:"n/a"}")
            logger.info("- ColorRange      ${hdrInfo.colorRange?:"n/a"}")
            logger.info("- ColorTransfer   ${hdrInfo.colorTransfer?:"n/a"}")
            logger.info("- HDRStaticInfo   ${if(hdrInfo.hdrStaticInfo!=null) "present (${hdrInfo.hdrStaticInfo.limit()} bytes)" else "n/a"}")
            logger.info("- HDR10PlusInfo   ${if(hdrInfo.hdr10PlusInfo!=null) "present (${hdrInfo.hdr10PlusInfo.limit()} bytes)" else "n/a"}")
        }
        logger.info("-------------------------------------------------------------------")

        return MediaFormat.createVideoFormat(codec.mime, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, (colorFormat?:ColorFormat.COLOR_FormatSurface).value)
            setInteger(MediaFormat.KEY_PROFILE, pl.profile)
            setInteger(MediaFormat.KEY_LEVEL, min(pl.level, maxLevel?.value?:Int.MAX_VALUE))
            if(brm!=null) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, brm.value)
            }
            if (hdrInfo?.isHDR==true) {
                // output も HDR対応プロファイルなので、HDR情報を引き継ぐ。
                hdrInfo.colorStandard?.let { setInteger(MediaFormat.KEY_COLOR_STANDARD, it.value) }
                hdrInfo.colorRange?.let { setInteger(MediaFormat.KEY_COLOR_RANGE, it.value) }
                hdrInfo.colorTransfer?.let { setInteger(MediaFormat.KEY_COLOR_TRANSFER, it.value) }
                hdrInfo.hdrStaticInfo?.let { setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, it) }
                hdrInfo.hdr10PlusInfo?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { setByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO, it) }}
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
        IStrategy.logger.info("using encoder: [${encoder.name}] ---")
        supported.forEach { IStrategy.logger.info("  ${Profile.fromValue(codec, it.profile)?:"?"}@${Level.fromValue(codec, it.level)?:"?"}") }
        IStrategy.logger.info("-------------------------------------------")
        return selectMostSuitableProfile(supported)
    }

    @Suppress("unused")
    fun dumpCodecs() {
        logger.info("#### Dump Video Codecs ####")
        logger.info(DeviceCapabilities.availableCodecs(codec, encoder = true).toString())
        logger.info(DeviceCapabilities.availableCodecs(codec, encoder = false).toString())
    }

    /**
     * Profile/Levelリストの中から、VideoStrategyに対して、最も適切なProfileLevelを返す。
     * これがEncoderのMediaFormatに設定するパラメータとなる。
     */
    protected fun selectMostSuitableProfile(supported: List<MediaCodecInfo.CodecProfileLevel>) : MediaCodecInfo.CodecProfileLevel? {
        fun findProfileWithLevel(profile:Profile, level:Level?): MediaCodecInfo.CodecProfileLevel? {
            return supported.firstOrNull { it.profile == profile.value && (level==null || it.level<=level.value) }
        }
        fun findProfileWithoutLevel(profile:Profile, level:Level?): MediaCodecInfo.CodecProfileLevel? {
            return if(level==null) null else findProfileWithLevel(profile, null)
        }

        // Strategyでレベルが明示されていれば、そのレベルをサポートしているコーデックを優先的に探す。
        // 見つからなければ、レベルを無視してコーデックを探す。
        // レベルが明示的に設定されていない場合、または、そのレベルをサポートしているコーデックがない場合は、
        // 見つかったコーデックがサポートするレベルの最高値を使用する。
        var findProfile = ::findProfileWithLevel
        (0..1).forEach { i ->
            var pl = findProfile(profile, maxLevel)
            if (pl != null) {
                // profile / level ともに適合するものが見つかった
                return pl
            }

            // 以上で、対応するプロファイルが見つからなければ、
            // フォールバックプロファイルに一致するものを探す
            val fallbacks = fallbackProfiles
            if (fallbacks != null) {
                for (v in fallbacks) {
                    pl = findProfile(v.profile, maxLevel)
                    if (pl != null) {
                        // profile/level が合致するものが見つかった
                        return pl
                    }
                }
            }
            // profile/level ともに適合するものが見つからなかった。
            // 次は、levelを無視して、profileだけで探す。
            findProfile = ::findProfileWithoutLevel
        }
        return null
    }

    protected fun capabilities(encoder: MediaCodec): CodecCapabilities? {
        return try { encoder.codecInfo.getCapabilitiesForType(codec.mime) } catch(_:Throwable) { null }
    }

    protected fun isBitrateModeSupported(encoder: MediaCodec, mode: BitRateMode):Boolean {
        return capabilities(encoder)?.encoderCapabilities?.isBitrateModeSupported(mode.value) == true
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

    protected fun getCapabilitiesOf(info: MediaCodecInfo): CodecCapabilities? {
        return capabilities(info, codec.mime)
    }



    override fun createEncoder(): MediaCodec {
        if(encoderType==EncoderType.DEFAULT) {
            return super.createEncoder()
        }

        fun checkProfileLevel(cap: CodecCapabilities?): Boolean {
            if(cap==null) return false
            val maxLevel = this.maxLevel
            return cap.profileLevels.any {
                pl -> pl.profile == profile.value &&
                (maxLevel == null || pl.level <= maxLevel.value) }
        }
//        val defaultCodec = super.createEncoder()
//        val cap = getCapabilitiesOf(defaultCodec.codecInfo)
//        if(checkProfileLevel(cap)) {
//            // デフォルトのコーデックが条件に適合した
//            logger.info("using default encoder: ${defaultCodec.name}")
//            return defaultCodec
//        }

        // 条件に適合するコーデックを探す
        fun supportedCodec(optionalFilter:(MediaCodecInfo)->Boolean): List<MediaCodecInfo> {
            return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter {
                    it.isEncoder
                    && optionalFilter(it)
                    && checkProfileLevel(getCapabilitiesOf(it))
                }
        }


        val codec = (if(encoderType==EncoderType.HARDWARE) {
            // hardware accelerated なコーデックを優先して探す
            supportedCodec { isHardwareAccelerated(it) }
        } else {
            // hardware accelerated ではないコーデックを探す
            supportedCodec { !isHardwareAccelerated(it)||!isSoftwareOnly(it) }
        }).firstOrNull()

        return if(codec!=null) {
            logger.info("using [$encoderType] encoder: ${codec.name}")
            MediaCodec.createByCodecName(codec.name)
        } else {
            // hardware accelerated なコーデックがなければ、デフォルトのコーデックを使用。
            logger.info("no hardware accelerated codec found.")
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

//        fun dumpEncodingCodecs(codec:Codec) {
//            logger.info("#### dump codecs ####")
//            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }.forEach { ci->
//                val cap = capabilities(ci,codec.mime) ?: return@forEach
//                val hw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    if(ci.isHardwareAccelerated) "HW" else "SW"
//                } else {
//                    "?"
//                }
//                cap.profileLevels.forEach { pl->
//                    logger.info("${ci.name} : ${Profile.fromValue(codec, pl.profile)?:"?"}@${Level.fromValue(codec, pl.level)?:"?"} ($hw)")
//                }
//            }
//        }
    }
}

interface IHDRSupport {
    fun hdr(profile:Profile?=null, level:Level?=null): IVideoStrategy
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



