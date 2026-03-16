package com.ghatana.appplatform.ems.domain;

import java.time.Instant;

/**
 * @doc.type      Record
 * @doc.purpose   Describes a registered exchange venue with its capabilities and health status.
 * @doc.layer     Domain
 * @doc.pattern   Immutable Value Object
 */
public record ExchangeVenue(
        String exchangeId,
        String name,
        boolean active,
        int priority,
        Instant lastHeartbeatAt
) {
    public boolean isHealthy(Instant now) {
        return active && lastHeartbeatAt != null
                && lastHeartbeatAt.plusSeconds(30).isAfter(now);
    }
}
