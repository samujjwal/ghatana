package com.ghatana.appplatform.eventstore.port;

import java.time.Instant;

/**
 * Port for converting UTC timestamps to Bikram Sambat (BS) date strings.
 *
 * <p>Implementations are provided by the {@code calendar-service} kernel module.
 * A no-op implementation is used when the calendar service is unavailable (degradation mode).
 * The event-store records {@code null} for {@code created_at_bs} in degradation mode, which
 * is acceptable — the BS date can be backfilled via a replay when the calendar service becomes
 * available.
 *
 * <p>Registered as a Hexagonal port: the event-store depends on this interface;
 * the calendar-service kernel provides the concrete adapter.
 *
 * @doc.type interface
 * @doc.purpose Port for BS calendar date enrichment in the event-store (K05 + K15 integration)
 * @doc.layer domain
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface CalendarDateEnricher {

    /**
     * Converts a UTC {@link Instant} to the corresponding Bikram Sambat date string.
     *
     * @param utc the UTC timestamp to convert
     * @return BS date in {@code "YYYY-MM-DD"} format, or {@code null} if conversion fails
     *         or the calendar service is unavailable
     */
    String toBs(Instant utc);

    /**
     * No-op implementation for use when the calendar service is not wired (degradation mode).
     * Records {@code null} for {@code created_at_bs} in all events.
     */
    CalendarDateEnricher DEGRADED = utc -> null;
}
