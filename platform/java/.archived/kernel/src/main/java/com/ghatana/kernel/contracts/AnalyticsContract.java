/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contract for analytics surfaces: metrics, telemetry pipelines, dashboards, KPI definitions.
 *
 * <p>An analytics contract declares the metric families a module emits, the
 * dashboard definitions it provides, and the telemetry pipeline integrations
 * it requires. Aligns with the kernel's {@code observability} library patterns.</p>
 *
 * @doc.type class
 * @doc.purpose Analytics contract for metrics and telemetry declarations
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class AnalyticsContract extends KernelContract {

    /**
     * Metric types aligned with Micrometer / OpenTelemetry conventions.
     */
    public enum MetricType {
        COUNTER,
        GAUGE,
        HISTOGRAM,
        TIMER,
        DISTRIBUTION_SUMMARY
    }

    /**
     * Declares a metric this module emits.
     */
    public record MetricDeclaration(String metricName, MetricType type,
                                    String description, List<String> tags) {
        public MetricDeclaration {
            Objects.requireNonNull(metricName, "metricName required");
            Objects.requireNonNull(type, "type required");
            if (tags == null) tags = List.of();
        }
    }

    /**
     * Declares a dashboard provided by this module.
     */
    public record DashboardDeclaration(String dashboardId, String title,
                                       List<String> requiredMetrics) {
        public DashboardDeclaration {
            Objects.requireNonNull(dashboardId, "dashboardId required");
            Objects.requireNonNull(title, "title required");
            if (requiredMetrics == null) requiredMetrics = List.of();
        }
    }

    private final List<MetricDeclaration> metrics;
    private final List<DashboardDeclaration> dashboards;

    private AnalyticsContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              KernelContract.ContractFamily.ANALYTICS, builder.metadata);
        this.metrics = builder.metrics != null ? List.copyOf(builder.metrics) : List.of();
        this.dashboards = builder.dashboards != null ? List.copyOf(builder.dashboards) : List.of();
        validate();
    }

    public List<MetricDeclaration> getMetrics() { return metrics; }
    public List<DashboardDeclaration> getDashboards() { return dashboards; }

    @Override
    protected void validate() {
        super.validate();
        for (MetricDeclaration metric : metrics) {
            if (!metric.metricName().matches("^[a-z][a-z0-9._]*$")) {
                throw new IllegalArgumentException(
                    "Metric name must match ^[a-z][a-z0-9._]*$: " + metric.metricName());
            }
        }
    }

    /**
     * Creates a new builder for {@link AnalyticsContract}.
     */
    public static Builder builder(String contractId, String name, String version) {
        return new Builder(contractId, name, version);
    }

    /**
     * Fluent builder for {@link AnalyticsContract}.
     */
    public static final class Builder {
        private final String contractId;
        private final String name;
        private final String version;
        private Map<String, String> metadata = Map.of();
        private List<MetricDeclaration> metrics = List.of();
        private List<DashboardDeclaration> dashboards = List.of();

        private Builder(String contractId, String name, String version) {
            this.contractId = contractId;
            this.name = name;
            this.version = version;
        }

        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder metrics(List<MetricDeclaration> metrics) { this.metrics = metrics; return this; }
        public Builder dashboards(List<DashboardDeclaration> dashboards) { this.dashboards = dashboards; return this; }

        public AnalyticsContract build() { return new AnalyticsContract(this); }
    }
}
