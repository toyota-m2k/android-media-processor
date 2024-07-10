package io.github.toyota32k.media.lib.misc

import android.media.MediaMetadataRetriever

/**
 * MediaMetadataRetriever が、AutoCloseable i/f を継承するようになったのは API29からで、それ以前は、release()を呼ぶ必要があった。
 * したがって、MinSdk=26 では AutoCloseable.use を使ってはいけない。。。（なぜかエラーも警告も出ないが、Github Actions でコンパイルするとエラーになる）
 * 代用品を実装。
 */
inline fun <T> MediaMetadataRetriever.safeUse(fn:(MediaMetadataRetriever)->T):T {
    return try {
        fn(this)
    } finally {
        release()
    }
}