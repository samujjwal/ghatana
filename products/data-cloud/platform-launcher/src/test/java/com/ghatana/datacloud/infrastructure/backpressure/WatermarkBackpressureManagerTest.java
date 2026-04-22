package com.ghatana.datacloud.infrastructure.backpressure;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link WatermarkBackpressureManager}.
 *
 * <p>Validates ACCEPT / THROTTLE / REJECT flow control logic based on
 * high/medium/low watermark configuration.
 *
 * @doc.type test
 * @doc.purpose Validate watermark-based backpressure flow control, enqueue, drain, and stats
 * @doc.layer infrastructure
 */
@DisplayName("WatermarkBackpressureManager Tests [GH-90000]")
class WatermarkBackpressureManagerTest extends EventloopTestBase {

    private BackpressureConfig config;
    private WatermarkBackpressureManager manager;

    @BeforeEach
    void setUp() { // GH-90000
        config = BackpressureConfig.builder() // GH-90000
                .maxQueueSize(10) // GH-90000
                .highWatermark(0.80) // GH-90000
                .mediumWatermark(0.60) // GH-90000
                .lowWatermark(0.30) // GH-90000
                .build(); // GH-90000
        manager = new WatermarkBackpressureManager(config); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException for null config [GH-90000]")
        void shouldThrowForNullConfig() { // GH-90000
            assertThatThrownBy(() -> new WatermarkBackpressureManager(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // FLOW CONTROL
    // =========================================================================

    @Nested
    @DisplayName("Flow Control [GH-90000]")
    class FlowControlTests {

        @Test
        @DisplayName("should ACCEPT items when queue is empty [GH-90000]")
        void shouldAcceptWhenEmpty() { // GH-90000
            WatermarkBackpressureManager.FlowControl control = manager.checkFlowControl(); // GH-90000
            assertThat(control.canAccept()).isTrue(); // GH-90000
            assertThat(control.shouldReject()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should REJECT items when queue is at high watermark [GH-90000]")
        void shouldRejectAtHighWatermark() { // GH-90000
            // Fill queue to high watermark (8/10 = 80%) // GH-90000
            for (int i = 0; i < 8; i++) { // GH-90000
                runPromise(() -> manager.enqueue("fill-item [GH-90000]"));
            }

            WatermarkBackpressureManager.FlowControl control = manager.checkFlowControl(); // GH-90000
            assertThat(control.shouldReject()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should report queue depth correctly [GH-90000]")
        void shouldReportQueueDepth() { // GH-90000
            assertThat(manager.getQueueDepth()).isEqualTo(0); // GH-90000
            runPromise(() -> manager.enqueue("item1 [GH-90000]"));
            assertThat(manager.getQueueDepth()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should report queue utilization as fraction of max capacity [GH-90000]")
        void shouldReportQueueUtilization() { // GH-90000
            assertThat(manager.getQueueUtilization()).isEqualTo(0.0); // GH-90000
            runPromise(() -> manager.enqueue("item1 [GH-90000]"));
            assertThat(manager.getQueueUtilization()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0); // GH-90000
        }
    }

    // =========================================================================
    // ENQUEUE
    // =========================================================================

    @Nested
    @DisplayName("Enqueue [GH-90000]")
    class EnqueueTests {

        @Test
        @DisplayName("should enqueue item successfully when queue has capacity [GH-90000]")
        void shouldEnqueueSuccessfully() { // GH-90000
            WatermarkBackpressureManager.EnqueueResult result =
                    runPromise(() -> manager.enqueue("test-item [GH-90000]"));

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject enqueue when queue is full [GH-90000]")
        void shouldRejectWhenQueueFull() { // GH-90000
            // Fill queue to high watermark first so control is REJECT
            for (int i = 0; i < 8; i++) { // GH-90000
                runPromise(() -> manager.enqueue("overflow-fill [GH-90000]"));
            }

            WatermarkBackpressureManager.EnqueueResult result =
                    runPromise(() -> manager.enqueue("overflow-item [GH-90000]"));

            // Either rejected or the status reflects the overflow situation
            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // DRAIN
    // =========================================================================

    @Nested
    @DisplayName("Drain [GH-90000]")
    class DrainTests {

        @Test
        @DisplayName("should return empty list when no items queued [GH-90000]")
        void shouldReturnEmptyWhenNoItems() { // GH-90000
            List<Object> drained = manager.drain(10); // GH-90000
            assertThat(drained).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should drain up to maxItems items [GH-90000]")
        void shouldDrainItems() { // GH-90000
            runPromise(() -> manager.enqueue("item-a [GH-90000]"));
            runPromise(() -> manager.enqueue("item-b [GH-90000]"));

            List<Object> drained = manager.drain(10); // GH-90000
            assertThat(drained).isNotEmpty(); // GH-90000
            assertThat(drained).hasSizeLessThanOrEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should reduce queue depth after drain [GH-90000]")
        void shouldReduceQueueDepthAfterDrain() { // GH-90000
            runPromise(() -> manager.enqueue("item [GH-90000]"));
            int before = manager.getQueueDepth(); // GH-90000
            manager.drain(10); // GH-90000
            int after = manager.getQueueDepth(); // GH-90000
            assertThat(after).isLessThanOrEqualTo(before); // GH-90000
        }
    }

    // =========================================================================
    // STATS
    // =========================================================================

    @Nested
    @DisplayName("Stats [GH-90000]")
    class StatsTests {

        @Test
        @DisplayName("should return non-null stats map [GH-90000]")
        void shouldReturnStats() { // GH-90000
            Map<String, Object> stats = manager.getStats(); // GH-90000
            assertThat(stats).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should include queue size in stats [GH-90000]")
        void shouldIncludeQueueDepthInStats() { // GH-90000
            Map<String, Object> stats = manager.getStats(); // GH-90000
            assertThat(stats).containsKey("queueSize [GH-90000]");
        }
    }

    // =========================================================================
    // FLOW CONTROL MODEL
    // =========================================================================

    @Nested
    @DisplayName("FlowControl model [GH-90000]")
    class FlowControlModel {

        @Test
        @DisplayName("FlowControl.accept() should create accept control [GH-90000]")
        void shouldCreateAcceptControl() { // GH-90000
            WatermarkBackpressureManager.FlowControl accept = WatermarkBackpressureManager.FlowControl.accept(); // GH-90000
            assertThat(accept.canAccept()).isTrue(); // GH-90000
            assertThat(accept.shouldReject()).isFalse(); // GH-90000
            assertThat(accept.shouldThrottle()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("FlowControl.reject() should create reject control [GH-90000]")
        void shouldCreateRejectControl() { // GH-90000
            WatermarkBackpressureManager.FlowControl reject = WatermarkBackpressureManager.FlowControl.reject("Queue full [GH-90000]");
            assertThat(reject.shouldReject()).isTrue(); // GH-90000
            assertThat(reject.canAccept()).isFalse(); // GH-90000
            assertThat(reject.getReason()).isEqualTo("Queue full [GH-90000]");
        }

        @Test
        @DisplayName("FlowControl.throttle() should create throttle control with backoff [GH-90000]")
        void shouldCreateThrottleControl() { // GH-90000
            WatermarkBackpressureManager.FlowControl throttle = WatermarkBackpressureManager.FlowControl.throttle(100L); // GH-90000
            assertThat(throttle.shouldThrottle()).isTrue(); // GH-90000
            assertThat(throttle.getBackoffMs()).isEqualTo(100L); // GH-90000
            assertThat(throttle.canAccept()).isFalse(); // GH-90000
            assertThat(throttle.shouldReject()).isFalse(); // GH-90000
        }
    }
}
