package com.junzhou.payment;

import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class MockPaymentTimeTest {
    @Test void supportsMysqlDatetimeAndH2TimestampWithoutChangingPaidInstant() throws Exception {
        java.lang.reflect.Method formatter=MockPaymentGateway.class.getDeclaredMethod("formatSuccessTime",Object.class);
        formatter.setAccessible(true);
        assertEquals("2026-11-14T19:43:00+08:00",formatter.invoke(null,LocalDateTime.of(2026,11,14,19,43)));
        assertEquals("2026-11-14T19:43:00+08:00",formatter.invoke(null,Timestamp.from(Instant.parse("2026-11-14T11:43:00Z"))));
    }
}
