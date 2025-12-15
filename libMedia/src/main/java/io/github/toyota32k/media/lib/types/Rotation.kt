package io.github.toyota32k.media.lib.types

data class Rotation(val degree:Int, val relative:Boolean) {
    companion object {
        val nop:Rotation by lazy { Rotation(0, true) }
        val right:Rotation by lazy { Rotation(90, true) }
        val left:Rotation by lazy { Rotation(270, true) }
        val upsideDown:Rotation by lazy { Rotation(180, true) }
        fun absolute(degree:Int) = Rotation(degree, false)
        fun relative(degree:Int): Rotation {
            return when (degree%360) {
                0 -> nop
                -270,90 -> right
                -180,180 -> upsideDown
                -90,270 -> left
                else -> Rotation(degree, true)
            }
        }

        fun normalize(degree:Int):Int {
            val r = degree%360
            return if(r<0) {
                r+360
            } else r
        }
    }

    fun rotate(orgDegree:Int):Int {
        if(!relative) {
            return normalize(degree)
        }
        return normalize(degree + orgDegree)
    }
}