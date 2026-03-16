package com.ghatana.appplatform.refdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @doc.type       Domain Record
 * @doc.purpose    OHLCV value of a benchmark index for a single trading day.
 *                 Stored with both UTC and Bikram Sambat calendar dates to
 *                 support dual-calendar reporting via K-15.
 * @doc.layer      Domain
 * @doc.pattern    Immutable Value Object
 */
public record BenchmarkValue(
        UUID benchmarkId,
        LocalDate dateUtc,
        String dateBs,              // Bikram Sambat e.g. "2081-06-15"
        BigDecimal openValue,
        BigDecimal highValue,
        BigDecimal lowValue,
        BigDecimal closeValue,
        long volume,
        BigDecimal dailyReturn      // (closeValue / prevClose) - 1, populated post-calculation
) {}
