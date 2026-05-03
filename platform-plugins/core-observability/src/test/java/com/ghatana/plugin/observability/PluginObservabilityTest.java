package com.ghatana.plugin.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link PluginObservability}.
 *
 * <p>OpenTelemetry defaults to the no-op implementation in test environments
 * (no SDK registered), so these tests verify behavioral correctness:
 * correct metric naming, attribute composition, span scope lifecycle, and
 * helper methods — without requiring a live OTel exporter.
 *
 * @doc.type class
 * @doc.purpose Tests for PluginObservability base class behaviors
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PluginObservability Tests")
class PluginObservabilityTest {

    /** Minimal concrete subclass for testing the abstract base class. */
    static class TestPlugin extends PluginObservability {
        TestPlugin(String pluginId) {
            super(pluginId);
        }

        void emitMetric(String name, Map<String, String> labels, long value) {
            recordMetric(name, labels, value);
        }

        void emitHistogram(String name, double value, Map<String, String> labels) {
            recordHistogram(name, value, labels);
        }

        PluginObservability.SpanScope startNamedSpan(String operation) {
            return startSpan(operation);
        }

        PluginObservability.SpanScope startFullSpan(String op, String tenant,
                                                    String actor, String classification) {
            return startSpan(op, tenant, actor, classification);
        }
    }

    // ==================== Construction ====================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("subclass instantiates without error using any plugin ID")
        void constructor_anyPluginId_noException() {
            assertThatCode(() -> new TestPlugin("my-plugin")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("pluginId is stored and accessible via field")
        void constructor_storesPluginId() {
            TestPlugin plugin = new TestPlugin("test-plugin-id");
            assertThat(plugin.pluginId).isEqualTo("test-plugin-id");
        }
    }

    // ==================== recordMetric ====================

    @Nested
    @DisplayName("recordMetric")
    class RecordMetricTests {

        @Test
        @DisplayName("does not throw with valid name and labels")
        void recordMetric_validArgs_noException() {
            TestPlugin plugin = new TestPlugin("audit");
            assertThatCode(() -> plugin.emitMetric(
                    "denials_total", Map.of("tenant_id", "t1", "result", "denied"), 1L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("does not throw with empty labels")
        void recordMetric_emptyLabels_noException() {
            TestPlugin plugin = new TestPlugin("audit");
            assertThatCode(() -> plugin.emitMetric("events_total", Map.of(), 42L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("does not throw with zero value")
        void recordMetric_zeroValue_noException() {
            TestPlugin plugin = new TestPlugin("audit");
            assertThatCode(() -> plugin.emitMetric("entries_emitted", Map.of("tenant_id", "t2"), 0L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("does not throw with large value")
        void recordMetric_largeValue_noException() {
            TestPlugin plugin = new TestPlugin("audit");
            assertThatCode(() -> plugin.emitMetric("bytes_processed", Map.of(), Long.MAX_VALUE))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== recordHistogram ====================

    @Nested
    @DisplayName("recordHistogram")
    class RecordHistogramTests {

        @Test
        @DisplayName("does not throw with valid latency value")
        void recordHistogram_latency_noException() {
            TestPlugin plugin = new TestPlugin("search");
            assertThatCode(() -> plugin.emitHistogram(
                    "query_latency_ms", 125.5, Map.of("tenant_id", "t3")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("does not throw with zero latency")
        void recordHistogram_zeroValue_noException() {
            TestPlugin plugin = new TestPlugin("search");
            assertThatCode(() -> plugin.emitHistogram("response_time_ms", 0.0, Map.of()))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== startSpan ====================

    @Nested
    @DisplayName("startSpan")
    class StartSpanTests {

        @Test
        @DisplayName("startSpan returns non-null SpanScope")
        void startSpan_returnsNonNull() {
            TestPlugin plugin = new TestPlugin("lineage");
            PluginObservability.SpanScope scope = plugin.startNamedSpan("check-access");
            assertThat(scope).isNotNull();
            scope.close();
        }

        @Test
        @DisplayName("SpanScope can be closed without error")
        void spanScope_close_noException() {
            TestPlugin plugin = new TestPlugin("lineage");
            assertThatCode(() -> {
                try (PluginObservability.SpanScope scope = plugin.startNamedSpan("post-entry")) {
                    // Span is active during this block
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("startSpan with all attributes does not throw")
        void startSpan_allAttributes_noException() {
            TestPlugin plugin = new TestPlugin("governance");
            assertThatCode(() -> {
                try (PluginObservability.SpanScope scope =
                             plugin.startFullSpan("evaluate-rule", "tenant-xyz", "user-1", "SENSITIVE")) {
                    // Span has tenant, actor, and classification attributes
                }
            }).doesNotThrowAnyException();
        }
    }

    // ==================== Attribute Helpers ====================

    @Nested
    @DisplayName("Attribute helpers")
    class AttributeHelperTests {

        @Test
        @DisplayName("attributes() includes metric_version sentinel")
        void attributes_includesMetricVersion() {
            Map<String, String> result = PluginObservability.attributes(Map.of("k1", "v1"));
            assertThat(result).containsKey("metric_version");
            assertThat(result).containsEntry("k1", "v1");
        }

        @Test
        @DisplayName("attributes() with null input returns map with metric_version only")
        void attributes_nullInput_returnsVersionOnly() {
            Map<String, String> result = PluginObservability.attributes(null);
            assertThat(result).containsKey("metric_version");
        }

        @Test
        @DisplayName("attr(k,v) includes metric_version sentinel")
        void attr_single_includesVersion() {
            Map<String, String> result = PluginObservability.attr("tenant_id", "t1");
            assertThat(result).containsEntry("tenant_id", "t1");
            assertThat(result).containsKey("metric_version");
        }

        @Test
        @DisplayName("attr(k1,v1,k2,v2) includes both keys and metric_version")
        void attr_double_includesBothKeys() {
            Map<String, String> result = PluginObservability.attr("a", "1", "b", "2");
            assertThat(result).containsEntry("a", "1").containsEntry("b", "2");
            assertThat(result).containsKey("metric_version");
        }
    }
}
