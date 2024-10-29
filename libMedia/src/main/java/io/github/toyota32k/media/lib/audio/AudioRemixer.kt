package io.github.toyota32k.media.lib.audio

import java.nio.ShortBuffer
import kotlin.math.min

interface AudioRemixer {
    fun remix(inSBuff: ShortBuffer, outSBuff: ShortBuffer)

    companion object {
        val DOWNMIX: AudioRemixer = object : AudioRemixer {
            private val SIGNED_SHORT_LIMIT = 32768
            private val UNSIGNED_SHORT_MAX = 65535
            override fun remix(inSBuff: ShortBuffer, outSBuff: ShortBuffer) { // Down-mix stereo to mono
                // Viktor Toth's algorithm -
                // See: http://www.vttoth.com/CMS/index.php/technical-notes/68
                //      http://stackoverflow.com/a/25102339
                val inRemaining = inSBuff.remaining() / 2
                val outSpace = outSBuff.remaining()
                val samplesToBeProcessed = min(inRemaining, outSpace)
                (0 until samplesToBeProcessed).forEach { // i ->
                    // Convert to unsigned
                    val a = inSBuff.get() + SIGNED_SHORT_LIMIT
                    val b = inSBuff.get() + SIGNED_SHORT_LIMIT
                    var m: Int // Pick the equation
                    m = if (a < SIGNED_SHORT_LIMIT || b < SIGNED_SHORT_LIMIT) { // Viktor's first equation when both sources are "quiet"
                        // (i.e. less than middle of the dynamic range)
                        a * b / SIGNED_SHORT_LIMIT
                    } else { // Viktor's second equation when one or both sources are loud
                        2 * (a + b) - a * b / SIGNED_SHORT_LIMIT - UNSIGNED_SHORT_MAX
                    } // Convert output back to signed short
                    if (m == UNSIGNED_SHORT_MAX + 1) m = UNSIGNED_SHORT_MAX
                    outSBuff.put((m - SIGNED_SHORT_LIMIT).toShort())
                }
            }
        }
        val UPMIX: AudioRemixer = object : AudioRemixer {
            override fun remix(inSBuff: ShortBuffer, outSBuff: ShortBuffer) { // Up-mix mono to stereo
                val inRemaining = inSBuff.remaining()
                val outSpace = outSBuff.remaining() / 2
                val samplesToBeProcessed = min(inRemaining, outSpace)
                (0 until samplesToBeProcessed).forEach { // i ->
                    val inSample = inSBuff.get()
                    outSBuff.put(inSample)
                    outSBuff.put(inSample)
                }
            }
        }
        val PASSTHROUGH: AudioRemixer = object : AudioRemixer {
            override fun remix(inSBuff: ShortBuffer, outSBuff: ShortBuffer) { // Passthrough
                outSBuff.put(inSBuff)
            }
        }
    }
}
