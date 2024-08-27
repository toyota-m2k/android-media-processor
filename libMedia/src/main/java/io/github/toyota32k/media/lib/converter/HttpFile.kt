package io.github.toyota32k.media.lib.converter

import android.media.MediaExtractor
import android.media.MediaMetadataRetriever

/**
 * HTTP上のファイルを Converter の入力として利用するための IInputMediaFile の実装クラス。
 * ただし、trimming を指定していると、seek がうまく動作しないようなので、利用はお勧めしない。
 * 当面は、一時ファイルにキャッシュしてシークをサポートする httpInputFile を使うことにする。
 */
@Deprecated("use HttpInputFil")
class HttpFile(private val url: String, private val headers: Map<String, String>?=null) : IInputMediaFile {
    override fun openExtractor(): CloseableExtractor {
        val extractor = MediaExtractor().apply {
            if(headers.isNullOrEmpty()) {
                setDataSource(url)
            } else {
                setDataSource(url, headers)
            }
        }
        return CloseableExtractor(extractor,null)
    }

    override fun openMetadataRetriever(): CloseableMediaMetadataRetriever {
        val metadataRetriever = MediaMetadataRetriever().apply {
            if(headers.isNullOrEmpty()) {
                setDataSource(url)
            } else {
                setDataSource(url, headers)
            }
        }
        return CloseableMediaMetadataRetriever(metadataRetriever, null)
    }

    override fun getLength(): Long {
        return -1L
    }

    override val seekable: Boolean = false
}