package io.github.toyota32k.media.lib.converter

import kotlinx.coroutines.CancellationException

data class Result(val succeeded:Boolean, val cancelled:Boolean, val errorMessage:String?, val exception:Throwable?) {
    constructor() : this(true, false, null, null)
    companion object {
        val succeeded:Result
            get() = Result()
        val cancelled:Result
            get() = Result(false, true, null, null)
        fun error(exception:Throwable):Result {
            return if(exception is CancellationException) cancelled else Result(false, false, exception.message,exception)
        }
    }
}