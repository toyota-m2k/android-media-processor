package io.github.toyota32k.media.lib.misc

import java.lang.NumberFormatException
import java.util.regex.Pattern

object ISO6709LocationParser {

    /**
     * This method parses the given string representing a geographic point location by coordinates in ISO 6709 format
     * and returns the latitude and the longitude in float. If `location` is not in ISO 6709 format,
     * this method returns `null`
     *
     * @param location a String representing a geographic point location by coordinates in ISO 6709 format
     * @return `null` if the given string is not as expected, an array of floats with size 2,
     * where the first element represents latitude and the second represents longitude, otherwise.
     */
    fun parse(location: String?): FloatArray? {
        if (location == null) return null
        val pattern = Pattern.compile("([+\\-][0-9.]+)([+\\-][0-9.]+)")
        val m = pattern.matcher(location)
        if (m.find() && m.groupCount() == 2) {
            val latstr = m.group(1) ?: return null
            val lonstr = m.group(2) ?: return null
            try {
                val lat = latstr.toFloat()
                val lon = lonstr.toFloat()
                return floatArrayOf(lat, lon)
            } catch (ignored: NumberFormatException) {
            }
        }
        return null
    }

}
