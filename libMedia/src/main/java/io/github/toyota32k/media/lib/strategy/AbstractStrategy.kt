package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import java.lang.Integer.max
import java.lang.Integer.min

data class MaxDefault(val max:Int, val default:Int=max) {
    fun value(req:Int?):Int {
        return if(req==null || req==0) { min(max, default) } else { min(max, req) }
    }
}
data class MinDefault(val min:Int, val default:Int=min) {
    fun value(req:Int?):Int {
        return if(req==null || req==0) { max(min, default) } else { max(min, req) }
    }
}

abstract class AbstractStrategy(
    val mimeType: String,
    val profile:Int,
    val level:Int = 0,
    val fallbackProfiles: Array<Int>? = null,
) : IStrategy {
    final override fun createEncoder(): MediaCodec {
        return MediaCodec.createEncoderByType(mimeType)
    }
}