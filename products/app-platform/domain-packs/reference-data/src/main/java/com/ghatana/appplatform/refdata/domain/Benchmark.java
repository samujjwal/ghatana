package com.ghatana.appplatform.refdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @doc.type       Domain Record
 * @doc.purpose    Benchmark or market-index definition: specifies what is
 *                 being measured (PRICE_INDEX, TOTAL_RETURN, SECTOR) along with
 *                 its base-date reference values.
 * @doc.layer      Domain
 * @doc.pattern    Immutable Value Object
 */
public record Benchmark(
        UUID id,
        String name,
        BenchmarkType type,
        java.time.LocalDate baseDate,
        BigDecimal baseValue,
        String currency,
        String calculationMethod,
        String status               // ACTIVE / INACTIVE
) {}
