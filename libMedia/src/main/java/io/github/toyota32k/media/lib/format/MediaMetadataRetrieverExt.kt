package io.github.toyota32k.media.lib.format

import android.media.MediaMetadataRetriever

fun MediaMetadataRetriever.getString(key:Int):String? {
    return try {
        this.extractMetadata(key)
    } catch (e:Exception) {
        null
    }
}
fun MediaMetadataRetriever.getInt(key:Int):Int? {
    return try {
        this.extractMetadata(key)?.toInt()
    } catch (e:Exception) {
        null
    }
}
fun MediaMetadataRetriever.getLong(key:Int):Long? {
    return try {
        this.extractMetadata(key)?.toLong()
    } catch (e:Exception) {
        null
    }
}

fun MediaMetadataRetriever.getMime(): String? = getString(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
fun MediaMetadataRetriever.getWidth(): Int? = getInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
fun MediaMetadataRetriever.getHeight(): Int? = getInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
fun MediaMetadataRetriever.getDuration(): Long? = getLong(MediaMetadataRetriever.METADATA_KEY_DURATION)
fun MediaMetadataRetriever.getFrameRate(): Int? = getInt(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
fun MediaMetadataRetriever.getBitRate(): Int? = getInt(MediaMetadataRetriever.METADATA_KEY_BITRATE)
fun MediaMetadataRetriever.getRotation(): Int? = getInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
fun MediaMetadataRetriever.getLocation():String? = getString(MediaMetadataRetriever.METADATA_KEY_LOCATION)
