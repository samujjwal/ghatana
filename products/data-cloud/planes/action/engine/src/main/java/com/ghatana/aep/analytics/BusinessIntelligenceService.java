package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Business-intelligence summary service contract.
 *
 * @doc.type interface
 * @doc.purpose Generate tenant-scoped business intelligence summaries
 * @doc.layer product
 * @doc.pattern Service
 */
public interface BusinessIntelligenceService {

    /**
     * Immutable BI summary.
     */
    record BISummary(String tenantId, Instant generatedAt, Map<String, Object> metrics) {
        public BISummary {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(generatedAt, "generatedAt");
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        }
    }
}