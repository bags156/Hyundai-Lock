package io.github.bags156.hyundailock.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

public class RequestsTest {

    private final Vehicle gas = new Vehicle("REG1", "VIN1", "", "3", "N", "");
    private final Vehicle ev = new Vehicle("REG2", "VIN2", "", "3", "E", "");
    private final Vehicle olderEv = new Vehicle("REG3", "VIN3", "", "2", "E", "");

    @Test
    public void endpointsDependOnPowertrain() {
        assertTrue(Requests.climateStartUrl(gas).endsWith("/ac/v2/rcs/rsc/start"));
        assertTrue(Requests.climateStopUrl(gas).endsWith("/ac/v2/rcs/rsc/stop"));
        assertTrue(Requests.climateStartUrl(ev).endsWith("/ac/v2/evc/fatc/start"));
        assertTrue(Requests.climateStopUrl(ev).endsWith("/ac/v2/evc/fatc/stop"));
        assertTrue(Requests.LOCK_URL.endsWith("/rcs/rdo/off"));
        assertTrue(Requests.UNLOCK_URL.endsWith("/rcs/rdo/on"));
    }

    @Test
    public void headersIncludeAccountAndCar() {
        Map<String, String> headers = Requests.authHeaders(-6, "me@example.com", "TOKEN", "1234", gas);
        assertEquals("-6", headers.get("offset"));
        assertEquals("me@example.com", headers.get("username"));
        assertEquals("TOKEN", headers.get("accessToken"));
        assertEquals("1234", headers.get("blueLinkServicePin"));
        assertEquals("REG1", headers.get("registrationId"));
        assertEquals("3", headers.get("gen"));
        assertEquals("VIN1", headers.get("vin"));
        assertEquals("H", headers.get("brandIndicator"));
        assertFalse(Requests.authHeaders(0, "u", "t", "p", null).containsKey("vin"));
    }

    @Test
    public void lockBody() throws JSONException {
        JSONObject body = Requests.lockOrUnlock("me@example.com", gas);
        assertEquals("me@example.com", body.getString("userName"));
        assertEquals("VIN1", body.getString("vin"));
    }

    @Test
    public void gasClimateBodyUsesRegistrationIdAndDuration() throws JSONException {
        JSONObject body = Requests.climateStart(gas, "me@example.com", new ClimateOptions(99, 0, true, true));
        assertEquals("REG1", body.getString("vin"));
        assertEquals(82, body.getJSONObject("airTemp").getInt("value"));
        assertEquals(1, body.getInt("igniOnDuration"));
        assertEquals(1, body.getInt("heating1"));
        assertTrue(body.getBoolean("defrost"));
        assertTrue(body.has("seatHeaterVentInfo"));
    }

    @Test
    public void evClimateBodyDependsOnGeneration() throws JSONException {
        JSONObject newer = Requests.climateStart(ev, "u", new ClimateOptions(70, 5, false, false));
        assertEquals("70", newer.getJSONObject("airTemp").getString("value"));
        assertEquals(5, newer.getInt("igniOnDuration"));
        assertFalse(newer.has("vin"));
        JSONObject older = Requests.climateStart(olderEv, "u", new ClimateOptions(70, 5, false, false));
        assertFalse(older.has("igniOnDuration"));
        assertFalse(older.has("seatHeaterVentInfo"));
    }
}
