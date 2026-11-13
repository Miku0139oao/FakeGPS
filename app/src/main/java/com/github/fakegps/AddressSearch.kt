package com.github.fakegps

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.tencent.fakegps.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Address search: Android Geocoder first, OpenStreetMap Nominatim as a fallback.
 * The suspend function always performs its work off the main thread and checks
 * for cancellation between the two providers.
 */
object AddressSearch {

    private const val MAX_RESULTS = 5

    data class Result(
        val label: String,
        val latitude: Double,
        val longitude: Double
    )

    suspend fun search(context: Context, query: String): List<Result> =
        withContext(Dispatchers.IO) {
            ensureActive()
            val geocoderResults = searchWithGeocoder(context, query)
            ensureActive()
            if (geocoderResults.isEmpty()) {
                searchWithNominatim(query)
            } else {
                geocoderResults
            }
        }

    @Suppress("DEPRECATION") // The synchronous API is the only one available below API 33.
    private fun searchWithGeocoder(context: Context, query: String): List<Result> {
        val results = ArrayList<Result>()
        try {
            if (!Geocoder.isPresent()) {
                return results
            }
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(query, MAX_RESULTS) ?: return results
            for (address in addresses) {
                if (!address.hasLatitude() || !address.hasLongitude()) {
                    continue
                }
                results.add(
                    Result(
                        labelFor(address),
                        address.latitude,
                        address.longitude
                    )
                )
            }
        } catch (ignored: Exception) {
            // No geocoder service, no network, or a timeout: try Nominatim.
        }
        return results
    }

    private fun labelFor(address: Address): String {
        val line = address.getAddressLine(0)
        if (!line.isNullOrBlank()) {
            return line
        }
        val builder = StringBuilder()
        append(builder, address.locality)
        append(builder, address.adminArea)
        append(builder, address.countryName)
        if (builder.isEmpty()) {
            builder.append(
                String.format(Locale.US, "%.6f, %.6f", address.latitude, address.longitude)
            )
        }
        return builder.toString()
    }

    private fun append(builder: StringBuilder, value: String?) {
        if (value.isNullOrBlank()) {
            return
        }
        if (builder.isNotEmpty()) {
            builder.append(", ")
        }
        builder.append(value)
    }

    private fun searchWithNominatim(query: String): List<Result> {
        val results = ArrayList<Result>()
        var connection: HttpURLConnection? = null
        try {
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=" +
                MAX_RESULTS + "&q=" + URLEncoder.encode(query, "UTF-8")
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty(
                "User-Agent",
                "FakeGPS/" + BuildConfig.VERSION_NAME +
                    " (https://github.com/Miku0139oao/FakeGPS)"
            )
            if (connection.responseCode != 200) {
                return results
            }
            val body = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    body.append(line)
                    line = reader.readLine()
                }
            }
            val array = JSONArray(body.toString())
            var i = 0
            while (i < array.length() && i < MAX_RESULTS) {
                val item = array.getJSONObject(i)
                val label = item.optString("display_name")
                val latitude = item.getString("lat").toDouble()
                val longitude = item.getString("lon").toDouble()
                if (label.isNotEmpty()) {
                    results.add(Result(label, latitude, longitude))
                }
                i++
            }
        } catch (ignored: Exception) {
            // Offline or the service is unavailable.
        } finally {
            connection?.disconnect()
        }
        return results
    }
}
