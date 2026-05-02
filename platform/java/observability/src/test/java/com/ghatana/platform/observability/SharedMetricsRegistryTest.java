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
@DisplayName("SharedMetricsRegistry")
class SharedMetricsRegistryTest {

    private SharedMetricsRegistry buildRegistry() { 
        return new SharedMetricsRegistry(new SimpleMeterRegistry(), "aep"); 
    }

    // ─── constructor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("null registry throws NullPointerException")
    void constructor_nullRegistry_throwsNPE() { 
        assertThatNullPointerException() 
                .isThrownBy(() -> new SharedMetricsRegistry(null, "aep")); 
    }

    @Test
    @DisplayName("null product throws NullPointerException")
    void constructor_nullProduct_throwsNPE() { 
        assertThatNullPointerException() 
                .isThrownBy(() -> new SharedMetricsRegistry(new SimpleMeterRegistry(), null)); 
    }

    @Test
    @DisplayName("product() returns the configured product name")
    void product_returnsProduct() { 
        SharedMetricsRegistry reg = new SharedMetricsRegistry(new SimpleMeterRegistry(), "audio-video"); 
        assertThat(reg.product()).isEqualTo("audio-video");
    }

    // ─── counter ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("counter()")
    class CounterTests {

        @Test
        @DisplayName("counter() returns a non-null counter")
        void counter_returnsCounter() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThat(reg.counter("events.processed")).isNotNull();
        }

        @Test
        @DisplayName("counter() registers metric name in registeredMetrics()")
        void counter_registeredName() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            reg.counter("events.processed");
            assertThat(reg.registeredMetrics()).contains("events.processed");
        }

        @Test
        @DisplayName("null counter name throws NullPointerException")
        void counter_nullName_throwsNPE() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThatNullPointerException().isThrownBy(() -> reg.counter(null)); 
        }

        @Test
        @DisplayName("odd-length tags throw IllegalArgumentException")
        void counter_oddTags_throwsIAE() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThatIllegalArgumentException() 
                    .isThrownBy(() -> reg.counter("metric", "key-only")); 
        }

        @Test
        @DisplayName("increment() increments the counter by 1")
        void increment_byOne() { 
            SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); 
            SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "aep"); 
            reg.increment("events.processed");
            assertThat(meterReg.get("events.processed").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("increment(amount) increments by the correct amount")
        void increment_byAmount() { 
            SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); 
            SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "aep"); 
            reg.increment("events.processed", 5.0); 
            assertThat(meterReg.get("events.processed").counter().count()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("negative increment amount throws IllegalArgumentException")
        void increment_negativeAmount_throwsIAE() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThatIllegalArgumentException() 
                    .isThrownBy(() -> reg.increment("m", -1.0)); 
        }
    }

    // ─── timer ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("timer()")
    class TimerTests {

        @Test
        @DisplayName("timer() returns a non-null timer")
        void timer_returnsTimer() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThat(reg.timer("pipeline.latency")).isNotNull();
        }

        @Test
        @DisplayName("timer() registers metric name")
        void timer_registered() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            reg.timer("pipeline.latency");
            assertThat(reg.registeredMetrics()).contains("pipeline.latency");
        }

        @Test
        @DisplayName("record() records a duration against the timer")
        void record_duration() { 
            SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); 
            SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "aep"); 
            reg.record("pipeline.latency", Duration.ofMillis(100)); 
            assertThat(meterReg.get("pipeline.latency").timer().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("null name throws NullPointerException")
        void timer_nullName_throwsNPE() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThatNullPointerException().isThrownBy(() -> reg.timer(null)); 
        }

        @Test
        @DisplayName("null duration throws NullPointerException")
        void record_nullDuration_throwsNPE() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThatNullPointerException() 
                    .isThrownBy(() -> reg.record("t", null)); 
        }
    }

    // ─── gauge ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("gauge()")
    class GaugeTests {

        @Test
        @DisplayName("gauge() registers a gauge metric")
        void gauge_registers() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            reg.gauge("queue.depth", () -> 42); 
            assertThat(reg.registeredMetrics()).contains("queue.depth");
        }

        @Test
        @DisplayName("gauge() supplier is called at scrape time")
        void gauge_supplierCalled() { 
            SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); 
            SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "aep"); 
            int[] counter = {0};
            reg.gauge("queue.depth", () -> ++counter[0]); 
            double value = meterReg.get("queue.depth").gauge().value();
            assertThat(value).isGreaterThan(0); 
        }

        @Test
        @DisplayName("null name throws NullPointerException")
        void gauge_nullName_throwsNPE() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThatNullPointerException() 
                    .isThrownBy(() -> reg.gauge(null, () -> 1)); 
        }

        @Test
        @DisplayName("null supplier throws NullPointerException")
        void gauge_nullSupplier_throwsNPE() { 
            SharedMetricsRegistry reg = buildRegistry(); 
            assertThatNullPointerException() 
                    .isThrownBy(() -> reg.gauge("metric", null)); 
        }
    }

    // ─── product tag ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("all counters automatically carry the product tag")
    void counter_hasProductTag() { 
        SimpleMeterRegistry meterReg = new SimpleMeterRegistry(); 
        SharedMetricsRegistry reg = new SharedMetricsRegistry(meterReg, "audio-video"); 
        reg.increment("events.processed");

        String productTag = meterReg.get("events.processed")
                .counter() 
                .getId() 
                .getTag("product");
        assertThat(productTag).isEqualTo("audio-video");
    }

    // ─── registeredMetrics snapshot ───────────────────────────────────────────

    @Test
    @DisplayName("registeredMetrics() returns an unmodifiable set")
    void registeredMetrics_unmodifiable() { 
        SharedMetricsRegistry reg = buildRegistry(); 
        reg.counter("m1");
        assertThatExceptionOfType(UnsupportedOperationException.class) 
                .isThrownBy(() -> reg.registeredMetrics().add("injected"));
    }
}
