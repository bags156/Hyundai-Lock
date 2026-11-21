package io.github.bags156.hyundailock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

import io.github.bags156.hyundailock.core.ActionState;
import io.github.bags156.hyundailock.core.ClimateOptions;
import io.github.bags156.hyundailock.core.Json;
import io.github.bags156.hyundailock.core.Requests;
import io.github.bags156.hyundailock.core.Vehicle;
import io.github.bags156.hyundailock.core.VehicleStatus;

/**
 * Talks to Hyundai's Bluelink servers (US). Blocking; call off the main thread. The access token
 * lives only in memory, so a restarted app signs in again (about a second).
 */
final class BluelinkClient {

    static final class Credentials {
        final String email;
        final String password;
        final String pin;

        Credentials(String email, String password, String pin) {
            this.email = email;
            this.password = password;
            this.pin = pin;
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 45_000;
    /** Waking the car makes Hyundai's server wait for the car to answer. */
    private static final int WAKE_READ_TIMEOUT_MS = 120_000;
    private static final long TOKEN_MARGIN_MS = 60_000;
    private static final String[] TRANSACTION_HEADERS = {"tmsTid", "transactionId", "Xid"};

    private String accessToken;
    private String tokenEmail;
    private long tokenExpiresAt;

    synchronized void forget() {
        accessToken = null;
        tokenEmail = null;
        tokenExpiresAt = 0;
    }

    /** Signs in and lists the account's cars. */
    List<Vehicle> signIn(Credentials credentials) throws IOException, BluelinkException {
        forget();
        login(credentials);
        Response response = authed(credentials, null, "GET",
                Requests.enrollmentUrl(credentials.email), null, null, READ_TIMEOUT_MS);
        try {
            List<Vehicle> vehicles = Vehicle.parseEnrollment(response.json());
            if (vehicles.isEmpty()) {
                throw new BluelinkException("This MyHyundai account has no active Bluelink cars.");
            }
            return vehicles;
        } catch (JSONException e) {
            throw new BluelinkException("Couldn't read the car list: " + e.getMessage());
        }
    }

    /**
     * Hyundai's last saved status, or (with {@code wake}) a fresh one from the car itself, which
     * is slower and uses up the car's limited remote requests.
     */
    VehicleStatus status(Credentials credentials, Vehicle vehicle, boolean wake)
            throws IOException, BluelinkException {
        Map<String, String> extra = new java.util.HashMap<>();
        extra.put("refresh", wake ? "true" : "false");
        Response response = authed(credentials, vehicle, "GET", Requests.STATUS_URL, extra, null,
                wake ? WAKE_READ_TIMEOUT_MS : READ_TIMEOUT_MS);
        try {
            return VehicleStatus.parse(response.json());
        } catch (JSONException e) {
            throw new BluelinkException("Couldn't read the car's status: " + e.getMessage());
        }
    }

    /** Sends lock or unlock. Returns a transaction id to confirm with, or null if none came back. */
    String lock(Credentials credentials, Vehicle vehicle, boolean lock)
            throws IOException, BluelinkException {
        Map<String, String> extra = new java.util.HashMap<>();
        extra.put("APPCLOUD-VIN", vehicle.vin);
        Response response = authed(credentials, vehicle, "POST",
                lock ? Requests.LOCK_URL : Requests.UNLOCK_URL, extra,
                Requests.lockOrUnlock(credentials.email, vehicle).toString(), READ_TIMEOUT_MS);
        return response.transactionId;
    }

    String startClimate(Credentials credentials, Vehicle vehicle, ClimateOptions options)
            throws IOException, BluelinkException {
        Response response = authed(credentials, vehicle, "POST", Requests.climateStartUrl(vehicle),
                null, Requests.climateStart(vehicle, credentials.email, options).toString(),
                READ_TIMEOUT_MS);
        return response.transactionId;
    }

    String stopClimate(Credentials credentials, Vehicle vehicle) throws IOException, BluelinkException {
        Response response = authed(credentials, vehicle, "POST", Requests.climateStopUrl(vehicle),
                null, "", READ_TIMEOUT_MS);
        return response.transactionId;
    }

    /** Asks whether the car has carried out a command yet. */
    ActionState actionState(Credentials credentials, Vehicle vehicle, String transactionId)
            throws IOException, BluelinkException {
        Map<String, String> extra = new java.util.HashMap<>();
        extra.put("tid", transactionId);
        extra.put("login_id", credentials.email);
        extra.put("service_type", "REMOTE_POLL");
        Response response = authed(credentials, vehicle, "GET", Requests.ACTION_STATUS_URL, extra,
                null, READ_TIMEOUT_MS);
        return ActionState.parse(response.body);
    }

    // ---- plumbing ----

    private synchronized void ensureToken(Credentials credentials) throws IOException, BluelinkException {
        boolean valid = accessToken != null
                && credentials.email.equals(tokenEmail)
                && System.currentTimeMillis() < tokenExpiresAt - TOKEN_MARGIN_MS;
        if (!valid) {
            login(credentials);
        }
    }

    private synchronized void login(Credentials credentials) throws IOException, BluelinkException {
        Response response = send("POST", Requests.LOGIN_URL, Requests.baseHeaders(utcOffsetHours()),
                Requests.login(credentials.email, credentials.password).toString(), READ_TIMEOUT_MS);
        JSONObject json = Json.objectOrNull(response.body);
        String error = Json.errorMessage(json);
        if (error != null) {
            throw new BluelinkException("Sign-in failed: " + error, Json.errorCode(json));
        }
        String token = Json.str(json, "access_token", null);
        if (token == null) {
            throw new BluelinkException(response.code == 401 || response.code == 400
                    ? "Sign-in failed. Check your MyHyundai email and password."
                    : "Sign-in failed (HTTP " + response.code + ").", String.valueOf(response.code));
        }
        double expiresIn = 1800;
        try {
            expiresIn = Double.parseDouble(Json.str(json, "expires_in", "1800"));
        } catch (NumberFormatException ignored) {
            // Keep the default.
        }
        accessToken = token;
        tokenEmail = credentials.email;
        tokenExpiresAt = System.currentTimeMillis() + (long) (expiresIn * 1000);
    }

    /** Sends a signed-in request, signing in again once if the token was rejected. */
    private Response authed(Credentials credentials, Vehicle vehicle, String method, String url,
            Map<String, String> extraHeaders, String body, int readTimeoutMs)
            throws IOException, BluelinkException {
        ensureToken(credentials);
        Response response = send(method, url, headers(credentials, vehicle, extraHeaders), body,
                readTimeoutMs);
        if (response.looksUnauthorized()) {
            synchronized (this) {
                accessToken = null;
            }
            ensureToken(credentials);
            response = send(method, url, headers(credentials, vehicle, extraHeaders), body,
                    readTimeoutMs);
        }
        response.throwIfError();
        return response;
    }

    private synchronized Map<String, String> headers(Credentials credentials, Vehicle vehicle,
            Map<String, String> extra) {
        Map<String, String> headers = Requests.authHeaders(utcOffsetHours(), credentials.email,
                accessToken, credentials.pin, vehicle);
        if (extra != null) {
            headers.putAll(extra);
        }
        return headers;
    }

    private static int utcOffsetHours() {
        return TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000;
    }

    private static Response send(String method, String url, Map<String, String> headers, String body,
            int readTimeoutMs) throws IOException, BluelinkException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod(method);
            connection.setUseCaches(false);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getValue() != null) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = read(stream);
            String transactionId = null;
            for (String name : TRANSACTION_HEADERS) {
                String value = connection.getHeaderField(name);
                if (value != null && !value.trim().isEmpty()) {
                    transactionId = value.trim();
                    break;
                }
            }
            return new Response(code, text, transactionId);
        } catch (UnknownHostException e) {
            throw new BluelinkException("No internet connection, or Hyundai's server can't be reached.");
        } catch (SocketTimeoutException e) {
            throw new BluelinkException("Hyundai's server didn't answer in time.");
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
                if (out.size() > 2_000_000) {
                    throw new IOException("Hyundai's reply was unexpectedly large");
                }
            }
            return out.toString("UTF-8");
        }
    }

    private static final class Response {
        final int code;
        final String body;
        final String transactionId;

        Response(int code, String body, String transactionId) {
            this.code = code;
            this.body = body == null ? "" : body;
            this.transactionId = transactionId;
        }

        JSONObject json() throws BluelinkException {
            JSONObject json = Json.objectOrNull(body);
            if (json == null) {
                throw new BluelinkException("Hyundai sent an empty or unreadable reply.");
            }
            return json;
        }

        boolean looksUnauthorized() {
            if (code == 401) {
                return true;
            }
            JSONObject json = Json.objectOrNull(body);
            String errorCode = Json.errorCode(json);
            return "401".equals(errorCode);
        }

        void throwIfError() throws BluelinkException {
            JSONObject json = Json.objectOrNull(body);
            String error = Json.errorMessage(json);
            if (error != null) {
                throw new BluelinkException("Hyundai: " + error, Json.errorCode(json));
            }
            if (code != 200) {
                String snippet = body.trim();
                if (snippet.length() > 160) {
                    snippet = snippet.substring(0, 160) + "…";
                }
                throw new BluelinkException("Hyundai's server returned HTTP " + code
                        + (snippet.isEmpty() ? "" : ": " + snippet), String.valueOf(code));
            }
        }
    }
}
