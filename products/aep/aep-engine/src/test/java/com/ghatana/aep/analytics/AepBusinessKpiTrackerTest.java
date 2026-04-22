package com.ghatana.aep.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link AepBusinessKpiTracker} — AEP-011.1.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the AEP business KPI tracker analytics component
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepBusinessKpiTracker [GH-90000]")
class AepBusinessKpiTrackerTest {

    private static final String TENANT = "tenant-alpha";
    private static final String KPI_PROCESSED = "events.processed";
    private static final String KPI_ERRORS = "events.error";
    private static final String KPI_LAG = "pipeline.lag.ms";

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: starts with no tracked tenants [GH-90000]")
    void create_empty() { // GH-90000
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
        assertThat(tracker.trackedTenants()).isEmpty(); // GH-90000
    }

    // ─── increment (single) ─────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("increment() - single step [GH-90000]")
    class IncrementSingle {

        @Test
        @DisplayName("increments counter by 1 [GH-90000]")
        void increment_byOne() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            tracker.increment(TENANT, KPI_PROCESSED); // GH-90000
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(1L); // GH-90000
        }

        @Test
        @DisplayName("multiple increments accumulate [GH-90000]")
        void increment_multiple_accumulates() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            tracker.increment(TENANT, KPI_PROCESSED); // GH-90000
            tracker.increment(TENANT, KPI_PROCESSED); // GH-90000
            tracker.increment(TENANT, KPI_PROCESSED); // GH-90000
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(3L); // GH-90000
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException [GH-90000]")
        void increment_nullTenant_throwsNPE() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> tracker.increment(null, KPI_PROCESSED)); // GH-90000
        }

        @Test
        @DisplayName("null kpiName throws NullPointerException [GH-90000]")
        void increment_nullKpi_throwsNPE() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> tracker.increment(TENANT, null)); // GH-90000
        }
    }

    // ─── increment (delta) ──────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("increment(delta) [GH-90000]")
    class IncrementDelta {

        @Test
        @DisplayName("increments counter by delta [GH-90000]")
        void incrementDelta_correctValue() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            tracker.increment(TENANT, KPI_PROCESSED, 10L); // GH-90000
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(10L); // GH-90000
        }

        @Test
        @DisplayName("negative delta throws IllegalArgumentException [GH-90000]")
        void incrementDelta_negative_throwsIAE() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> tracker.increment(TENANT, KPI_PROCESSED, -1L)); // GH-90000
        }

        @Test
        @DisplayName("zero delta is accepted and does not change value [GH-90000]")
        void incrementDelta_zero_accepted() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            tracker.increment(TENANT, KPI_PROCESSED, 5L); // GH-90000
            tracker.increment(TENANT, KPI_PROCESSED, 0L); // GH-90000
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(5L); // GH-90000
        }
    }

    // ─── counter ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("counter() [GH-90000]")
    class Counter {

        @Test
        @DisplayName("returns 0 for unknown tenant/kpi pair [GH-90000]")
        void counter_unknown_returnsZero() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            assertThat(tracker.counter("unknown-tenant", "nonexistent.kpi")).isZero(); // GH-90000
        }

        @Test
        @DisplayName("different KPIs for same tenant are independent [GH-90000]")
        void counter_differentKpis_independent() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            tracker.increment(TENANT, KPI_PROCESSED, 5L); // GH-90000
            tracker.increment(TENANT, KPI_ERRORS, 2L); // GH-90000
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(5L); // GH-90000
            assertThat(tracker.counter(TENANT, KPI_ERRORS)).isEqualTo(2L); // GH-90000
        }

        @Test
        @DisplayName("different tenants share the same KPI name independently [GH-90000]")
        void counter_tenantIsolation() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            tracker.increment("tenant-a", KPI_PROCESSED, 10L); // GH-90000
            tracker.increment("tenant-b", KPI_PROCESSED, 3L); // GH-90000
            assertThat(tracker.counter("tenant-a", KPI_PROCESSED)).isEqualTo(10L); // GH-90000
            assertThat(tracker.counter("tenant-b", KPI_PROCESSED)).isEqualTo(3L); // GH-90000
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException [GH-90000]")
        void counter_nullTenant_throwsNPE() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> tracker.counter(null, KPI_PROCESSED)); // GH-90000
        }
    }

    // ─── gauge ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("gauge() [GH-90000]")
    class Gauge {

        @Test
        @DisplayName("records and retrieves a gauge value [GH-90000]")
        void gauge_recordAndRetrieve() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            tracker.gauge(TENANT, KPI_LAG, 123.45); // GH-90000
            assertThat(tracker.gaugeValue(TENANT, KPI_LAG)) // GH-90000
                    .isPresent() // GH-90000
                    .hasValueSatisfying(s -> assertThat(s.value()).isEqualTo(123.45)); // GH-90000
        }

        @Test
        @DisplayName("latest gauge value overwrites earlier one [GH-90000]")
        void gauge_latestOverwrites() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            tracker.gauge(TENANT, KPI_LAG, 100.0); // GH-90000
            tracker.gauge(TENANT, KPI_LAG, 50.0); // GH-90000
            assertThat(tracker.gaugeValue(TENANT, KPI_LAG)) // GH-90000
                    .isPresent() // GH-90000
                    .hasValueSatisfying(s -> assertThat(s.value()).isEqualTo(50.0)); // GH-90000
        }

        @Test
        @DisplayName("gaugeValue returns empty for unknown tenant/kpi [GH-90000]")
        void gaugeValue_unknown_returnsEmpty() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            assertThat(tracker.gaugeValue("unknown", KPI_LAG)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("gauge timestamp is set to approximately now [GH-90000]")
        void gauge_timestampIsNow() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            long before = System.currentTimeMillis(); // GH-90000
            tracker.gauge(TENANT, KPI_LAG, 10.0); // GH-90000
            long after = System.currentTimeMillis(); // GH-90000

            tracker.gaugeValue(TENANT, KPI_LAG).ifPresent(s -> { // GH-90000
                long epochMs = s.timestamp().toEpochMilli(); // GH-90000
                assertThat(epochMs).isGreaterThanOrEqualTo(before).isLessThanOrEqualTo(after); // GH-90000
            });
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException [GH-90000]")
        void gauge_nullTenant_throwsNPE() { // GH-90000
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> tracker.gauge(null, KPI_LAG, 1.0)); // GH-90000
        }
    }

    // ─── trackedTenants ───────────────────────────────────────────────────────

    @Test
    @DisplayName("trackedTenants: includes tenants with only counters and only gauges [GH-90000]")
    void trackedTenants_includesAllTenants() { // GH-90000
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
        tracker.increment("counter-only", KPI_PROCESSED); // GH-90000
        tracker.gauge("gauge-only", KPI_LAG, 5.0); // GH-90000
        assertThat(tracker.trackedTenants()).containsExactlyInAnyOrder("counter-only", "gauge-only"); // GH-90000
    }

    // ─── resetTenant ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetTenant: removes all data for that tenant [GH-90000]")
    void resetTenant_removesAll() { // GH-90000
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
        tracker.increment(TENANT, KPI_PROCESSED, 5L); // GH-90000
        tracker.gauge(TENANT, KPI_LAG, 20.0); // GH-90000
        tracker.resetTenant(TENANT); // GH-90000

        assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isZero(); // GH-90000
        assertThat(tracker.gaugeValue(TENANT, KPI_LAG)).isEmpty(); // GH-90000
        assertThat(tracker.trackedTenants()).doesNotContain(TENANT); // GH-90000
    }

    @Test
    @DisplayName("resetTenant: null tenantId throws NullPointerException [GH-90000]")
    void resetTenant_nullTenant_throwsNPE() { // GH-90000
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> tracker.resetTenant(null)); // GH-90000
    }

    // ─── printReport ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("printReport: completes without exception [GH-90000]")
    void printReport_noException() { // GH-90000
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); // GH-90000
        tracker.increment(TENANT, KPI_PROCESSED, 1_000L); // GH-90000
        tracker.increment(TENANT, KPI_ERRORS, 2L); // GH-90000
        tracker.gauge(TENANT, KPI_LAG, 55.5); // GH-90000
        tracker.printReport(TENANT); // GH-90000
    }
}
