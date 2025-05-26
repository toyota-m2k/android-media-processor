package io.github.toyota32k.media.lib.strategy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.os.Build
import io.github.toyota32k.media.lib.format.Codec
import io.github.toyota32k.media.lib.format.Level
import io.github.toyota32k.media.lib.format.Profile

object DeviceCapabilities {
    data class ProfileLevel(val profile: Profile, val level: Level?) {
        override fun toString(): String {
            return if(level!=null) "$profile/${level}" else "$profile"      // audio codec は level を持っていないことが多い
        }

        companion object {
            fun create(profile: Profile?, level: Level?): ProfileLevel? {
                if (profile == null) {
                    return null
                }
                return ProfileLevel(profile, level)
            }
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

    fun capabilities(info: MediaCodecInfo, mime: String): CodecCapabilities? {
        return try {
            info.getCapabilitiesForType(mime)
        } catch (_: Throwable) {
            null
        }
    }

    class CodecInfo(private val codec: Codec, private val ci: MediaCodecInfo) {
        val name: String get() = ci.name
        val hardwareAccelerated = isHardwareAccelerated(ci)
        val softwareOnly = isSoftwareOnly(ci)
        val capabilities: List<ProfileLevel>
            get() {
                return capabilities(ci, codec.mime)?.profileLevels?.map {
                    ProfileLevel.create(Profile.fromValue(codec, it.profile), Level.fromValue(codec, it.level))
                }?.filterNotNull() ?: emptyList()
            }
        private val hwsw:String get() =
            if(hardwareAccelerated && softwareOnly) {
                "hybrid"    // たぶんこんなのはない
            } else if (hardwareAccelerated) {
                "HW"
            } else if (softwareOnly) {
                "SW"
            } else {
                "unknown"   // OSが古い(=P以下の)場合は区別がつかない
            }

        override fun toString(): String {
            return StringBuilder().apply {
                append("Codec: $name ($hwsw)")
                appendLine("Capabilities:")
                capabilities.forEach {
                    appendLine("  $it")
                }
            }.toString()
        }
    }

    class CodecInfoList(val encoder:Boolean, val codec:Codec, val all:List<CodecInfo>, val default:String) {
        val hardwareAccelerated get() = all.filter { it.hardwareAccelerated }
        val softwareOnly get() = all.filter { it.softwareOnly }
        val other get() = all.filter { !it.hardwareAccelerated && !it.softwareOnly }

        override fun toString(): String {
            return StringBuilder().apply {
                appendLine("Available ${if(encoder) "Encoders" else "Decoders"} for ${codec.mime}")
                appendLine("default: $default")
                all.forEach { appendLine("$it") }
            }.toString()
        }

        val isEmpty:Boolean get() = all.isEmpty()
    }

    /**
     * デバイスにインストールされているコーデック（デコーダー/エンコーダー）を列挙する
     */
    fun availableCodecs(codec:Codec, encoder:Boolean) : CodecInfoList {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder==encoder && capabilities(it,codec.mime)!=null }
            .fold(mutableListOf<CodecInfo>()) { codecs, ci ->
                codecs.apply { add(CodecInfo(codec,ci)) }
            }
        val default = try {
            MediaCodec.createEncoderByType(codec.mime).run {
                val name = this.name
                release()
                name
            }
        } catch (_: Throwable) {
            "n/a"
        }
        return CodecInfoList(encoder, codec, list, default)
    }
}