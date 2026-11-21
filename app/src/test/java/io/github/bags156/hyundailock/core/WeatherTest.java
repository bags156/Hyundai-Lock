package io.github.bags156.hyundailock.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class WeatherTest {

    @Test
    public void urls() {
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=36.3729&longitude=-94.2088"
                + "&current=temperature_2m&temperature_unit=fahrenheit", Weather.currentUrl(36.37285, -94.20882));
        assertTrue(Weather.searchUrl(" Bentonville, AR ", true).endsWith("&countryCode=US&name=Bentonville%2C+AR"));
        assertTrue(Weather.searchUrl("72712", false).endsWith("&format=json&name=72712"));
    }

    @Test
    public void currentTemperature() throws JSONException {
        assertEquals(31.6, Weather.parseCurrentTemp(new JSONObject(
                "{\"current\":{\"time\":\"2026-11-21T16:00\",\"interval\":900,\"temperature_2m\":31.6}}")), 0.001);
        assertNull(Weather.parseCurrentTemp(new JSONObject("{\"current\":{\"temperature_2m\":null}}")));
        assertNull(Weather.parseCurrentTemp(new JSONObject("{\"error\":true,\"reason\":\"x\"}")));
        assertNull(Weather.parseCurrentTemp(null));
    }

    @Test
    public void places() throws JSONException {
        List<Weather.Place> places = Weather.parsePlaces(new JSONObject("{\"results\":["
                + "{\"name\":\"Bentonville\",\"latitude\":36.37285,\"longitude\":-94.20882,\"admin1\":\"Arkansas\"},"
                + "{\"name\":\"Nowhere\"}]}"));
        assertEquals(1, places.size());
        assertEquals("Bentonville, Arkansas", places.get(0).label());
        Weather.Place copy = Weather.Place.fromJson(places.get(0).toJson());
        assertNotNull(copy);
        assertEquals(-94.20882, copy.longitude, 1e-9);
        assertTrue(Weather.parsePlaces(new JSONObject("{\"generationtime_ms\":0.5}")).isEmpty());
        assertNull(Weather.Place.fromJson("{}"));
    }
}
