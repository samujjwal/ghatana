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
@DisplayName("AepBusinessKpiTracker")
class AepBusinessKpiTrackerTest {

    private static final String TENANT = "tenant-alpha";
    private static final String KPI_PROCESSED = "events.processed";
    private static final String KPI_ERRORS = "events.error";
    private static final String KPI_LAG = "pipeline.lag.ms";

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: starts with no tracked tenants")
    void create_empty() { 
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
        assertThat(tracker.trackedTenants()).isEmpty(); 
    }

    // ─── increment (single) ─────────────────────────────────────────────────── 

    @Nested
    @DisplayName("increment() - single step")
    class IncrementSingle {

        @Test
        @DisplayName("increments counter by 1")
        void increment_byOne() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            tracker.increment(TENANT, KPI_PROCESSED); 
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(1L); 
        }

        @Test
        @DisplayName("multiple increments accumulate")
        void increment_multiple_accumulates() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            tracker.increment(TENANT, KPI_PROCESSED); 
            tracker.increment(TENANT, KPI_PROCESSED); 
            tracker.increment(TENANT, KPI_PROCESSED); 
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(3L); 
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void increment_nullTenant_throwsNPE() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            assertThatNullPointerException() 
                    .isThrownBy(() -> tracker.increment(null, KPI_PROCESSED)); 
        }

        @Test
        @DisplayName("null kpiName throws NullPointerException")
        void increment_nullKpi_throwsNPE() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            assertThatNullPointerException() 
                    .isThrownBy(() -> tracker.increment(TENANT, null)); 
        }
    }

    // ─── increment (delta) ──────────────────────────────────────────────────── 

    @Nested
    @DisplayName("increment(delta)")
    class IncrementDelta {

        @Test
        @DisplayName("increments counter by delta")
        void incrementDelta_correctValue() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            tracker.increment(TENANT, KPI_PROCESSED, 10L); 
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(10L); 
        }

        @Test
        @DisplayName("negative delta throws IllegalArgumentException")
        void incrementDelta_negative_throwsIAE() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            assertThatIllegalArgumentException() 
                    .isThrownBy(() -> tracker.increment(TENANT, KPI_PROCESSED, -1L)); 
        }

        @Test
        @DisplayName("zero delta is accepted and does not change value")
        void incrementDelta_zero_accepted() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            tracker.increment(TENANT, KPI_PROCESSED, 5L); 
            tracker.increment(TENANT, KPI_PROCESSED, 0L); 
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(5L); 
        }
    }

    // ─── counter ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("counter()")
    class Counter {

        @Test
        @DisplayName("returns 0 for unknown tenant/kpi pair")
        void counter_unknown_returnsZero() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            assertThat(tracker.counter("unknown-tenant", "nonexistent.kpi")).isZero(); 
        }

        @Test
        @DisplayName("different KPIs for same tenant are independent")
        void counter_differentKpis_independent() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            tracker.increment(TENANT, KPI_PROCESSED, 5L); 
            tracker.increment(TENANT, KPI_ERRORS, 2L); 
            assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isEqualTo(5L); 
            assertThat(tracker.counter(TENANT, KPI_ERRORS)).isEqualTo(2L); 
        }

        @Test
        @DisplayName("different tenants share the same KPI name independently")
        void counter_tenantIsolation() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            tracker.increment("tenant-a", KPI_PROCESSED, 10L); 
            tracker.increment("tenant-b", KPI_PROCESSED, 3L); 
            assertThat(tracker.counter("tenant-a", KPI_PROCESSED)).isEqualTo(10L); 
            assertThat(tracker.counter("tenant-b", KPI_PROCESSED)).isEqualTo(3L); 
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void counter_nullTenant_throwsNPE() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            assertThatNullPointerException() 
                    .isThrownBy(() -> tracker.counter(null, KPI_PROCESSED)); 
        }
    }

    // ─── gauge ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("gauge()")
    class Gauge {

        @Test
        @DisplayName("records and retrieves a gauge value")
        void gauge_recordAndRetrieve() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            tracker.gauge(TENANT, KPI_LAG, 123.45); 
            assertThat(tracker.gaugeValue(TENANT, KPI_LAG)) 
                    .isPresent() 
                    .hasValueSatisfying(s -> assertThat(s.value()).isEqualTo(123.45)); 
        }

        @Test
        @DisplayName("latest gauge value overwrites earlier one")
        void gauge_latestOverwrites() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            tracker.gauge(TENANT, KPI_LAG, 100.0); 
            tracker.gauge(TENANT, KPI_LAG, 50.0); 
            assertThat(tracker.gaugeValue(TENANT, KPI_LAG)) 
                    .isPresent() 
                    .hasValueSatisfying(s -> assertThat(s.value()).isEqualTo(50.0)); 
        }

        @Test
        @DisplayName("gaugeValue returns empty for unknown tenant/kpi")
        void gaugeValue_unknown_returnsEmpty() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            assertThat(tracker.gaugeValue("unknown", KPI_LAG)).isEmpty(); 
        }

        @Test
        @DisplayName("gauge timestamp is set to approximately now")
        void gauge_timestampIsNow() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            long before = System.currentTimeMillis(); 
            tracker.gauge(TENANT, KPI_LAG, 10.0); 
            long after = System.currentTimeMillis(); 

            tracker.gaugeValue(TENANT, KPI_LAG).ifPresent(s -> { 
                long epochMs = s.timestamp().toEpochMilli(); 
                assertThat(epochMs).isGreaterThanOrEqualTo(before).isLessThanOrEqualTo(after); 
            });
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void gauge_nullTenant_throwsNPE() { 
            AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
            assertThatNullPointerException() 
                    .isThrownBy(() -> tracker.gauge(null, KPI_LAG, 1.0)); 
        }
    }

    // ─── trackedTenants ───────────────────────────────────────────────────────

    @Test
    @DisplayName("trackedTenants: includes tenants with only counters and only gauges")
    void trackedTenants_includesAllTenants() { 
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
        tracker.increment("counter-only", KPI_PROCESSED); 
        tracker.gauge("gauge-only", KPI_LAG, 5.0); 
        assertThat(tracker.trackedTenants()).containsExactlyInAnyOrder("counter-only", "gauge-only"); 
    }

    // ─── resetTenant ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetTenant: removes all data for that tenant")
    void resetTenant_removesAll() { 
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
        tracker.increment(TENANT, KPI_PROCESSED, 5L); 
        tracker.gauge(TENANT, KPI_LAG, 20.0); 
        tracker.resetTenant(TENANT); 

        assertThat(tracker.counter(TENANT, KPI_PROCESSED)).isZero(); 
        assertThat(tracker.gaugeValue(TENANT, KPI_LAG)).isEmpty(); 
        assertThat(tracker.trackedTenants()).doesNotContain(TENANT); 
    }

    @Test
    @DisplayName("resetTenant: null tenantId throws NullPointerException")
    void resetTenant_nullTenant_throwsNPE() { 
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
        assertThatNullPointerException() 
                .isThrownBy(() -> tracker.resetTenant(null)); 
    }

    // ─── printReport ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("printReport: completes without exception")
    void printReport_noException() { 
        AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create(); 
        tracker.increment(TENANT, KPI_PROCESSED, 1_000L); 
        tracker.increment(TENANT, KPI_ERRORS, 2L); 
        tracker.gauge(TENANT, KPI_LAG, 55.5); 
        tracker.printReport(TENANT); 
    }
}
