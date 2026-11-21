package io.github.bags156.hyundailock.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints, headers and request bodies for Hyundai Bluelink in the US.
 *
 * This is the same unofficial interface the MyHyundai web app uses, as documented by the
 * open-source hyundai_kia_connect_api project. Hyundai can change it at any time.
 */
public final class Requests {

    private Requests() {
    }

    public static final String HOST = "api.telematics.hyundaiusa.com";
    public static final String ORIGIN = "https://" + HOST;
    public static final String LOGIN_URL = ORIGIN + "/v2/ac/oauth/token";
    public static final String API = ORIGIN + "/ac/v2/";

    public static final String STATUS_URL = API + "rcs/rvs/vehicleStatus";
    public static final String LOCK_URL = API + "rcs/rdo/off";
    public static final String UNLOCK_URL = API + "rcs/rdo/on";
    public static final String ACTION_STATUS_URL = API + "rmt/getRunningStatus";

    // Public identifiers of Hyundai's own web client, not account secrets.
    static final String CLIENT_ID = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920";
    static final String CLIENT_SECRET = "v558o935-6nne-423i-baa8";

    public static String enrollmentUrl(String username) {
        return API + "enrollment/details/" + username;
    }

    public static String climateStartUrl(Vehicle vehicle) {
        return vehicle.isEv() ? API + "evc/fatc/start" : API + "rcs/rsc/start";
    }

    public static String climateStopUrl(Vehicle vehicle) {
        return vehicle.isEv() ? API + "evc/fatc/stop" : API + "rcs/rsc/stop";
    }

    /**
     * Headers sent with every request, spelled as Hyundai's web app spells them. Callers override
     * a header by putting the same key again.
     */
    public static Map<String, String> baseHeaders(int utcOffsetHours) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", "application/json;charset=UTF-8");
        headers.put("accept", "application/json, text/plain, */*");
        headers.put("accept-language", "en-US,en;q=0.9");
        headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36");
        headers.put("origin", ORIGIN);
        headers.put("referer", ORIGIN + "/login");
        headers.put("from", "SPA");
        headers.put("to", "ISS");
        headers.put("language", "0");
        headers.put("offset", String.valueOf(utcOffsetHours));
        headers.put("refresh", "false");
        headers.put("encryptFlag", "false");
        headers.put("brandIndicator", "H");
        headers.put("client_id", CLIENT_ID);
        headers.put("clientSecret", CLIENT_SECRET);
        return headers;
    }

    /** Adds the signed-in user's headers (and the car's, when one is given). */
    public static Map<String, String> authHeaders(int utcOffsetHours, String username,
            String accessToken, String pin, Vehicle vehicle) {
        Map<String, String> headers = baseHeaders(utcOffsetHours);
        headers.put("username", username);
        headers.put("accessToken", accessToken);
        headers.put("blueLinkServicePin", pin);
        if (vehicle != null) {
            headers.put("registrationId", vehicle.regId);
            headers.put("gen", vehicle.generation);
            headers.put("vin", vehicle.vin);
        }
        return headers;
    }

    public static JSONObject login(String username, String password) {
        try {
            return new JSONObject().put("username", username).put("password", password);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JSONObject lockOrUnlock(String username, Vehicle vehicle) {
        try {
            return new JSONObject().put("userName", username).put("vin", vehicle.vin);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Remote start with climate. Gas, hybrid and EV cars take different shapes. */
    public static JSONObject climateStart(Vehicle vehicle, String username, ClimateOptions options) {
        try {
            JSONObject body = new JSONObject();
            if (vehicle.isEv()) {
                body.put("airCtrl", 1);
                body.put("airTemp", new JSONObject().put("value", String.valueOf(options.tempF)).put("unit", 1));
                body.put("defrost", options.defrost);
                body.put("heating1", options.heat ? 1 : 0);
                // Older EVs reject these fields.
                if ("3".equals(vehicle.generation)) {
                    body.put("igniOnDuration", options.minutes);
                    body.put("seatHeaterVentInfo", seatsOff());
                }
            } else {
                body.put("Ims", 0);
                body.put("airCtrl", 1);
                body.put("airTemp", new JSONObject().put("unit", 1).put("value", options.tempF));
                body.put("defrost", options.defrost);
                body.put("heating1", options.heat ? 1 : 0);
                body.put("igniOnDuration", options.minutes);
                body.put("seatHeaterVentInfo", seatsOff());
                body.put("username", username);
                // Hyundai's gas-car endpoint takes the registration id in the "vin" field.
                body.put("vin", vehicle.regId);
            }
            return body;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject seatsOff() throws JSONException {
        return new JSONObject()
                .put("drvSeatHeatState", 0)
                .put("astSeatHeatState", 0)
                .put("rlSeatHeatState", 0)
                .put("rrSeatHeatState", 0);
    }
}
