package io.github.bags156.hyundailock.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class SmallPartsTest {

    @Test
    public void delays() {
        assertEquals("15 seconds", Delays.label(15_000));
        assertEquals("1 minute", Delays.label(60_000));
        assertEquals("2 minutes", Delays.label(120_000));
        assertEquals(3, Delays.indexOf(Delays.DEFAULT_MS));
        assertEquals(Delays.CHOICES_MS[0], Delays.at(-5));
        assertEquals(Delays.CHOICES_MS[Delays.CHOICES_MS.length - 1], Delays.at(99));
        assertEquals(2, Delays.indexOf(70_000));
    }

    @Test
    public void notificationTextSkipsRepeats() {
        assertEquals("Vehicle Alert · Doors unlocked at 3:02 PM",
                NotificationText.combine("Vehicle Alert", "Doors unlocked", "Doors unlocked at 3:02 PM",
                        null, "", "Vehicle Alert"));
        assertEquals("", NotificationText.combine());
        assertTrue(NotificationText.combine(repeat('x', 1000)).length() <= NotificationText.MAX_LENGTH);
    }

    @Test
    public void eventLogIsNewestFirstAndBounded() {
        String log = null;
        for (int i = 0; i < EventLog.MAX_ENTRIES + 5; i++) {
            log = EventLog.append(log, i, "event " + i);
        }
        List<EventLog.Entry> entries = EventLog.parse(log);
        assertEquals(EventLog.MAX_ENTRIES, entries.size());
        assertEquals("event " + (EventLog.MAX_ENTRIES + 4), entries.get(0).text);
        assertEquals(3, EventLog.newest(log, 3).size());
        assertTrue(EventLog.parse("garbage").isEmpty());
        assertEquals("a b", EventLog.parse(EventLog.append(null, 1, "a\nb")).get(0).text);
    }

    @Test
    public void climateClamps() {
        ClimateOptions low = new ClimateOptions(0, -3, false, false);
        assertEquals(ClimateOptions.MIN_TEMP_F, low.tempF);
        assertEquals(ClimateOptions.MIN_MINUTES, low.minutes);
        ClimateOptions high = new ClimateOptions(200, 60, false, false);
        assertEquals(ClimateOptions.MAX_TEMP_F, high.tempF);
        assertEquals(ClimateOptions.MAX_MINUTES, high.minutes);
    }

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(c);
        }
        return b.toString();
    }
}
