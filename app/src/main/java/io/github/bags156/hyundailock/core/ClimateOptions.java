package io.github.bags156.hyundailock.core;

/** Remote start / climate settings. Values are clamped to what Bluelink US accepts. */
public final class ClimateOptions {

    public static final int MIN_TEMP_F = 62;
    public static final int MAX_TEMP_F = 82;
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 10;

    public final int tempF;
    public final int minutes;
    public final boolean defrost;
    /** Heated extras such as the steering wheel and rear defroster, where the car has them. */
    public final boolean heat;

    public ClimateOptions(int tempF, int minutes, boolean defrost, boolean heat) {
        this.tempF = clamp(tempF, MIN_TEMP_F, MAX_TEMP_F);
        this.minutes = clamp(minutes, MIN_MINUTES, MAX_MINUTES);
        this.defrost = defrost;
        this.heat = heat;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
