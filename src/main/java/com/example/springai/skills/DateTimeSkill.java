package com.example.springai.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;

/**
 * Date and time skills for the AI agent.
 * Provides functions for getting current time, converting timezones, and date calculations.
 */
@Configuration
public class DateTimeSkill {

    private static final Logger log = LoggerFactory.getLogger(DateTimeSkill.class);

    public record CurrentTimeRequest(String timezone) {}
    public record CurrentTimeResponse(String dateTime, String timezone, String dayOfWeek, long epochMillis) {}

    @Bean
    @Description("Get the current date and time. Optionally provide a timezone (e.g. 'America/New_York', 'Europe/London'). Defaults to UTC.")
    public Function<CurrentTimeRequest, CurrentTimeResponse> getCurrentTime() {
        return request -> {
            String tz = (request.timezone() != null && !request.timezone().isBlank())
                    ? request.timezone() : "UTC";
            log.info("Skill invoked: getCurrentTime(timezone={})", tz);

            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));
            return new CurrentTimeResponse(
                    now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                    tz,
                    now.getDayOfWeek().name(),
                    now.toInstant().toEpochMilli()
            );
        };
    }

    public record DateDiffRequest(String startDate, String endDate) {}
    public record DateDiffResponse(long days, long weeks, String startDate, String endDate) {}

    @Bean
    @Description("Calculate the number of days between two dates. Provide dates in ISO format (YYYY-MM-DD).")
    public Function<DateDiffRequest, DateDiffResponse> calculateDateDifference() {
        return request -> {
            log.info("Skill invoked: calculateDateDifference({}, {})", request.startDate(), request.endDate());
            LocalDate start = LocalDate.parse(request.startDate());
            LocalDate end = LocalDate.parse(request.endDate());
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
            return new DateDiffResponse(days, days / 7, request.startDate(), request.endDate());
        };
    }
}
