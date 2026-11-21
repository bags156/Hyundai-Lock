package io.github.bags156.hyundailock.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class ScheduleTest {

    private static final TimeZone CHICAGO = TimeZone.getTimeZone("America/Chicago");

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(CHICAGO);
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void dayBitsStartOnMonday() {
        assertEquals(Schedule.MONDAY, Schedule.dayBit(Calendar.MONDAY));
        assertEquals(Schedule.SATURDAY, Schedule.dayBit(Calendar.SATURDAY));
        assertEquals(Schedule.SUNDAY, Schedule.dayBit(Calendar.SUNDAY));
    }

    @Test
    public void nextRunLaterTheSameDay() {
        Schedule s = Schedule.coldMorning(1);
        // Friday Nov 20, 2026, 6:00 AM
        assertEquals(at(2026, 11, 20, 7, 30), s.nextRun(at(2026, 11, 20, 6, 0), CHICAGO));
    }

    @Test
    public void weekdaySchedulesSkipTheWeekend() {
        Schedule s = Schedule.coldMorning(1);
        // Friday Nov 20 at 7:30 exactly: the next one is Monday.
        assertEquals(at(2026, 11, 23, 7, 30), s.nextRun(at(2026, 11, 20, 7, 30), CHICAGO));
        // Saturday afternoon: also Monday.
        assertEquals(at(2026, 11, 23, 7, 30), s.nextRun(at(2026, 11, 21, 15, 0), CHICAGO));
    }

    @Test
    public void singleDayWaitsAWeek() {
        Schedule s = new Schedule(1, true, 9, 0, Schedule.SATURDAY, Schedule.Condition.ALWAYS, 0,
                new ClimateOptions(70, 5, false, false));
        assertEquals(at(2026, 11, 28, 9, 0), s.nextRun(at(2026, 11, 21, 9, 0), CHICAGO));
    }

    @Test
    public void noDaysNeverRuns() {
        Schedule s = new Schedule(1, true, 9, 0, 0, Schedule.Condition.ALWAYS, 0,
                new ClimateOptions(70, 5, false, false));
        assertEquals(-1, s.nextRun(at(2026, 11, 21, 9, 0), CHICAGO));
    }

    @Test
    public void keepsWallClockTimeAcrossDaylightSaving() {
        Schedule s = new Schedule(1, true, 7, 30, Schedule.EVERY_DAY, Schedule.Condition.ALWAYS, 0,
                new ClimateOptions(70, 5, false, false));
        // Clocks spring forward on Sunday March 8, 2026.
        long next = s.nextRun(at(2026, 3, 7, 8, 0), CHICAGO);
        Calendar c = Calendar.getInstance(CHICAGO);
        c.setTimeInMillis(next);
        assertEquals(8, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(7, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, c.get(Calendar.MINUTE));
        assertEquals(23 * 3_600_000L, next - at(2026, 3, 7, 7, 30));
    }

    @Test
    public void conditions() {
        Schedule cold = Schedule.coldMorning(1);
        assertTrue(cold.check(28.4).run);
        assertEquals("28°F outside", cold.check(28.4).reason);
        assertFalse(cold.check(35.0).run);
        assertEquals("52°F outside, not below 35°F", cold.check(52.0).reason);
        assertFalse(cold.check(null).run);

        Schedule hot = Schedule.hotAfternoon(2);
        assertTrue(hot.check(91.0).run);
        assertFalse(hot.check(85.0).run);
        assertEquals("70°F outside, not above 85°F", hot.check(70.0).reason);

        Schedule always = new Schedule(3, true, 7, 0, Schedule.WEEKDAYS, Schedule.Condition.ALWAYS, 0,
                new ClimateOptions(70, 5, false, false));
        assertTrue(always.check(null).run);
        assertFalse(always.needsWeather());
        assertTrue(cold.needsWeather());
    }

    @Test
    public void labels() {
        Schedule cold = Schedule.coldMorning(1);
        assertEquals("Weekdays", cold.daysLabel());
        assertEquals("If below 35°F", cold.conditionLabel());
        assertEquals("74°F · defrost · heated · 10\u00A0min", cold.actionLabel());
        assertEquals("Warm-up", cold.kindLabel());
        assertEquals("64°F · 10\u00A0min", Schedule.hotAfternoon(2).actionLabel());
        Schedule odd = new Schedule(3, true, 7, 0, Schedule.MONDAY | Schedule.FRIDAY | Schedule.SUNDAY,
                Schedule.Condition.ALWAYS, 0, new ClimateOptions(70, 5, false, false));
        assertEquals("Mon, Fri, Sun", odd.daysLabel());
        assertEquals("Any temperature", odd.conditionLabel());
    }

    @Test
    public void storageRoundTripsAndSorts() {
        List<Schedule> list = Arrays.asList(Schedule.hotAfternoon(2), Schedule.coldMorning(1).withEnabled(false));
        List<Schedule> back = Schedule.parseList(Schedule.toJson(list));
        assertEquals(2, back.size());
        assertEquals(2, back.get(0).id);
        assertEquals(Schedule.Condition.ABOVE, back.get(0).condition);
        assertFalse(back.get(1).enabled);
        assertTrue(back.get(1).climate.defrost);
        assertEquals(1, Schedule.sorted(back).get(0).id);
        assertTrue(Schedule.parseList("nonsense").isEmpty());
        assertTrue(Schedule.parseList(null).isEmpty());
    }

    @Test
    public void valuesAreClamped() {
        Schedule s = new Schedule(1, true, 30, 99, 0xFFFF, null, 500, new ClimateOptions(70, 5, false, false));
        assertEquals(23, s.hour);
        assertEquals(59, s.minute);
        assertEquals(Schedule.EVERY_DAY, s.days);
        assertEquals(Schedule.Condition.ALWAYS, s.condition);
        assertEquals(Schedule.MAX_THRESHOLD_F, s.thresholdF);
    }
}
