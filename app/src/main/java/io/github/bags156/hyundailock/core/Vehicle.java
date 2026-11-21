package io.github.bags156.hyundailock.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A car enrolled in the Bluelink account, as needed to send it commands. */
public final class Vehicle {

    /** Bluelink's registration id. Commands address the car by this plus the VIN. */
    public final String regId;
    public final String vin;
    public final String nickname;
    /** Telematics generation ("2", "3", ...). Sent as a header with every command. */
    public final String generation;
    /** "N" gas, "E" electric, "P" plug-in hybrid. */
    public final String evStatus;
    public final String model;

    public Vehicle(String regId, String vin, String nickname, String generation, String evStatus,
            String model) {
        this.regId = regId;
        this.vin = vin;
        this.nickname = nickname == null ? "" : nickname;
        this.generation = generation == null || generation.isEmpty() ? "2" : generation;
        this.evStatus = evStatus == null || evStatus.isEmpty() ? "N" : evStatus;
        this.model = model == null ? "" : model;
    }

    /** Electric cars use different climate endpoints. Plug-in hybrids use the gas ones. */
    public boolean isEv() {
        return "E".equalsIgnoreCase(evStatus);
    }

    /** "2022 Tucson": Hyundai sends names in capitals, which read as shouting on screen. */
    public String displayName() {
        if (!nickname.trim().isEmpty()) {
            return softenCapitals(nickname.trim());
        }
        String tail = vin.length() > 6 ? vin.substring(vin.length() - 6) : vin;
        return (model.isEmpty() ? "Hyundai" : softenCapitals(model)) + " …" + tail;
    }

    /** Trim and drivetrain names that stay in capitals. */
    private static final java.util.Set<String> ACRONYMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "EV", "HEV", "PHEV", "N", "GT", "SE", "SEL", "XRT", "AWD", "FWD", "HTRAC", "US"));

    /**
     * When a name is written entirely in capitals ("2022 SANTA FE"), title-cases each word except
     * trim acronyms such as EV or N. Names with any lower-case letters are left alone.
     */
    public static String softenCapitals(String text) {
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) {
                    return text;
                }
            }
        }
        if (!hasLetter) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (String word : text.split(" ", -1)) {
            if (out.length() > 0) {
                out.append(' ');
            }
            if (word.isEmpty() || ACRONYMS.contains(word) || !Character.isLetter(word.charAt(0))) {
                out.append(word);
            } else {
                out.append(word.charAt(0)).append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return out.toString();
    }

    /** Reads GET enrollment/details/{user}. Cancelled enrollments are left out. */
    public static List<Vehicle> parseEnrollment(JSONObject response) throws JSONException {
        if (response == null) {
            throw new JSONException("Empty response from Hyundai");
        }
        JSONArray entries = response.optJSONArray("enrolledVehicleDetails");
        if (entries == null) {
            throw new JSONException("Hyundai didn't list any enrolled vehicles");
        }
        List<Vehicle> result = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            JSONObject details = entry == null ? null : entry.optJSONObject("vehicleDetails");
            if (details == null) {
                continue;
            }
            if ("CANCELLED".equalsIgnoreCase(Json.str(details, "enrollmentStatus", ""))) {
                continue;
            }
            String regId = Json.str(details, "regid", null);
            String vin = Json.str(details, "vin", null);
            if (regId == null || vin == null) {
                continue;
            }
            result.add(new Vehicle(regId, vin,
                    Json.str(details, "nickName", ""),
                    Json.str(details, "vehicleGeneration", "2"),
                    Json.str(details, "evStatus", "N"),
                    Json.str(details, "modelCode", "")));
        }
        return Collections.unmodifiableList(result);
    }

    public String toJson() {
        try {
            return new JSONObject()
                    .put("regId", regId)
                    .put("vin", vin)
                    .put("nickname", nickname)
                    .put("generation", generation)
                    .put("evStatus", evStatus)
                    .put("model", model)
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads {@link #toJson()}; null when the text is missing or damaged. */
    public static Vehicle fromJson(String text) {
        JSONObject object = Json.objectOrNull(text);
        if (object == null) {
            return null;
        }
        String regId = Json.str(object, "regId", null);
        String vin = Json.str(object, "vin", null);
        if (regId == null || vin == null) {
            return null;
        }
        return new Vehicle(regId, vin,
                Json.str(object, "nickname", ""),
                Json.str(object, "generation", "2"),
                Json.str(object, "evStatus", "N"),
                Json.str(object, "model", ""));
    }
}
