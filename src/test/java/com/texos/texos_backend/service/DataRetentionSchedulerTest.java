package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DataRetentionSchedulerTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Test
    void calculatesCutoffAtIndianFinancialYearBoundary() {
        Instant january2027 = LocalDate.of(2027, 1, 15)
                .atStartOfDay(INDIA)
                .toInstant();
        Instant march312027 = LocalDate.of(2027, 3, 31)
                .atTime(23, 59, 59)
                .atZone(INDIA)
                .toInstant();
        Instant april12027 = LocalDate.of(2027, 4, 1)
                .atStartOfDay(INDIA)
                .toInstant();

        assertEquals(Instant.parse("2018-03-31T18:30:00Z"),
                DataRetentionScheduler.indianFinancialYearCutoff(january2027));
        assertEquals(Instant.parse("2018-03-31T18:30:00Z"),
                DataRetentionScheduler.indianFinancialYearCutoff(march312027));
        assertEquals(Instant.parse("2019-03-31T18:30:00Z"),
                DataRetentionScheduler.indianFinancialYearCutoff(april12027));
    }
}
