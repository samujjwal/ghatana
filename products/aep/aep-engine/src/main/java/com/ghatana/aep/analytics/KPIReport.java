package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * KPI report emitted by the in-process analytics aggregators.
 *
 * @doc.type record
 * @doc.purpose Structured KPI calculation result for AEP analytics queries
 * @doc.layer product
 * @doc.pattern DTO
 */
public record KPIReport(String tenantId, Instant generatedAt, boolean success, List<KPIEntry> kpis) {

    public KPIReport {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        kpis = kpis == null ? List.of() : List.copyOf(kpis);
    }

    /**
     * Individual KPI value.
     */
    public record KPIEntry(String name, double value, String unit) {
        public KPIEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(unit, "unit");
        }
    }
}