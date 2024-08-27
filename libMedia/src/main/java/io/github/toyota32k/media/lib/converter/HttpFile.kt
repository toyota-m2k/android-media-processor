package io.github.toyota32k.media.lib.converter

import android.media.MediaExtractor
import android.media.MediaMetadataRetriever

class HttpFile(val url: String, val headers: Map<String, String>?=null) : IInputMediaFile {
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