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
    fallbackProfiles: Array<ProfileLv>? = null,

    val sizeCriteria: SizeCriteria?,
    val bitRate: MaxDefault, // = Int.MAX_VALUE,
    val frameRate: MaxDefault, // = Int.MAX_VALUE,
    val iFrameInterval:MinDefault, // = DEFAULT_IFRAME_INTERVAL,
    val colorFormat:ColorFormat?, // = DEFAULT_COLOR_FORMAT,
    val bitRateMode: BitRateMode?,
) : AbstractStrategy(codec,profile,level, fallbackProfiles), IVideoStrategy {

//    override fun createOutputFormat(inputFormat: MediaFormat, encoder: MediaCodec): MediaFormat {
//        return createOutputFormat(MediaFormatCompat(inputFormat), encoder)
//    }

//    private fun hex(v:Int?):String {
//        return if(v!=null) String.format("0x%x",v) else "n/a"
//    }
    /**
     * 既存のVideoStrategy（Preset*とか）から、必要なパラメータを書き換えて、新しいVideoStrategyを作成する。
     */
    @Suppress("unused")
    fun derived(
        codec: Codec = this.codec,
        profile: Profile = this.profile,
        level: Level? = this.maxLevel,
        fallbackProfiles:Array<ProfileLv>? = this.fallbackProfiles,
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
            fallbackProfiles,
            sizeCriteria,
            bitRate,
            frameRate,
            iFrameInterval,
            colorFormat,
            bitRateMode)
    }

    @Suppress("unused")
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
            setInteger(MediaFormat.KEY_LEVEL, min(pl.level, maxLevel?.value?:Int.MAX_VALUE))
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
        IStrategy.logger.info("using encoder: [${encoder.name}] ---")
        supported.forEach { IStrategy.logger.info("  ${Profile.fromValue(codec, it.profile)?:"?"}@${Level.fromValue(codec, it.level)?:"?"}") }
        IStrategy.logger.info("-------------------------------------------")
        return selectMostSuitableProfile(supported)
    }

    @Suppress("unused")
    fun dumpCodecs() {
        dumpEncodingCodecs(codec)
    }

    /**
     * Profile/Levelリストの中から、VideoStrategyに対して、最も適切なProfileLevelを返す。
     * これがEncoderのMediaFormatに設定するパラメータとなる。
     */
    protected fun selectMostSuitableProfile(supported: List<MediaCodecInfo.CodecProfileLevel>) : MediaCodecInfo.CodecProfileLevel? {
        fun findProfileWithLevel(profile:Profile, level:Level?): MediaCodecInfo.CodecProfileLevel? {
            return supported.firstOrNull { it.profile == profile.value && (level==null || it.level>=level.value) }
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
            if (fallbackProfiles != null) {
                for (v in fallbackProfiles) {
                    pl = findProfile(v.profile, maxLevel)
                    if (pl != null) {
                        // profile/level が合致するものが見つかった
                        return pl
                    }
                }
            }
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
        fun checkProfileLevel(cap: CodecCapabilities?): Boolean {
            if(cap==null) return false
            return cap.profileLevels.find {
                pl -> pl.profile == profile.value &&
                (maxLevel == null || pl.level >= maxLevel.value) }!=null
        }
        val defaultCodec = super.createEncoder()
        val cap = getCapabilitiesOf(defaultCodec.codecInfo)
        if(checkProfileLevel(cap)) {
            // デフォルトのコーデックが条件に適合した
            logger.info("using default encoder: ${defaultCodec.name}")
            return defaultCodec
        }

        // 他のコーデックを探す
        fun supportedCodec(optionalFilter:(MediaCodecInfo)->Boolean): List<MediaCodecInfo> {
            return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter {
                    it.isEncoder
                    && optionalFilter(it)
                    && checkProfileLevel(getCapabilitiesOf(it))
                }
        }

        var codec = supportedCodec { isHardwareAccelerated(it) }.firstOrNull()  // hardware accelerated なコーデックを最優先
                    ?: supportedCodec { true }.firstOrNull()     //  条件を緩和
        return (if(codec!=null) {
            MediaCodec.createByCodecName(codec.name)
        } else {
            super.createEncoder()
        }).apply {
            logger.info("using encoder: $name")
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

        fun capabilities(info: MediaCodecInfo, mime:String): CodecCapabilities? {
            return try {
                info.getCapabilitiesForType(mime)
            } catch(_:Throwable) {
                null
            }
        }
        fun isHardwareAccelerated(info: MediaCodecInfo): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                true   // どうせわからんからなんでも true
            }
        }
        fun isSoftwareOnly(info: MediaCodecInfo): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isSoftwareOnly
            } else {
                false   // どうせわからんからなんでも false
            }
        }

        fun dumpEncodingCodecs(codec:Codec) {
            logger.info("#### dump codecs ####")
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }.forEach { ci->
                val cap = capabilities(ci,codec.mime) ?: return@forEach
                val hw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if(ci.isHardwareAccelerated) "HW" else "SW"
                } else {
                    "?"
                }
                cap.profileLevels.forEach { pl->
                    logger.info("${ci.name} : ${Profile.fromValue(codec, pl.profile)?:"?"}@${Level.fromValue(codec, pl.level)?:"?"} ($hw)")
                }
            }
        }

        private class AvailableCodecList(override val encoder:Boolean) : IAvailableCodecList {
            override var default: String = ""
            override var hardwareAccelerated = mutableListOf<String>()
            override var softwareOnly = mutableListOf<String>()
            override var other = mutableListOf<String>()

            override fun toString(): String {
                return StringBuilder().apply {
                    appendLine("Available ${if(encoder) "Encoders" else "Decoders"}:")
                    appendLine("default: $default")
                    appendLine("hardwareAccelerated:")
                    hardwareAccelerated.forEach { appendLine("- $it") }
                    appendLine("softwareOnly:")
                    softwareOnly.forEach { appendLine("- $it") }
                    if(other.isNotEmpty()) {
                        appendLine("other:")
                        other.forEach { appendLine("  $it") }
                    }
                }.toString()
            }
        }

        /**
         * デバイスにインストールされているエンコーダーを列挙する
         * （実験＆ログ出力用）
         */
        fun availableEncoders(codec:Codec) : IAvailableCodecList {
            return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder && capabilities(it,codec.mime)!=null }
                .fold(AvailableCodecList(true)) { codecs, ci ->
                    codecs.apply {
                        if (isHardwareAccelerated(ci)) {
                            hardwareAccelerated.add(ci.name)
                        } else if (isSoftwareOnly(ci)) {
                            softwareOnly.add(ci.name)
                        } else {
                            other.add(ci.name)
                        }
                    }
                }.apply {
                    val codec = MediaCodec.createEncoderByType(codec.mime)
                    default = codec.name
                    codec.release()
                }
        }

        /**
         * デバイスにインストールされているデコーダーを列挙する
         * （実験＆ログ出力用）
         */
        fun availableDecoders(codec:Codec) : IAvailableCodecList {
            return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && capabilities(it,codec.mime)!=null }
                .fold(AvailableCodecList(false)) { codecs, ci ->
                    codecs.apply {
                        if (isHardwareAccelerated(ci)) {
                            hardwareAccelerated.add(ci.name)
                        } else if (isSoftwareOnly(ci)) {
                            softwareOnly.add(ci.name)
                        } else {
                            other.add(ci.name)
                        }
                    }
                }.apply {
                    val codec = MediaCodec.createDecoderByType(codec.mime)
                    default = codec.name
                    codec.release()
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



