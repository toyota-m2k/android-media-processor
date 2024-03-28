package io.github.toyota32k.media.lib.report

import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.getBitRate
import io.github.toyota32k.media.lib.format.getChannelCount
import io.github.toyota32k.media.lib.format.getSampleRate
import java.lang.StringBuilder

data class AudioSummary(
    val codec: Codec?,
    val profile: Profile?,
    val sampleRate: Int,
    val channelCount: Int,
    val bitRate: Int,
    ) : IAttributes {
    constructor(format:MediaFormat) : this(Codec.fromFormat(format), Profile.fromFormat(format), format.getSampleRate()?:-1, format.getChannelCount()?:-1, format.getBitRate()?:-1)

    override val title: String
        get() = "Audio Summary"
    override val subAttributes: List<IAttributes>
        get() = emptyList()

    private fun Int.format():String {
        return String.format("%,d", this)
    }

//    fun dump(logger: UtLog, message:String) {
//        logger.info(message)
//        logger.info("Codec = ${codec?:"n/a"}")
//        logger.info("Profile = ${profile?:"n/a"}")
//        logger.info("Sample Rate = ${sampleRate.format()} Hz")
//        logger.info("Channels = $channelCount")
//        logger.info("Bit Rate = ${bitRate.format()} bps")
//    }

//    override fun toString(): String {
//        return StringBuilder()
//            .appendLine("  Audio")
//            .appendLine("  - Codec = ${codec?:"n/a"}")
//            .appendLine("  - Profile = ${profile?:"n/a"}")
//            .appendLine("  - Sample Rate = ${sampleRate.format()} Hz")
//            .appendLine("  - Channels = $channelCount")
//            .appendLine("  - Bit Rate = ${bitRate.format()} bps")
//            .toString()
//
//    }

    override fun toString(): String {
        return format(StringBuilder(), "- ").toString()
    }

    override fun toList(): List<IAttributes.KeyValue> {
        return listOf<IAttributes.KeyValue>(
            IAttributes.KeyValue("Codec",   "${codec?:"n/a"}"),
            IAttributes.KeyValue("Profile", "${profile?:"n/a"}"),
            IAttributes.KeyValue("Sample Rate", "${sampleRate.format()} Hz"),
            IAttributes.KeyValue("Channels", "${channelCount?:"n/a"}"),
            IAttributes.KeyValue("Bit Rate", "${bitRate.format()} bps"),
            )
    }

}
