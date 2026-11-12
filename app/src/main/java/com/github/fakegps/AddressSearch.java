package com.github.fakegps;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.tencent.fakegps.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Address search: Android Geocoder first, OpenStreetMap Nominatim as a fallback.
 * Both run off the main thread; Nominatim needs no API key.
 */
public final class AddressSearch {

    private static final int MAX_RESULTS = 5;

    public static final class Result {
        public final String label;
        public final double latitude;
        public final double longitude;

        Result(String label, double latitude, double longitude) {
            this.label = label;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private AddressSearch() {
    }

    public static List<Result> search(Context context, String query) {
        List<Result> results = searchWithGeocoder(context, query);
        if (results.isEmpty()) {
            results = searchWithNominatim(query);
        }
        return results;
    }

    private static List<Result> searchWithGeocoder(Context context, String query) {
        List<Result> results = new ArrayList<>();
        try {
            if (!Geocoder.isPresent()) {
                return results;
            }
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocationName(query, MAX_RESULTS);
            if (addresses == null) {
                return results;
            }
            for (Address address : addresses) {
                if (!address.hasLatitude() || !address.hasLongitude()) {
                    continue;
                }
                results.add(new Result(labelFor(address), address.getLatitude(),
                        address.getLongitude()));
            }
        } catch (Exception ignored) {
            // No geocoder service, no network, or a timeout: try Nominatim.
        }
        return results;
    }

    private static String labelFor(Address address) {
        String line = address.getAddressLine(0);
        if (line != null && !line.trim().isEmpty()) {
            return line;
        }
        StringBuilder builder = new StringBuilder();
        append(builder, address.getLocality());
        append(builder, address.getAdminArea());
        append(builder, address.getCountryName());
        if (builder.length() == 0) {
            builder.append(String.format(Locale.US, "%.6f, %.6f",
                    address.getLatitude(), address.getLongitude()));
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(value);
    }

    private static List<Result> searchWithNominatim(String query) {
        List<Result> results = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit="
                    + MAX_RESULTS + "&q=" + URLEncoder.encode(query, "UTF-8");
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent",
                    "FakeGPS/" + BuildConfig.VERSION_NAME
                            + " (https://github.com/Miku0139oao/FakeGPS)");
            if (connection.getResponseCode() != 200) {
                return results;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            JSONArray array = new JSONArray(body.toString());
            for (int i = 0; i < array.length() && i < MAX_RESULTS; i++) {
                JSONObject item = array.getJSONObject(i);
                String label = item.optString("display_name");
                double latitude = Double.parseDouble(item.getString("lat"));
                double longitude = Double.parseDouble(item.getString("lon"));
                if (!label.isEmpty()) {
                    results.add(new Result(label, latitude, longitude));
                }
            }
        } catch (Exception ignored) {
            // Offline or the service is unavailable.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return results;
    }
}
