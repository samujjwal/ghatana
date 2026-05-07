package com.ghatana.digitalmarketing.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MicrometerDmosMetricsCollector}.
 *
 * <p>Uses {@link SimpleMeterRegistry} (an in-process Micrometer registry) to verify
 * that counters and timers are correctly registered and incremented.</p>
 */
@DisplayName("MicrometerDmosMetricsCollector")
class MicrometerDmosMetricsCollectorTest {

    private SimpleMeterRegistry registry;
    private MicrometerDmosMetricsCollector collector;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        collector = new MicrometerDmosMetricsCollector(registry);
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("constructor throws for null registry")
    void constructor_throwsForNullRegistry() {
        assertThatThrownBy(() -> new MicrometerDmosMetricsCollector(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("registry");
    }

    // -------------------------------------------------------------------------
    // increment
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("increment registers and increments a Micrometer counter")
    void increment_registersAndIncrementsCounter() {
        collector.increment(DmosMetricsCollector.CAMPAIGN_CREATED, Map.of("tenantId", "acme", "workspaceId", "ws-1"));

        Counter counter = registry.find(DmosMetricsCollector.CAMPAIGN_CREATED)
                .tag("tenantId", "acme")
                .tag("workspaceId", "ws-1")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("increment accumulates across multiple calls")
    void increment_accumulatesAcrossMultipleCalls() {
        Map<String, String> labels = Map.of("tenantId", "t1", "workspaceId", "ws-2");

        collector.increment(DmosMetricsCollector.CAMPAIGN_LAUNCHED, labels);
        collector.increment(DmosMetricsCollector.CAMPAIGN_LAUNCHED, labels);
        collector.increment(DmosMetricsCollector.CAMPAIGN_LAUNCHED, labels);

        Counter counter = registry.find(DmosMetricsCollector.CAMPAIGN_LAUNCHED)
                .tag("tenantId", "t1")
                .tag("workspaceId", "ws-2")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("increment with empty labels registers counter with no tags")
    void increment_emptyLabelsRegistersCounterWithNoTags() {
        collector.increment(DmosMetricsCollector.APPROVAL_REQUESTED, Map.of());

        Counter counter = registry.find(DmosMetricsCollector.APPROVAL_REQUESTED).counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("increment with null labels is treated as empty tag set")
    void increment_nullLabelsDoesNotThrow() {
        // Null labels should degrade gracefully (no exception, counter incremented with no tags)
        collector.increment(DmosMetricsCollector.CAMPAIGN_PAUSED, null);

        Counter counter = registry.find(DmosMetricsCollector.CAMPAIGN_PAUSED).counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("increment with blank counterName is silently dropped")
    void increment_blankCounterNameIsDropped() {
        // Must not throw; blank names are dropped via warn log
        collector.increment("", Map.of("tenantId", "t1"));
        collector.increment("  ", Map.of("tenantId", "t1"));
        // No counter registered
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("increment with null counterName is silently dropped")
    void increment_nullCounterNameIsDropped() {
        collector.increment(null, Map.of("tenantId", "t1"));
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("increment uses distinct counters for different label values")
    void increment_distinctLabelValuesProduceSeparateCounters() {
        collector.increment(DmosMetricsCollector.COMPLIANCE_VIOLATION,
                Map.of("tenantId", "t1", "ruleSet", "gdpr"));
        collector.increment(DmosMetricsCollector.COMPLIANCE_VIOLATION,
                Map.of("tenantId", "t2", "ruleSet", "ccpa"));

        Counter gdprCounter = registry.find(DmosMetricsCollector.COMPLIANCE_VIOLATION)
                .tag("tenantId", "t1").tag("ruleSet", "gdpr").counter();
        Counter ccpaCounter = registry.find(DmosMetricsCollector.COMPLIANCE_VIOLATION)
                .tag("tenantId", "t2").tag("ruleSet", "ccpa").counter();

        assertThat(gdprCounter).isNotNull();
        assertThat(ccpaCounter).isNotNull();
        assertThat(gdprCounter.count()).isEqualTo(1.0);
        assertThat(ccpaCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("increment handles null label value by substituting 'unknown'")
    void increment_nullLabelValueSubstitutedWithUnknown() {
        Map<String, String> labelsWithNull = new HashMap<>();
        labelsWithNull.put("tenantId", null);

        collector.increment(DmosMetricsCollector.PERFORMANCE_FETCHED, labelsWithNull);

        Counter counter = registry.find(DmosMetricsCollector.PERFORMANCE_FETCHED)
                .tag("tenantId", "unknown").counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // observe (native Timer override)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("observe records duration on a Micrometer Timer")
    void observe_recordsDurationOnTimer() {
        collector.observe(DmosMetricsCollector.API_REQUEST_DURATION, 250L,
                Map.of("servlet", "campaign", "method", "POST", "status", "200"));

        Timer timer = registry.find(DmosMetricsCollector.API_REQUEST_DURATION)
                .tag("servlet", "campaign")
                .tag("method", "POST")
                .tag("status", "200")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(250.0);
    }

    @Test
    @DisplayName("observe with blank metricName is silently dropped")
    void observe_blankMetricNameIsDropped() {
        collector.observe("", 100L, Map.of("tenantId", "t1"));
        assertThat(registry.getMeters()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // All canonical KPI constants roundtrip
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("all canonical KPI counters can be incremented without error")
    void allCanonicalKpiCounters_canBeIncrementedWithoutError() {
        Map<String, String> labels = Map.of("tenantId", "smoke", "workspaceId", "ws-smoke");

        collector.increment(DmosMetricsCollector.CAMPAIGN_CREATED, labels);
        collector.increment(DmosMetricsCollector.CAMPAIGN_LAUNCHED, labels);
        collector.increment(DmosMetricsCollector.CAMPAIGN_PAUSED, labels);
        collector.increment(DmosMetricsCollector.APPROVAL_REQUESTED, labels);
        collector.increment(DmosMetricsCollector.PERFORMANCE_FETCHED, labels);
        collector.increment(DmosMetricsCollector.APPROVAL_PENDING_GAUGE, labels);
        collector.increment(DmosMetricsCollector.COMPLIANCE_VIOLATION, labels);

        assertThat(registry.getMeters()).hasSize(7);
    }
}
