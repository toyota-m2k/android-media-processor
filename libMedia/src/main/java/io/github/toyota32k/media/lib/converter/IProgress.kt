package io.github.toyota32k.media.lib.converter

interface IProgress {
    val percent:Int     // percent (-1: on total not available)
    val total:Long      // in us
    val current:Long    // in us
}