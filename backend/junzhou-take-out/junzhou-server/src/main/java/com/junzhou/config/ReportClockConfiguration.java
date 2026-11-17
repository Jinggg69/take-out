package com.junzhou.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Shanghai wall clock used by reporting. Injectable so tests can pin the calendar day without
 * depending on the machine time zone or a real midnight.
 */
@Configuration
public class ReportClockConfiguration {
    public static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean(name = "reportClock")
    public Clock reportClock() {
        return Clock.system(REPORT_ZONE);
    }
}
