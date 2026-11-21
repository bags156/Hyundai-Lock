package io.github.bags156.hyundailock.core;

import org.json.JSONObject;

/** Progress of a remote command, from GET rmt/getRunningStatus. */
public enum ActionState {
    PENDING,
    SUCCESS,
    FAILED;

    /** An empty or unrecognised reply means the car hasn't answered yet. */
    public static ActionState parse(String body) {
        JSONObject json = Json.objectOrNull(body);
        if (json == null) {
            return PENDING;
        }
        String status = Json.str(json, "status", "").trim().toUpperCase(java.util.Locale.ROOT);
        switch (status) {
            case "SUCCESS":
                return SUCCESS;
            case "ERROR":
            case "FAIL":
            case "FAILED":
                return FAILED;
            default:
                return PENDING;
        }
    }
}
