package io.github.toyota32k.media.lib.utils

/**
 * 時間計測用ログ出力クラス
 */
class Chronos(callerLogger:UtLog) {
    var logger = UtLog("TIME", callerLogger, callerLogger.omissionNamespace).apply { stackOffset=5 }
    var prev:Long
    var start:Long
    init {
        prev = System.currentTimeMillis()
        start = prev
    }

    private val lapTime:Long
        get() {
            val c = System.currentTimeMillis()
            val d = c - prev
            prev = c
            return d
        }
    private val totalTime:Long get()=System.currentTimeMillis()-start

    fun total(msg:String="") {
        logger.debug("total = ${formatMS(totalTime)} $msg")
    }

    fun lap(msg:String="") {
        logger.debug("lap = ${formatMS(lapTime)} $msg")
    }

    fun formatMS(t:Long):String {
        return "${t/1000f} sec"
    }

    fun <T> measure(msg:String?=null, fn:()->T):T {
        val begin = System.currentTimeMillis()
        var result = false
        logger.debug("enter ${msg?:""}")
        return try {
            fn()
        } finally {
            logger.debug("exit ${formatMS(System.currentTimeMillis()-begin)} ${msg?:""}")
        }
    }
}