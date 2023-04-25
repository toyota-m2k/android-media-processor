package io.github.toyota32k.media.lib.report

import android.media.MediaFormat
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.ColorFormat
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.getBitRate
import io.github.toyota32k.media.lib.format.getChannelCount
import io.github.toyota32k.media.lib.format.getSampleRate
import io.github.toyota32k.media.lib.utils.UtLog
import java.util.logging.Logger

data class AudioSummary(
    val codec: Codec?,
    val profile: Profile?,
    val sampleRate: Int,
    val channelCount: Int,
    val bitRate: Int,
    ) {
    constructor(format:MediaFormat) : this(Codec.codecOf(format), Profile.fromValue(format), format.getSampleRate()?:-1, format.getChannelCount()?:-1, format.getBitRate()?:-1)

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

    override fun toString(): String {
        return StringBuilder()
            .appendLine("  Audio")
            .appendLine("  - Codec = ${codec?:"n/a"}")
            .appendLine("  - Profile = ${profile?:"n/a"}")
            .appendLine("  - Sample Rate = ${sampleRate.format()} Hz")
            .appendLine("  - Channels = $channelCount")
            .appendLine("  - Bit Rate = ${bitRate.format()} bps")
            .toString()

    }
}
