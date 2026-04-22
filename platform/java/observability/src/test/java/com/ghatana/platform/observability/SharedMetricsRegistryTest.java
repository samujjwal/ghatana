package com.ghatana.platform.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link SharedMetricsRegistry} — CP-001.4.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the shared metrics collection facade
 * @doc.layer observability
 * @doc.pattern Test
 */
@DisplayName("SharedMetricsRegistry [GH-90000]")
class SharedMetricsRegistryTest {

    private SharedMetricsRegistry buildRegistry() { // GH-90000
        return new SharedMetricsRegistry(new SimpleMeterRegistry(), "aep"); // GH-90000
    }

    // ─── constructor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("null registry throws NullPointerException [GH-90000]")
    void constructor_nullRegistry_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new SharedMetricsRegistry(null, "aep")); // GH-90000
    }

    @Test
    @DisplayName("null product throws NullPointerException [GH-90000]")
    void constructor_nullProduct_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new SharedMetricsRegistry(new SimpleMeterRegistry(), null)); // GH-90000
    }

    @Test
    @DisplayName("product() returns the configured product name [GH-90000]")
    void product_returnsProduct() { // GH-90000
        SharedMetricsRegistry reg = new SharedMetricsRegistry(new SimpleMeterRegistry(), "audio-video"); // GH-90000
        assertThat(reg.product()).isEqualTo("audio-video [GH-90000]");
    }

    // ─── counter ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("counter() [GH-90000]")
    class CounterTests {

        @Test
        @DisplayName("counter() returns a non-null counter [GH-90000]")
        void counter_returnsCounter() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThat(reg.counter("events.processed [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("counter() registers metric name in registeredMetrics() [GH-90000]")
        void counter_registeredName() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            reg.counter("events.processed [GH-90000]");
            assertThat(reg.registeredMetrics()).contains("events.processed [GH-90000]");
        }

        @Test
        @DisplayName("null counter name throws NullPointerException [GH-90000]")
        void counter_nullName_throwsNPE() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThatNullPointerException().isThrownBy(() -> reg.counter(null)); // GH-90000
        }

        @Test
        @DisplayName("odd-length tags throw IllegalArgumentException [GH-90000]")
        void counter_oddTags_throwsIAE() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> reg.counter("metric", "key-only")); // GH-90000
        }

        @Test
        @DisplayName("increment() increments the counter by 1 [GH-90000]")
        void increment_byOne() { // GH-90000
            SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); // GH-90000
            SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "aep"); // GH-90000
            reg.increment("events.processed [GH-90000]");
            assertThat(meterReg.get("events.processed [GH-90000]").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("increment(amount) increments by the correct amount [GH-90000]")
        void increment_byAmount() { // GH-90000
            SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); // GH-90000
            SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "aep"); // GH-90000
            reg.increment("events.processed", 5.0); // GH-90000
            assertThat(meterReg.get("events.processed [GH-90000]").counter().count()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("negative increment amount throws IllegalArgumentException [GH-90000]")
        void increment_negativeAmount_throwsIAE() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> reg.increment("m", -1.0)); // GH-90000
        }
    }

    // ─── timer ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("timer() [GH-90000]")
    class TimerTests {

        @Test
        @DisplayName("timer() returns a non-null timer [GH-90000]")
        void timer_returnsTimer() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThat(reg.timer("pipeline.latency [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("timer() registers metric name [GH-90000]")
        void timer_registered() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            reg.timer("pipeline.latency [GH-90000]");
            assertThat(reg.registeredMetrics()).contains("pipeline.latency [GH-90000]");
        }

        @Test
        @DisplayName("record() records a duration against the timer [GH-90000]")
        void record_duration() { // GH-90000
            SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); // GH-90000
            SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "aep"); // GH-90000
            reg.record("pipeline.latency", Duration.ofMillis(100)); // GH-90000
            assertThat(meterReg.get("pipeline.latency [GH-90000]").timer().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("null name throws NullPointerException [GH-90000]")
        void timer_nullName_throwsNPE() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThatNullPointerException().isThrownBy(() -> reg.timer(null)); // GH-90000
        }

        @Test
        @DisplayName("null duration throws NullPointerException [GH-90000]")
        void record_nullDuration_throwsNPE() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> reg.record("t", null)); // GH-90000
        }
    }

    // ─── gauge ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("gauge() [GH-90000]")
    class GaugeTests {

        @Test
        @DisplayName("gauge() registers a gauge metric [GH-90000]")
        void gauge_registers() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            reg.gauge("queue.depth", () -> 42); // GH-90000
            assertThat(reg.registeredMetrics()).contains("queue.depth [GH-90000]");
        }

        @Test
        @DisplayName("gauge() supplier is called at scrape time [GH-90000]")
        void gauge_supplierCalled() { // GH-90000
            SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); // GH-90000
            SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "aep"); // GH-90000
            int[] counter = {0};
            reg.gauge("queue.depth", () -> ++counter[0]); // GH-90000
            double value = meterReg.get("queue.depth [GH-90000]").gauge().value();
            assertThat(value).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("null name throws NullPointerException [GH-90000]")
        void gauge_nullName_throwsNPE() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> reg.gauge(null, () -> 1)); // GH-90000
        }

        @Test
        @DisplayName("null supplier throws NullPointerException [GH-90000]")
        void gauge_nullSupplier_throwsNPE() { // GH-90000
            SharedMetricsRegistry reg = buildRegistry(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> reg.gauge("metric", null)); // GH-90000
        }
    }

    // ─── product tag ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("all counters automatically carry the product tag [GH-90000]")
    void counter_hasProductTag() { // GH-90000
        SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); // GH-90000
        SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "audio-video"); // GH-90000
        reg.increment("events.processed [GH-90000]");

        String productTag = meterReg.get("events.processed [GH-90000]")
                .counter() // GH-90000
                .getId() // GH-90000
                .getTag("product [GH-90000]");
        assertThat(productTag).isEqualTo("audio-video [GH-90000]");
    }

    // ─── registeredMetrics snapshot ───────────────────────────────────────────

    @Test
    @DisplayName("registeredMetrics() returns an unmodifiable set [GH-90000]")
    void registeredMetrics_unmodifiable() { // GH-90000
        SharedMetricsRegistry reg = buildRegistry(); // GH-90000
        reg.counter("m1 [GH-90000]");
        assertThatExceptionOfType(UnsupportedOperationException.class) // GH-90000
                .isThrownBy(() -> reg.registeredMetrics().add("injected [GH-90000]"));
    }
}
