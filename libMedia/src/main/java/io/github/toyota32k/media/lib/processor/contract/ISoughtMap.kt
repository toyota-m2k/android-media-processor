package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.types.RangeUs.Companion.formatAsUs

data class SoughtPosition(val requestUs:Long, val actualUs:Long, val presentationTimeUs:Long) {
    override fun toString(): String {
        return "req=${requestUs.formatAsUs()}, act=${actualUs.formatAsUs()}, diff=${(requestUs-actualUs).formatAsUs()} at ${presentationTimeUs.formatAsUs()})"
    }
}

interface ISoughtMap {
    val soughtPositionList: List<SoughtPosition>
    fun correctPositionUs(timeUs:Long):Long
}

