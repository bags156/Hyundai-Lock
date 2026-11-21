package io.github.bags156.hyundailock.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class ParsingTest {

    @Test
    public void enrollmentSkipsCancelledAndKeepsDetails() throws JSONException {
        String body = "{\"enrolledVehicleDetails\":["
                + "{\"vehicleDetails\":{\"regid\":\"H00001\",\"vin\":\"KM8JBCAE1NU000001\","
                + "\"nickName\":\"Tucson\",\"vehicleGeneration\":\"3\",\"evStatus\":\"N\","
                + "\"modelCode\":\"TUCSON\",\"enrollmentStatus\":\"ACTIVE\"}},"
                + "{\"vehicleDetails\":{\"regid\":\"H00002\",\"vin\":\"KM8KRDAF0PU000002\","
                + "\"nickName\":null,\"evStatus\":\"E\",\"modelCode\":\"IONIQ 5\"}},"
                + "{\"vehicleDetails\":{\"regid\":\"H00003\",\"vin\":\"X\",\"enrollmentStatus\":\"CANCELLED\"}}"
                + "]}";
        List<Vehicle> vehicles = Vehicle.parseEnrollment(new JSONObject(body));
        assertEquals(2, vehicles.size());
        Vehicle tucson = vehicles.get(0);
        assertEquals("H00001", tucson.regId);
        assertEquals("3", tucson.generation);
        assertFalse(tucson.isEv());
        assertEquals("Tucson", tucson.displayName());
        Vehicle ioniq = vehicles.get(1);
        assertTrue(ioniq.isEv());
        assertEquals("2", ioniq.generation);
        assertEquals("IONIQ 5 …000002", ioniq.displayName());
    }

    @Test(expected = JSONException.class)
    public void enrollmentWithoutVehiclesIsAnError() throws JSONException {
        Vehicle.parseEnrollment(new JSONObject("{\"something\":1}"));
    }

    @Test
    public void vehicleRoundTrips() {
        Vehicle original = new Vehicle("R", "VIN123456789", "Car", "3", "P", "SANTA FE");
        Vehicle copy = Vehicle.fromJson(original.toJson());
        assertNotNull(copy);
        assertEquals(original.regId, copy.regId);
        assertEquals(original.vin, copy.vin);
        assertEquals("P", copy.evStatus);
        assertNull(Vehicle.fromJson("not json"));
        assertNull(Vehicle.fromJson(null));
    }

    @Test
    public void statusReadsUsShape() throws JSONException {
        String body = "{\"vehicleStatus\":{\"dateTime\":\"2026-11-21T15:03:22Z\",\"doorLock\":false,"
                + "\"engine\":true,\"airCtrlOn\":1,\"trunkOpen\":false,\"hoodOpen\":false,"
                + "\"doorOpen\":{\"frontLeft\":0,\"frontRight\":1,\"backLeft\":0,\"backRight\":0}}}";
        VehicleStatus status = VehicleStatus.parse(new JSONObject(body));
        assertEquals(Boolean.FALSE, status.locked);
        assertEquals("Unlocked", status.lockLabel());
        assertEquals(Boolean.TRUE, status.engineOn);
        assertEquals(Boolean.TRUE, status.climateOn);
        assertEquals(Boolean.TRUE, status.anyDoorOpen);
        assertEquals("Open: a door", status.openLabel());
        assertEquals(Long.valueOf(1_795_273_402_000L), status.reportedAtMillis());

        VehicleStatus copy = VehicleStatus.fromJson(status.toJson());
        assertNotNull(copy);
        assertEquals(Boolean.FALSE, copy.locked);
        assertEquals(Boolean.TRUE, copy.anyDoorOpen);
        assertEquals("Locked", copy.withLocked(true).lockLabel());
    }

    @Test
    public void statusWithMissingFieldsIsUnknownNotFalse() throws JSONException {
        VehicleStatus status = VehicleStatus.parse(new JSONObject("{\"vehicleStatus\":{\"doorLock\":null}}"));
        assertNull(status.locked);
        assertNull(status.engineOn);
        assertNull(status.anyDoorOpen);
        assertEquals("Unknown", status.lockLabel());
        assertEquals("unknown", VehicleStatus.onOff(status.engineOn));
        assertEquals("Nothing reported open", status.openLabel());
        assertNull(status.reportedAtMillis());
    }

    @Test
    public void timeFormats() {
        assertEquals(Long.valueOf(0L), VehicleStatus.parseTime("1970-01-01T00:00:00Z"));
        assertEquals(Long.valueOf(1_500L), VehicleStatus.parseTime("1970-01-01T00:00:01.500Z"));
        assertEquals(Long.valueOf(61_000L), VehicleStatus.parseTime("19700101000101"));
        assertNull(VehicleStatus.parseTime("yesterday"));
    }

    @Test
    public void errorsAndActionStates() throws JSONException {
        assertEquals("Invalid PIN (code 400)", Json.errorMessage(
                new JSONObject("{\"errorCode\":400,\"errorMessage\":\"Invalid PIN\"}")));
        assertNull(Json.errorMessage(new JSONObject("{\"status\":\"ok\"}")));
        assertNull(Json.errorMessage(null));
        assertEquals(ActionState.SUCCESS, ActionState.parse("{\"status\":\"SUCCESS\"}"));
        assertEquals(ActionState.FAILED, ActionState.parse("{\"status\":\"ERROR\"}"));
        assertEquals(ActionState.PENDING, ActionState.parse("{\"status\":\"IN_PROGRESS\"}"));
        assertEquals(ActionState.PENDING, ActionState.parse(""));
        assertEquals(ActionState.PENDING, ActionState.parse(null));
    }

    @Test
    public void jsonHelpersTreatNullAsMissing() throws JSONException {
        JSONObject object = new JSONObject("{\"a\":null,\"b\":\"x\",\"c\":\"1\",\"d\":\"maybe\"}");
        assertEquals("fallback", Json.str(object, "a", "fallback"));
        assertEquals("x", Json.str(object, "b", null));
        assertEquals(Boolean.TRUE, Json.bool(object, "c"));
        assertNull(Json.bool(object, "d"));
        assertNull(Json.bool(object, "missing"));
    }
}
