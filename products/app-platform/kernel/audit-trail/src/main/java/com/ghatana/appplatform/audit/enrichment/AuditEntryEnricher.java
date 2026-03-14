package com.ghatana.appplatform.audit.enrichment;

import com.ghatana.appplatform.audit.domain.AuditEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Enriches an {@link AuditEntry} before persistence by filling missing fields.
 *
 * <p>Sprint 1: provides default Gregorian timestamp and empty BS timestamp.
 * Sprint 2: wires actual K-15 calendar conversion via injection of a
 * {@code CalendarConversionPort} that calls the calendar-service kernel.
 *
 * @doc.type class
 * @doc.purpose Fills missing AuditEntry fields (timestamp, BS date) before persistence
 * @doc.layer product
 * @doc.pattern Service
 */
public class AuditEntryEnricher {

    /**
     * Returns a copy of the entry with missing fields defaulted.
     *
     * <ul>
     *   <li>{@code timestampGregorian}: set to {@link Instant#now()} if null.
     *   <li>{@code timestampBs}: populated from K-15 if available, else empty string.
     *   <li>{@code traceId}: unchanged — caller supplies from HTTP request context.
     * </ul>
     */
    public AuditEntry enrich(AuditEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("AuditEntry must not be null");
        }

        Instant timestamp = entry.timestampGregorian() != null
                ? entry.timestampGregorian()
                : Instant.now();

        // K-15 calendar conversion — Sprint 1: degradation mode returns empty string.
        // Sprint 2: calls calendar-service to populate the BS date.
        LocalDate gregorianDate = timestamp.atOffset(ZoneOffset.UTC).toLocalDate();
        String timestampBs = resolveCalendarDate(gregorianDate);

        return AuditEntry.builder()
                .id(entry.id())
                .action(entry.action())
                .actor(entry.actor())
                .resource(entry.resource())
                .outcome(entry.outcome())
                .tenantId(entry.tenantId())
                .traceId(entry.traceId())
                .timestampGregorian(timestamp)
                .timestampBs(timestampBs)
                .details(entry.details())
                .build();
    }

    /**
     * Converts a Gregorian date to a BS calendar date string (YYYY-MM-DD).
     *
     * <p>Sprint 1 degradation mode: returns empty string.
     * Sprint 2: overridden or replaced with a K-15 service call.
     */
    protected String resolveCalendarDate(LocalDate gregorianDate) {
        return "";
    }
}
