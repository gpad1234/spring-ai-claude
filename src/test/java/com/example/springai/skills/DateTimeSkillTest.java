package com.example.springai.skills;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeSkillTest {

    private final DateTimeSkill dateTimeSkill = new DateTimeSkill();

    @Test
    void getCurrentTimeDefaultTimezone() {
        var fn = dateTimeSkill.getCurrentTime();
        var result = fn.apply(new DateTimeSkill.CurrentTimeRequest(null));
        assertEquals("UTC", result.timezone());
        assertNotNull(result.dateTime());
        assertNotNull(result.dayOfWeek());
        assertTrue(result.epochMillis() > 0);
    }

    @Test
    void getCurrentTimeSpecificTimezone() {
        var fn = dateTimeSkill.getCurrentTime();
        var result = fn.apply(new DateTimeSkill.CurrentTimeRequest("America/New_York"));
        assertEquals("America/New_York", result.timezone());
        assertNotNull(result.dateTime());
    }

    @Test
    void calculateDateDifference() {
        var fn = dateTimeSkill.calculateDateDifference();
        var result = fn.apply(new DateTimeSkill.DateDiffRequest("2025-01-01", "2025-01-31"));
        assertEquals(30, result.days());
        assertEquals(4, result.weeks());
    }
}
