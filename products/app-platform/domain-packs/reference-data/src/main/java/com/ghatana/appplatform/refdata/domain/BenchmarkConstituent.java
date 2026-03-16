package com.ghatana.appplatform.refdata.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @doc.type       Domain Record
 * @doc.purpose    Constituent entry of a benchmark index: which instrument,
 *                 what weight, and for what period.
 *                 Weights across all constituents of a benchmark must sum to 1.0.
 * @doc.layer      Domain
 * @doc.pattern    Immutable Value Object / SCD Type-2
 */
public record BenchmarkConstituent(
        UUID benchmarkId,
        UUID instrumentId,
        BigDecimal weight,          // 0 < weight ≤ 1.0; all constituents sum to 1.0
        LocalDate effectiveFrom,
        LocalDate effectiveTo       // null = current
) {}
