package com.ghatana.eventlog;

import java.time.Duration;

/**
 * Data retention policy for EventLog adapters.
 * maxAge: delete events older than this duration when purge hooks are invoked.
 * maxBytes: best-effort cap for on-disk size at adapter discretion (0 = disabled).
 */
public record RetentionPolicy(Duration maxAge, long maxBytes) {
    public RetentionPolicy {
        if (maxAge == null || maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        if (maxBytes < 0) {
            maxBytes = 0L;
        }
    }
}
