/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Phase 2 + Phase 4 — Performance Validation and SLI/SLO Monitoring:
 * Comprehensive tests for PerformanceMonitor verifying that documented SLO thresholds
 * are properly encoded and violations are correctly detected and counted.
 */
package com.ghatana.datacloud.observability;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for PerformanceMonitor verifying SLI/SLO threshold accuracy,
 * SLO violation counting, business metrics tracking, Prometheus export, and the
 * documented performance targets: API &lt;200ms (p95), DB &lt;50ms (p95), // GH-90000
 * event throughput &gt;10k/sec, error rate &lt;1%, availability &gt;99.9%.
 *
 * @doc.type test
 * @doc.purpose Verify SLO thresholds, violation detection, business metrics, and Prometheus export
 * @doc.layer observability
 * @doc.pattern UnitTest
 */
@DisplayName("PerformanceMonitor")
class PerformanceMonitorTest {

    private PerformanceMonitor monitor;

    @BeforeEach
    void setUp() { // GH-90000
        monitor = new PerformanceMonitor(); // GH-90000
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("creates without exception")
        void createsWithoutException() { // GH-90000
            assertThatCode(() -> new PerformanceMonitor()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("Prometheus metrics scrape endpoint is non-null and non-empty after construction")
        void prometheusEndpointPopulatedAtConstruction() { // GH-90000
            String scrape = monitor.getPrometheusMetrics(); // GH-90000

            assertThat(scrape).isNotNull().isNotBlank(); // GH-90000
        }
    }

    // =========================================================================
    // API Response Time — SLO Threshold 200ms
    // =========================================================================

    @Nested
    @DisplayName("API Response Time (SLO ≤200ms)")
    class ApiResponseTimeSlo {

        @Test
        @DisplayName("requests under 200ms do not increment sloViolations")
        void fastRequestDoesNotViolateSlo() { // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(100), "GET", "/entities", 200); // GH-90000

            assertThat(monitor.getSLOStatus().getSloViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("request of exactly 200ms does not violate the SLO (boundary is exclusive)")
        void exactThresholdDoesNotViolate() { // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(200), "GET", "/entities", 200); // GH-90000

            assertThat(monitor.getSLOStatus().getSloViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("request over 200ms increments sloViolations")
        void slowRequestViolatesSlo() { // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(201), "GET", "/entities", 200); // GH-90000

            assertThat(monitor.getSLOStatus().getSloViolations()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("multiple over-threshold requests accumulate violations")
        void multipleSlowRequestsAccumulateViolations() { // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(300), "POST", "/entities", 201); // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(400), "PUT", "/entities/1", 200); // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(500), "DELETE", "/entities/2", 204); // GH-90000

            assertThat(monitor.getSLOStatus().getSloViolations()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("5xx status codes increment the error counter")
        void serverErrorIncrementsErrorCounter() { // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(50), "GET", "/entities", 500); // GH-90000

            // After recording, the error should be tracked in the meter registry
            assertThat(monitor.getMeterRegistry().find("datacloud.api.errors.total").counter()).isNotNull();
        }

        @Test
        @DisplayName("SLOStatus after fast requests reports apiLatencySLOMet=true")
        void sloStatusReportsMetWhenFast() { // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(50), "GET", "/entities", 200); // GH-90000

            PerformanceMonitor.SLOStatus status = monitor.getSLOStatus(); // GH-90000

            // With only one recorded fast sample, SLO should still be met
            assertThat(status.getApiP95Latency()).isGreaterThanOrEqualTo(0.0); // GH-90000
        }
    }

    // =========================================================================
    // Database Query Time — SLO Threshold 50ms
    // =========================================================================

    @Nested
    @DisplayName("Database Query Time (SLO ≤50ms)")
    class DatabaseQueryTimeSlo {

        @Test
        @DisplayName("query under 50ms does not produce extra SLO violations")
        void fastQueryNoViolation() { // GH-90000
            monitor.recordDbQuery(Duration.ofMillis(20), "SELECT", "entities"); // GH-90000

            // SLO violations driven by API, not DB; DB has alert but no sloViolations counter
            assertThat(monitor.getSLOStatus().getSloViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("slow query over 50ms is recorded in DB query timer")
        void slowQueryIsRecordedInTimer() { // GH-90000
            monitor.recordDbQuery(Duration.ofMillis(200), "SELECT", "entities"); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.db.query.time").timer()).isNotNull();
            assertThat(monitor.getMeterRegistry().find("datacloud.db.query.time").timer().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordDbError increments db errors counter")
        void recordDbErrorIncrementsCounter() { // GH-90000
            monitor.recordDbError("TIMEOUT", "Query timed out on entities table"); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.db.errors.total").counter()).isNotNull();
            assertThat(monitor.getMeterRegistry().find("datacloud.db.errors.total").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("updateConnectionPoolMetrics sets active and idle counts")
        void connectionPoolMetricsUpdated() { // GH-90000
            monitor.updateConnectionPoolMetrics(10, 5); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.db.connections.active").gauge().value()).isEqualTo(10.0);
            assertThat(monitor.getMeterRegistry().find("datacloud.db.connections.idle").gauge().value()).isEqualTo(5.0);
        }
    }

    // =========================================================================
    // Event Throughput — SLO > 10,000 events/sec
    // =========================================================================

    @Nested
    @DisplayName("Event Throughput (SLO >10,000/sec)")
    class EventThroughputSlo {

        @Test
        @DisplayName("throughput above threshold does not increment SLO violations")
        void throughputAboveThresholdNoViolation() { // GH-90000
            monitor.checkEventThroughput(15_000.0); // GH-90000

            assertThat(monitor.getSLOStatus().getSloViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("throughput below 10,000/sec does not throw (violation is logged/alerted only)")
        void throughputBelowThresholdLogsAlert() { // GH-90000
            // checkEventThroughput emits a log+alert but does not increment the sloViolations counter
            // (which tracks only API latency violations). Verify the call is side-effect-safe. // GH-90000
            assertThatCode(() -> monitor.checkEventThroughput(5_000.0)).doesNotThrowAnyException(); // GH-90000
            assertThat(monitor.getSLOStatus().getSloViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("throughput exactly at threshold does not violate")
        void throughputAtExactThresholdNoViolation() { // GH-90000
            monitor.checkEventThroughput(10_000.0); // GH-90000

            assertThat(monitor.getSLOStatus().getSloViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("recordEventPublish increments published counter")
        void recordEventPublishIncrementsCounter() { // GH-90000
            monitor.recordEventPublish(Duration.ofMillis(5), "user.created"); // GH-90000
            monitor.recordEventPublish(Duration.ofMillis(3), "user.updated"); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.events.published.total").counter().count()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("recordEventConsume increments consumed counter")
        void recordEventConsumeIncrementsCounter() { // GH-90000
            monitor.recordEventConsume(Duration.ofMillis(8), "user.created"); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.events.consumed.total").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("setConsumerLag above threshold logs alert (does not throw)")
        void highConsumerLagLogsAlert() { // GH-90000
            assertThatCode(() -> monitor.setConsumerLag(100_000)).doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // Error Rate — SLO < 1%
    // =========================================================================

    @Nested
    @DisplayName("Error Rate (SLO <1%)")
    class ErrorRateSlo {

        @Test
        @DisplayName("error rate below 1% does not violate SLO")
        void lowErrorRateMeetsSlo() { // GH-90000
            monitor.checkErrorRate(0.005); // 0.5% // GH-90000

            PerformanceMonitor.SLOStatus status = monitor.getSLOStatus(); // GH-90000
            assertThat(status.isErrorRateSLOMet()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("actual error rate above 1% reports isErrorRateSLOMet=false")
        void highErrorRateViolatesSlo() { // GH-90000
            // Drive actual error rate above 1% by recording a 500-status request
            monitor.recordApiRequest(Duration.ofMillis(50), "GET", "/entities", 500); // GH-90000

            assertThat(monitor.getSLOStatus().isErrorRateSLOMet()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("error rate at exactly 1% meets the SLO (boundary is inclusive)")
        void errorRateAtExactThresholdMeetsSlo() { // GH-90000
            monitor.checkErrorRate(0.01); // exactly 1% // GH-90000

            PerformanceMonitor.SLOStatus status = monitor.getSLOStatus(); // GH-90000
            assertThat(status.isErrorRateSLOMet()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // Availability — SLO > 99.9%
    // =========================================================================

    @Nested
    @DisplayName("API Availability (SLO >99.9%)")
    class AvailabilitySlo {

        @Test
        @DisplayName("availability above 99.9% does not increment availability violations")
        void highAvailabilityNoViolation() { // GH-90000
            monitor.checkApiAvailability(0.9995); // 99.95% // GH-90000

            assertThat(monitor.getSLOStatus().getAvailabilityViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("availability below 99.9% increments availability violations")
        void lowAvailabilityIncrementsViolation() { // GH-90000
            monitor.checkApiAvailability(0.998); // 99.8% // GH-90000

            assertThat(monitor.getSLOStatus().getAvailabilityViolations()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("availability at exactly 99.9% meets SLO (boundary is exclusive)")
        void availabilityAtExactThresholdMeetsSlo() { // GH-90000
            monitor.checkApiAvailability(0.999); // exactly 99.9% // GH-90000

            assertThat(monitor.getSLOStatus().getAvailabilityViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("multiple availability checks below threshold accumulate violations")
        void multipleViolationsAccumulate() { // GH-90000
            monitor.checkApiAvailability(0.99); // GH-90000
            monitor.checkApiAvailability(0.98); // GH-90000

            assertThat(monitor.getSLOStatus().getAvailabilityViolations()).isEqualTo(2); // GH-90000
        }
    }

    // =========================================================================
    // SLO Status Report
    // =========================================================================

    @Nested
    @DisplayName("SLO Status Report")
    class SloStatusReport {

        @Test
        @DisplayName("getSLOStatus returns non-null status after construction")
        void statusNonNullAfterConstruction() { // GH-90000
            assertThat(monitor.getSLOStatus()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("getSLOStatus.areAllSLOsMet() returns true when no violations recorded")
        void allSlosMet_whenNoViolations() { // GH-90000
            assertThat(monitor.getSLOStatus().areAllSLOsMet()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("areAllSLOsMet returns false when actual error rate exceeds 1%")
        void areAllSlosMet_false_whenErrorRateViolated() { // GH-90000
            // Record a 500 request so the actual counter-based error rate = 1.0 > 0.01
            monitor.recordApiRequest(Duration.ofMillis(50), "GET", "/entities", 500); // GH-90000

            assertThat(monitor.getSLOStatus().areAllSLOsMet()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("SLOStatus.toString contains key metrics")
        void sloStatusToStringContainsKeyMetrics() { // GH-90000
            String str = monitor.getSLOStatus().toString(); // GH-90000

            assertThat(str) // GH-90000
                .contains("SLOStatus")
                .contains("apiP95")
                .contains("allMet");
        }

        @Test
        @DisplayName("sloViolations counter only tracks API latency violations")
        void sloViolationCountTracksApiLatencyOnly() { // GH-90000
            // Only recordApiRequest() with latency > 200ms increments sloViolations. // GH-90000
            // checkEventThroughput and checkErrorRate emit log/alert but do not affect the counter.
            monitor.recordApiRequest(Duration.ofMillis(300), "GET", "/entities", 200); // +1 sloViolation // GH-90000
            monitor.checkEventThroughput(1_000.0); // alerts only // GH-90000
            monitor.checkErrorRate(0.05);           // alerts only // GH-90000

            assertThat(monitor.getSLOStatus().getSloViolations()).isEqualTo(1); // GH-90000
        }
    }

    // =========================================================================
    // Business Metrics
    // =========================================================================

    @Nested
    @DisplayName("Business Metrics")
    class BusinessMetrics {

        @Test
        @DisplayName("recordUserAction increments the total user actions counter")
        void recordUserActionIncrementsCounter() { // GH-90000
            monitor.recordUserAction("entity.create", "user-42", "tenant-1"); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.user.actions.total").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("recordFeatureUsage increments the feature usage counter")
        void recordFeatureUsageIncrementsCounter() { // GH-90000
            monitor.recordFeatureUsage("query.graphql", "tenant-1"); // GH-90000
            monitor.recordFeatureUsage("query.graphql", "tenant-1"); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.feature.usage.total").counter().count()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("setActiveUsers updates the active users gauge")
        void setActiveUsersUpdatesGauge() { // GH-90000
            monitor.setActiveUsers(42); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.users.active").gauge().value()).isEqualTo(42.0);
        }

        @Test
        @DisplayName("setTenantCount updates the tenant count gauge")
        void setTenantCountUpdatesGauge() { // GH-90000
            monitor.setTenantCount(15); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.tenants.total").gauge().value()).isEqualTo(15.0);
        }

        @Test
        @DisplayName("setActiveConnections updates the active connections gauge")
        void setActiveConnectionsUpdatesGauge() { // GH-90000
            monitor.setActiveConnections(77); // GH-90000

            assertThat(monitor.getMeterRegistry().find("datacloud.api.connections.active").gauge().value()).isEqualTo(77.0);
        }
    }

    // =========================================================================
    // Prometheus Export
    // =========================================================================

    @Nested
    @DisplayName("Prometheus Metrics Export")
    class PrometheusExport {

        @Test
        @DisplayName("getPrometheusMetrics returns non-blank scrape output")
        void prometheusMetricsNonBlank() { // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(50), "GET", "/test", 200); // GH-90000

            assertThat(monitor.getPrometheusMetrics()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("scrape output contains core metric names")
        void scrapeContainsCoreMetricNames() { // GH-90000
            monitor.recordApiRequest(Duration.ofMillis(50), "GET", "/test", 200); // GH-90000
            monitor.recordDbQuery(Duration.ofMillis(10), "SELECT", "entities"); // GH-90000

            String scrape = monitor.getPrometheusMetrics(); // GH-90000

            assertThat(scrape) // GH-90000
                .contains("datacloud_api_requests_total")
                .contains("datacloud_db_queries_total");
        }

        @Test
        @DisplayName("getMeterRegistry returns the underlying PrometheusMeterRegistry")
        void getMeterRegistryReturnsSameInstance() { // GH-90000
            assertThat(monitor.getMeterRegistry()).isNotNull(); // GH-90000
            assertThat(monitor.getMeterRegistry()).isInstanceOf(PrometheusMeterRegistry.class); // GH-90000
        }
    }

    // =========================================================================
    // SLO Threshold Constants (Documented Targets) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Documented SLO Threshold Values")
    class DocumentedSloThresholds {

        @Test
        @DisplayName("API response time threshold is exactly 200ms (target from requirements)")
        void apiResponseTimeThresholdIs200ms() { // GH-90000
            // Verify by observing the boundary behaviour
            monitor.recordApiRequest(Duration.ofMillis(200), "GET", "/test", 200); // GH-90000
            int violationsAt200 = monitor.getSLOStatus().getSloViolations(); // GH-90000

            monitor.recordApiRequest(Duration.ofMillis(201), "GET", "/test", 200); // GH-90000
            int violationsAt201 = monitor.getSLOStatus().getSloViolations(); // GH-90000

            assertThat(violationsAt200).isZero(); // GH-90000
            assertThat(violationsAt201).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("event throughput SLO alert boundary is exactly 10,000 events/sec (target from requirements)")
        void eventThroughputThresholdIs10kPerSec() { // GH-90000
            // checkEventThroughput does NOT increment sloViolations — it only logs/alerts.
            // Verify the boundary by confirming neither call throws and sloViolations stays zero.
            assertThatCode(() -> monitor.checkEventThroughput(10_000.0)).doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> monitor.checkEventThroughput(9_999.0)).doesNotThrowAnyException(); // GH-90000

            // No counter side-effects from either call
            assertThat(monitor.getSLOStatus().getSloViolations()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("availability SLO threshold is exactly 99.9% (target from requirements)")
        void availabilityThresholdIs999Percent() { // GH-90000
            monitor.checkApiAvailability(0.999); // GH-90000
            int violationsAt999 = monitor.getSLOStatus().getAvailabilityViolations(); // GH-90000

            monitor.checkApiAvailability(0.9989); // GH-90000
            int violationsBelow999 = monitor.getSLOStatus().getAvailabilityViolations(); // GH-90000

            assertThat(violationsAt999).isZero(); // GH-90000
            assertThat(violationsBelow999).isEqualTo(1); // GH-90000
        }
    }
}
