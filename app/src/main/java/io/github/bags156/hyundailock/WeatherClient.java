package io.github.bags156.hyundailock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import io.github.bags156.hyundailock.core.Json;
import io.github.bags156.hyundailock.core.Weather;

/** Fetches the outside temperature and searches places on Open-Meteo. Blocking. */
final class WeatherClient {

    private WeatherClient() {
    }

    private static final int TIMEOUT_MS = 15_000;

    static double currentTemp(Weather.Place place) throws IOException, BluelinkException {
        JSONObject json = get(Weather.currentUrl(place.latitude, place.longitude));
        Double temp = Weather.parseCurrentTemp(json);
        if (temp == null) {
            throw new BluelinkException("The weather service didn't return a temperature.");
        }
        return temp;
    }

    /** Searches US places first, then anywhere. */
    static List<Weather.Place> search(String query) throws IOException, BluelinkException {
        List<Weather.Place> places = Weather.parsePlaces(get(Weather.searchUrl(query, true)));
        if (places.isEmpty()) {
            places = Weather.parsePlaces(get(Weather.searchUrl(query, false)));
        }
        return places;
    }

    private static JSONObject get(String url) throws IOException, BluelinkException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("accept", "application/json");
            connection.setUseCaches(false);
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = read(stream);
            JSONObject json = Json.objectOrNull(body);
            if (code != 200 || json == null) {
                String reason = json == null ? null : Json.str(json, "reason", null);
                throw new BluelinkException("Weather service error (HTTP " + code + ")"
                        + (reason == null ? "" : ": " + reason));
            }
            return json;
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                if (out.size() > 1_000_000) {
                    throw new IOException("Weather reply was unexpectedly large");
                }
            }
            return out.toString("UTF-8");
        }
    }
}
