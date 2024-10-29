package io.github.toyota32k.media.lib.format

import android.media.MediaMetadataRetriever

fun MediaMetadataRetriever.getString(key:Int):String? {
    return try {
        this.extractMetadata(key)
    } catch (_:Exception) {
        null
    }
}
fun MediaMetadataRetriever.getInt(key:Int):Int? {
    return try {
        this.extractMetadata(key)?.toInt()
    } catch (_:Exception) {
        null
    }
}
fun MediaMetadataRetriever.getLong(key:Int):Long? {
    return try {
        this.extractMetadata(key)?.toLong()
    } catch (_:Exception) {
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

// 今後は、IInputMediaFile.openMetadataRetriever():CloseableMediaMetadataRetrieverを使う
/**
 * MediaMetadataRetriever が、AutoCloseable i/f を継承するようになったのは API29からで、それ以前は、release()を呼ぶ必要があった。
 * したがって、MinSdk=26 では AutoCloseable.use を使ってはいけない。。。（なぜかエラーも警告も出ないが、Github Actions でコンパイルするとエラーになる）
 * 代用品を実装。
 */
//inline fun <T> MediaMetadataRetriever.safeUse(fn:(MediaMetadataRetriever)->T):T {
//    return try {
//        fn(this)
//    } finally {
//        release()
//    }
//}