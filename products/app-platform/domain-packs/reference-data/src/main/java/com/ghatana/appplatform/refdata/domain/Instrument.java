package com.ghatana.appplatform.refdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type       Domain Record
 * @doc.purpose    Canonical instrument master record.  Each row represents a
 *                 version of an instrument valid between effectiveFrom and
 *                 effectiveTo (SCD Type-2).  A null effectiveTo means the row
 *                 is the current version.
 * @doc.layer      Domain
 * @doc.pattern    Immutable Value Object / SCD Type-2
 */
public record Instrument(
        UUID id,
        String symbol,
        String exchange,
        String isin,
        String name,
        InstrumentType type,
        InstrumentStatus status,
        String sector,
        int lotSize,
        BigDecimal tickSize,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,      // null = current version
        Instant createdAtUtc,
        String createdAtBs,         // Bikram Sambat date string e.g. "2081-06-15"
        Map<String, Object> metadata
) {
    /** Returns true when this row is the current (not yet superseded) version. */
    public boolean isCurrent() {
        return effectiveTo == null;
    }
}
