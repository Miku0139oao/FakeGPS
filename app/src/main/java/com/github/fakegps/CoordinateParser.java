package com.github.fakegps;

import com.github.fakegps.model.LocPoint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses plain coordinates, geo: URIs, map URLs, and DMS strings.
 */
public final class CoordinateParser {

    private static final Pattern PLAIN = Pattern.compile(
            "^\\s*\\(?\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)?\\s*$");
    private static final Pattern GEO = Pattern.compile(
            "geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile(
            "[@?&](?:q=|query=|ll=|center=)?(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DMS = Pattern.compile(
            "(\\d{1,3})\\s*[°ºd]\\s*(\\d{1,2})?\\s*['′m]?\\s*([\\d.]+)?\\s*[\"″s]?\\s*([NSEW])",
            Pattern.CASE_INSENSITIVE);

    private CoordinateParser() {
    }

    public static LocPoint parse(String input) {
        if (input == null) {
            return null;
        }
        String text = input.trim();
        if (text.isEmpty()) {
            return null;
        }

        LocPoint point = parseDecimal(text, PLAIN);
        if (point == null) {
            point = parseDecimal(text, GEO);
        }
        if (point == null) {
            point = parseDecimal(text, URL);
        }
        if (point == null) {
            point = parseDms(text);
        }
        return point;
    }

    private static LocPoint parseDecimal(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(matcher.group(1));
            double longitude = Double.parseDouble(matcher.group(2));
            if (FakeGpsUtils.isValidLatitude(latitude)
                    && FakeGpsUtils.isValidLongitude(longitude)) {
                return new LocPoint(latitude, longitude);
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the next parser.
        }
        return null;
    }

    private static LocPoint parseDms(String text) {
        Matcher matcher = DMS.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        double first = dmsToDecimal(matcher);
        char firstHemisphere = matcher.group(4).toUpperCase().charAt(0);
        if (!matcher.find()) {
            return null;
        }
        double second = dmsToDecimal(matcher);
        char secondHemisphere = matcher.group(4).toUpperCase().charAt(0);

        double latitude;
        double longitude;
        if (firstHemisphere == 'N' || firstHemisphere == 'S') {
            latitude = first;
            longitude = second;
        } else {
            latitude = second;
            longitude = first;
        }
        if (!FakeGpsUtils.isValidLatitude(latitude)
                || !FakeGpsUtils.isValidLongitude(longitude)) {
            return null;
        }
        return new LocPoint(latitude, longitude);
    }

    private static double dmsToDecimal(Matcher matcher) {
        double degrees = Double.parseDouble(matcher.group(1));
        double minutes = matcher.group(2) == null ? 0 : Double.parseDouble(matcher.group(2));
        double seconds = matcher.group(3) == null ? 0 : Double.parseDouble(matcher.group(3));
        double value = degrees + minutes / 60.0 + seconds / 3600.0;
        char hemisphere = matcher.group(4).toUpperCase().charAt(0);
        if (hemisphere == 'S' || hemisphere == 'W') {
            value = -value;
        }
        return value;
    }
}
