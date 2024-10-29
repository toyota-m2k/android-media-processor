package io.github.toyota32k.media.lib.format

enum class Media {
    Audio,
    Video,
    ;

    fun isVideo():Boolean {
        return this == Video
    }
    @Suppress("unused")
    fun isAudio(): Boolean {
        return this == Audio
    }
}