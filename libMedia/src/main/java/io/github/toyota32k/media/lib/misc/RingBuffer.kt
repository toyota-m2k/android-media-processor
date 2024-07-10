package io.github.toyota32k.media.lib.misc

import java.lang.IllegalArgumentException

class RingBuffer<T>(val capacity: Int) {
    private val list = ArrayList<T>(capacity)
    private var pos = 0
    var count = 0
        private set

    fun put(v:T) {
        if (count < capacity) {
            list.add(v)
            count++
            pos++
        } else {
            list[pos] = v
            pos++
        }
        if (pos >= capacity) {
            pos = 0
        }
    }

    fun get(i:Int):T {
        if(i<0 || count<=i) throw IllegalArgumentException("out of range at $i ($count)")
        if(count<capacity) {
            return list[i]
        } else {
            val ix = if(pos+i<count) {
                pos+i
            } else {
                pos+i-count
            }
            return list[ix]
        }
    }

    val iterable:Iterable<T>
        get() = Iterable {
            iterator {
                for(i in 0 until count) {
                    yield(get(i))
                }
            }
        }
    @Suppress("unused")
    fun toList():List<T> = iterable.toList()

    val head:T
        get() = get(0)
    val tail:T
        get() = get(count-1)
}