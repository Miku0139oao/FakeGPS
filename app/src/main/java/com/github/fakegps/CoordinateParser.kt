package com.github.fakegps

import com.github.fakegps.model.LocPoint
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Parses plain coordinates, geo: URIs, map URLs and DMS strings.
 */
object CoordinateParser {

    private val PLAIN = Pattern.compile(
        """^\s*\(?\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)?\s*$"""
    )
    private val GEO = Pattern.compile(
        """geo:(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""",
        Pattern.CASE_INSENSITIVE
    )
    private val URL = Pattern.compile(
        """[@?&](?:q=|query=|ll=|center=)?(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""",
        Pattern.CASE_INSENSITIVE
    )
    private val DMS = Pattern.compile(
        """(\d{1,3})\s*[°ºd]\s*(\d{1,2})?\s*['′m]?\s*([\d.]+)?\s*["″s]?\s*([NSEW])""",
        Pattern.CASE_INSENSITIVE
    )

    @JvmStatic
    fun parse(input: String?): LocPoint? {
        if (input == null) {
            return null
        }
        val text = input.trim()
        if (text.isEmpty()) {
            return null
        }

        var point = parseDecimal(text, PLAIN)
        if (point == null) {
            point = parseDecimal(text, GEO)
        }
        if (point == null) {
            point = parseDecimal(text, URL)
        }
        if (point == null) {
            point = parseDms(text)
        }
        return point
    }

    private fun parseDecimal(text: String, pattern: Pattern): LocPoint? {
        val matcher = pattern.matcher(text)
        if (!matcher.find()) {
            return null
        }
        return try {
            val latitude = matcher.group(1).toDouble()
            val longitude = matcher.group(2).toDouble()
            if (FakeGpsUtils.isValidLatitude(latitude) &&
                FakeGpsUtils.isValidLongitude(longitude)
            ) {
                LocPoint(latitude, longitude)
            } else {
                null
            }
        } catch (ignored: NumberFormatException) {
            // Fall through to the next parser.
            null
        }
    }

    private fun parseDms(text: String): LocPoint? {
        val matcher = DMS.matcher(text)
        if (!matcher.find()) {
            return null
        }
        val first = dmsToDecimal(matcher)
        val firstHemisphere = matcher.group(4).uppercase(Locale.getDefault()).first()
        if (!matcher.find()) {
            return null
        }
        val second = dmsToDecimal(matcher)
        val secondHemisphere = matcher.group(4).uppercase(Locale.getDefault()).first()

        val latitude: Double
        val longitude: Double
        if (firstHemisphere == 'N' || firstHemisphere == 'S') {
            latitude = first
            longitude = second
        } else {
            latitude = second
            longitude = first
        }
        if (!FakeGpsUtils.isValidLatitude(latitude) ||
            !FakeGpsUtils.isValidLongitude(longitude)
        ) {
            return null
        }
        return LocPoint(latitude, longitude)
    }

    private fun dmsToDecimal(matcher: Matcher): Double {
        val degrees = matcher.group(1).toDouble()
        val minutes = matcher.group(2)?.toDouble() ?: 0.0
        val seconds = matcher.group(3)?.toDouble() ?: 0.0
        var value = degrees + minutes / 60.0 + seconds / 3600.0
        val hemisphere = matcher.group(4).uppercase(Locale.getDefault()).first()
        if (hemisphere == 'S' || hemisphere == 'W') {
            value = -value
        }
        return value
    }
}
