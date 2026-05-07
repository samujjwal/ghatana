package com.ghatana.datacloud.launcher.audit;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Query-side extension for audit services that can summarize persisted audit activity.
 *
 * @doc.type interface
 * @doc.purpose Expose persisted audit summaries for governance compliance endpoints
 * @doc.layer product
 * @doc.pattern QueryService
 */
public interface AuditSummaryProvider {

    Promise<AuditSummary> summarize(String tenantId, Instant startInclusive, int limit);

    record AuditSummary(
        Instant lastAuditAt,
        Map<String, Long> eventCounts,
        List<Map<String, Object>> recentEvents
    ) {
        public AuditSummary {
            lastAuditAt = lastAuditAt != null ? lastAuditAt : Instant.EPOCH;
            eventCounts = eventCounts != null ? Map.copyOf(eventCounts) : Map.of();
            recentEvents = recentEvents != null ? List.copyOf(recentEvents) : List.of();
        }

        public static AuditSummary empty() {
            return new AuditSummary(Instant.EPOCH, Map.of(), List.of());
        }
    }
}