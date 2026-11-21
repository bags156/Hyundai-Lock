package io.github.bags156.hyundailock.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Outside temperature from Open-Meteo (open-meteo.com): free, no account or API key. The app
 * sends it only the coordinates of the place you choose, never your phone's location.
 */
public final class Weather {

    private Weather() {
    }

    public static final String FORECAST_HOST = "api.open-meteo.com";
    public static final String GEOCODING_HOST = "geocoding-api.open-meteo.com";

    public static String currentUrl(double latitude, double longitude) {
        return String.format(Locale.US, "https://%s/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m&temperature_unit=fahrenheit", FORECAST_HOST, latitude, longitude);
    }

    /** Searches a city name or US ZIP code. {@code usOnly} narrows to the United States. */
    public static String searchUrl(String query, boolean usOnly) {
        String encoded;
        try {
            encoded = URLEncoder.encode(query.trim(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return "https://" + GEOCODING_HOST + "/v1/search?count=5&language=en&format=json"
                + (usOnly ? "&countryCode=US" : "") + "&name=" + encoded;
    }

    /** The current temperature in °F, or null when the reply doesn't have one. */
    public static Double parseCurrentTemp(JSONObject response) {
        JSONObject current = response == null ? null : response.optJSONObject("current");
        if (current == null || !current.has("temperature_2m") || current.isNull("temperature_2m")) {
            return null;
        }
        double value = current.optDouble("temperature_2m", Double.NaN);
        return Double.isNaN(value) ? null : value;
    }

    public static final class Place {
        public final String name;
        public final String region;
        public final double latitude;
        public final double longitude;

        public Place(String name, String region, double latitude, double longitude) {
            this.name = name == null ? "" : name;
            this.region = region == null ? "" : region;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /** "Bentonville, Arkansas". */
        public String label() {
            return region.isEmpty() || region.equals(name) ? name : name + ", " + region;
        }

        public String toJson() {
            try {
                return new JSONObject().put("name", name).put("region", region)
                        .put("lat", latitude).put("lon", longitude).toString();
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }

        public static Place fromJson(String text) {
            JSONObject o = Json.objectOrNull(text);
            if (o == null || !o.has("lat") || !o.has("lon")) {
                return null;
            }
            double lat = o.optDouble("lat", Double.NaN);
            double lon = o.optDouble("lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                return null;
            }
            return new Place(Json.str(o, "name", ""), Json.str(o, "region", ""), lat, lon);
        }
    }

    /** Reads a geocoding search reply. No matches gives an empty list. */
    public static List<Place> parsePlaces(JSONObject response) {
        List<Place> places = new ArrayList<>();
        JSONArray results = response == null ? null : response.optJSONArray("results");
        if (results == null) {
            return places;
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) {
                continue;
            }
            double lat = r.optDouble("latitude", Double.NaN);
            double lon = r.optDouble("longitude", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                continue;
            }
            places.add(new Place(Json.str(r, "name", ""), Json.str(r, "admin1", ""), lat, lon));
        }
        return places;
    }
}
