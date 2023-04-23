package io.github.toyota32k.media.lib.format

enum class Media {
    Audio,
    Video,
    ;

    fun isVideo():Boolean {
        return this == Video
    }
    fun isAudio(): Boolean {
        return this == Audio
    }
}