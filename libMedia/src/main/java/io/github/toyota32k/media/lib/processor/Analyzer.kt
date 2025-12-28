package io.github.toyota32k.media.lib.processor

import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.report.Summary

object Analyzer {
    fun analyze(file: IInputMediaFile) : Summary {
        return try {
            Summary.getSummary(file)
        } catch(_:Throwable) {
            Summary()
        }
    }
}