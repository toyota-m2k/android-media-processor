package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.Profile
import java.lang.Integer.max
import java.lang.Integer.min

data class MaxDefault(val max:Int, val default:Int=max) {
    fun value(req1:Int?, req2:Int?=null):Int {
        val req = req1 ?: req2 ?: 0
        return if (req==0) { min(max, default) } else { min(max, req) }
    }
}
data class MinDefault(val min:Int, val default:Int=min) {
    fun value(req1:Int?, req2:Int?=null):Int {
        val req = req1 ?: req2 ?: 0
        return if(req==0) { max(min, default) } else { max(min, req) }
    }
}

abstract class AbstractStrategy(
    val codec: Codec,
    val profile: Profile,
    val level:Level? = null,
    val fallbackProfiles: Array<Profile>? = null,
) : IStrategy {
    override fun createEncoder(): MediaCodec {
        return MediaCodec.createEncoderByType(codec.mime).apply {
            IStrategy.logger.info("using [$name] as encoder")
        }
    }
}