package app.lumen

import app.lumen.zeus.TimeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeRulesTest {
    private val zone = ZoneId.of("America/Toronto")
    private fun at(h: Int, m: Int, day: Int = 28) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone)

    @Test fun durations() {
        assertEquals(600, TimeRules.durationSeconds("set a timer for 10 minutes"))
        assertEquals(5400, TimeRules.durationSeconds("timer for 1 hour 30 minutes"))
        assertEquals(1800, TimeRules.durationSeconds("timer for half an hour"))
        assertEquals(90, TimeRules.durationSeconds("90 seconds"))
        assertEquals(60, TimeRules.durationSeconds("a minute"))
        assertEquals(2700, TimeRules.durationSeconds("forty five minutes"))
        assertNull(TimeRules.durationSeconds("set a timer"))
    }

    @Test fun alarmsPickTheNextMatchingTime() {
        assertEquals(at(7, 0, 29), TimeRules.nextAlarm(7, 0, "am", at(21, 0)))
        assertEquals(at(19, 0), TimeRules.nextAlarm(7, 0, "pm", at(9, 0)))
        // No am/pm: whichever 7 o'clock comes first.
        assertEquals(at(7, 0, 29), TimeRules.nextAlarm(7, 0, null, at(21, 0)))
        assertEquals(at(19, 0), TimeRules.nextAlarm(7, 0, null, at(8, 0)))
        assertEquals(at(19, 45), TimeRules.nextAlarm(19, 45, null, at(8, 0)))
        assertEquals(at(0, 0, 29), TimeRules.nextAlarm(12, 0, "am", at(21, 0)))
        assertNull(TimeRules.nextAlarm(25, 0, null, at(8, 0)))
        assertNull(TimeRules.nextAlarm(13, 0, "pm", at(8, 0)))
    }

    @Test fun readsClockTimes() {
        assertEquals(Triple(7, 0, "am"), TimeRules.clockIn("set an alarm for 7 am"))
        assertEquals(Triple(6, 30, "pm"), TimeRules.clockIn("wake me up at 6 30 p m"))
        assertEquals(Triple(19, 45, null), TimeRules.clockIn("alarm at 19:45"))
        assertNull(TimeRules.clockIn("alarm in 10 minutes"))
    }

    @Test fun describesDurations() {
        assertEquals("10 minutes", TimeRules.describe(600))
        assertEquals("1 hour 30 minutes", TimeRules.describe(5400))
        assertEquals("45 seconds", TimeRules.describe(45))
        assertEquals("1 minute 30 seconds", TimeRules.describe(90))
    }
}
