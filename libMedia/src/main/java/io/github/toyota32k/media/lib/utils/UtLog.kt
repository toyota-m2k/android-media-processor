package io.github.toyota32k.media.lib.utils

import android.util.Log
import java.io.Closeable
import java.lang.Exception

class UtLog @JvmOverloads constructor(val tag:String, val parent:UtLog?=null, val omissionNamespace:String?=parent?.omissionNamespace, private val outputClassName:Boolean=true, private val outputMethodName:Boolean=true) {
    companion object {
        fun hierarchicTag(tag:String, parent:UtLog?):String {
            return if(parent!=null) {
                "${hierarchicTag(parent.tag, parent.parent)}.${tag}"
            } else {
                tag
            }
        }
//        fun className():String {
//            return Thread.currentThread().stackTrace[2].className
//        }
//        fun className(omissionNamespace: String?):String {
//            val cn = className()
//            if(!omissionNamespace.isNullOrBlank() && cn.startsWith(omissionNamespace)) {
//                return cn.substring(omissionNamespace.length)
//            } else {
//                return cn
//            }
//        }
//        fun methodName():String {
//            return Thread.currentThread().stackTrace[2].methodName
//        }
//        fun classAndMethodName():Pair<String,String> {
//            val e = Thread.currentThread().stackTrace[2]
//            return Pair(e.className, e.methodName)
//        }
//
//        fun assert(chk:Boolean, msg:String) {
//            if(!chk) {
//                UtLogger.stackTrace(Exception("assertion failed."), msg)
//            }
//        }

//        val libLogger:UtLog by lazy { UtLog("libUtils") }

        var logLevel:Int = Log.DEBUG    // Log.VERBOSE
    }

    private val logger = UtLoggerInstance(hierarchicTag(tag,parent))

    fun stripNamespace(classname:String):String {
        if(!omissionNamespace.isNullOrBlank() && classname.startsWith(omissionNamespace)) {
            return classname.substring(omissionNamespace.length)
        } else {
            return classname
        }
    }

    var stackOffset:Int = 4

    private fun compose(message:String?):String {
        return if(outputClassName||outputMethodName) {
            val stack = Thread.currentThread().stackTrace
            var n:Int = stackOffset
            var e = stack[n]
            while(e.methodName.endsWith("\$default") && n<stack.size) {
                n++
                e = stack[n]
            }
            if(!outputClassName) {
                if(message!=null) "${e.methodName}: ${message}" else e.methodName
            } else if(!outputMethodName) {
                if(message!=null) "${stripNamespace(e.className)}:${message}" else stripNamespace(e.className)
            } else {
                if(message!=null) "${stripNamespace(e.className)}.${e.methodName}:${message}" else "${stripNamespace(e.className)}.${e.methodName}"
            }
        } else {
            message ?: ""
        }
    }
    private fun verbose(msg: String?=null) {
        logger.verbose(compose(msg))
    }

    fun verbose(fn:()->String) {
        if(logLevel<=Log.VERBOSE) {
            logger.verbose(compose(fn()))
        }
    }


    @JvmOverloads
    fun debug(msg: String?=null) {
        logger.debug(compose(msg))
    }
    fun debug(fn:()->String) {
        if(logLevel<=Log.DEBUG) {
            logger.debug(compose(fn()))
        }
    }

    @JvmOverloads
    fun warn(msg: String?=null) {
        logger.warn(compose(msg))
    }

    @JvmOverloads
    fun error(msg: String?=null) {
        logger.error(compose(msg))
    }

    @JvmOverloads
    fun info(msg: String?=null) {
        logger.info(compose(msg))
    }

    @JvmOverloads
    fun stackTrace(e:Throwable, msg:String?=null) {
        logger.stackTrace(e, compose(msg))
    }

    @JvmOverloads
    fun assert(chk:Boolean, msg:String?=null) {
        if(!chk) {
            logger.stackTrace(Exception("assertion failed."), compose(msg))
        }
    }

    @JvmOverloads
    fun scopeWatch(msg:String?=null) : Closeable {
        val composed = compose(msg)
        logger.debug("$composed - enter")
        return ScopeWatcher { logger.debug("$composed - leave") }
    }

    private class ScopeWatcher(val leaving:()->Unit) : Closeable {
        override fun close() {
            leaving()
        }
    }
}