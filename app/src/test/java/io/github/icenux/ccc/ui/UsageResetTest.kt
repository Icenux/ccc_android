package io.github.icenux.ccc.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class UsageResetTest {
    private val now = ZonedDateTime.of(2026, 11, 11, 17, 0, 0, 0, ZoneId.of("UTC"))

    @Test fun resetLaterTodayShowsClockAndTimeLeft() {
        val label = resetLabel("2026-11-11T19:12:00+00:00", now)
        assertEquals("19:12" to "2 h 12 min", label)
    }

    @Test fun resetInDaysShowsDaysAndHours() {
        assertEquals("1 d 20 h", resetLabel("2026-11-13T13:00:00+00:00", now)?.second)
    }

    @Test fun unreadableMomentGivesNothing() {
        assertNull(resetLabel("soon", now))
    }
}
