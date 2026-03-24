package com.ghatana.products.finance.domains.oms.port;

import java.time.Instant;

/**
 * Port for dual-calendar date conversion (Gregorian ↔ Bikram Sambat).
 *
 * <p>Abstracts the kernel extension K-15 (DualCalendarKernelExtension)
 * so the OMS domain is decoupled from kernel internals.</p>
 *
 * @doc.type interface
 * @doc.purpose Dual-calendar port — converts Gregorian timestamps to BS date strings
 * @doc.layer domain-pack
 * @doc.pattern Port (Hexagonal Architecture)
 * @since 1.0.0
 */
public interface DualCalendarPort {

    /**
     * Converts a Gregorian instant to a Bikram Sambat date string (e.g., "2081-01-15").
     *
     * @param instant the Gregorian instant to convert
     * @return the BS date string, or empty string if conversion is unavailable
     */
    String toBsDateString(Instant instant);
}
