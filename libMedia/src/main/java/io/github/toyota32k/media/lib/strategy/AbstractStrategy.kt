package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
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

data class ProfileLv(val profile: Profile, val maxLevel: Level?=null)


abstract class AbstractStrategy(
    override val codec: Codec,
    override val profile: Profile,
    val maxLevel:Level? = null,
    val fallbackProfiles: Array<ProfileLv>? = null,
) : IStrategy {
    override val name: String get() = this.javaClass.simpleName
    override fun createEncoder(): MediaCodec {
        return MediaCodec.createEncoderByType(codec.mime).apply {
            IStrategy.logger.info("using default encoder: $name")
        }
    }

}