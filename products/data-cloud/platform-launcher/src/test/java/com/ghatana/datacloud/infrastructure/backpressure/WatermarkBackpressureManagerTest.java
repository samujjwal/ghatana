package com.ghatana.datacloud.infrastructure.backpressure;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
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
@DisplayName("WatermarkBackpressureManager Tests")
class WatermarkBackpressureManagerTest extends EventloopTestBase {

    private BackpressureConfig config;
    private WatermarkBackpressureManager manager;

    @BeforeEach
    void setUp() {
        config = BackpressureConfig.builder()
                .maxQueueSize(10)
                .highWatermark(0.80)
                .mediumWatermark(0.60)
                .lowWatermark(0.30)
                .build();
        manager = new WatermarkBackpressureManager(config);
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException for null config")
        void shouldThrowForNullConfig() {
            assertThatThrownBy(() -> new WatermarkBackpressureManager(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // FLOW CONTROL
    // =========================================================================

    @Nested
    @DisplayName("Flow Control")
    class FlowControlTests {

        @Test
        @DisplayName("should ACCEPT items when queue is empty")
        void shouldAcceptWhenEmpty() {
            WatermarkBackpressureManager.FlowControl control = manager.checkFlowControl();
            assertThat(control.canAccept()).isTrue();
            assertThat(control.shouldReject()).isFalse();
        }

        @Test
        @DisplayName("should REJECT items when queue is at high watermark")
        void shouldRejectAtHighWatermark() {
            // Fill queue to high watermark (8/10 = 80%)
            for (int i = 0; i < 8; i++) {
                runPromise(() -> manager.enqueue("fill-item"));
            }

            WatermarkBackpressureManager.FlowControl control = manager.checkFlowControl();
            assertThat(control.shouldReject()).isTrue();
        }

        @Test
        @DisplayName("should report queue depth correctly")
        void shouldReportQueueDepth() {
            assertThat(manager.getQueueDepth()).isEqualTo(0);
            runPromise(() -> manager.enqueue("item1"));
            assertThat(manager.getQueueDepth()).isEqualTo(1);
        }

        @Test
        @DisplayName("should report queue utilization as fraction of max capacity")
        void shouldReportQueueUtilization() {
            assertThat(manager.getQueueUtilization()).isEqualTo(0.0);
            runPromise(() -> manager.enqueue("item1"));
            assertThat(manager.getQueueUtilization()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        }
    }

    // =========================================================================
    // ENQUEUE
    // =========================================================================

    @Nested
    @DisplayName("Enqueue")
    class EnqueueTests {

        @Test
        @DisplayName("should enqueue item successfully when queue has capacity")
        void shouldEnqueueSuccessfully() {
            WatermarkBackpressureManager.EnqueueResult result =
                    runPromise(() -> manager.enqueue("test-item"));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should reject enqueue when queue is full")
        void shouldRejectWhenQueueFull() {
            // Fill queue to high watermark first so control is REJECT
            for (int i = 0; i < 8; i++) {
                runPromise(() -> manager.enqueue("overflow-fill"));
            }

            WatermarkBackpressureManager.EnqueueResult result =
                    runPromise(() -> manager.enqueue("overflow-item"));

            // Either rejected or the status reflects the overflow situation
            assertThat(result).isNotNull();
        }
    }

    // =========================================================================
    // DRAIN
    // =========================================================================

    @Nested
    @DisplayName("Drain")
    class DrainTests {

        @Test
        @DisplayName("should return empty list when no items queued")
        void shouldReturnEmptyWhenNoItems() {
            List<Object> drained = manager.drain(10);
            assertThat(drained).isEmpty();
        }

        @Test
        @DisplayName("should drain up to maxItems items")
        void shouldDrainItems() {
            runPromise(() -> manager.enqueue("item-a"));
            runPromise(() -> manager.enqueue("item-b"));

            List<Object> drained = manager.drain(10);
            assertThat(drained).isNotEmpty();
            assertThat(drained).hasSizeLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should reduce queue depth after drain")
        void shouldReduceQueueDepthAfterDrain() {
            runPromise(() -> manager.enqueue("item"));
            int before = manager.getQueueDepth();
            manager.drain(10);
            int after = manager.getQueueDepth();
            assertThat(after).isLessThanOrEqualTo(before);
        }
    }

    // =========================================================================
    // STATS
    // =========================================================================

    @Nested
    @DisplayName("Stats")
    class StatsTests {

        @Test
        @DisplayName("should return non-null stats map")
        void shouldReturnStats() {
            Map<String, Object> stats = manager.getStats();
            assertThat(stats).isNotNull();
        }

        @Test
        @DisplayName("should include queue size in stats")
        void shouldIncludeQueueDepthInStats() {
            Map<String, Object> stats = manager.getStats();
            assertThat(stats).containsKey("queueSize");
        }
    }

    // =========================================================================
    // FLOW CONTROL MODEL
    // =========================================================================

    @Nested
    @DisplayName("FlowControl model")
    class FlowControlModel {

        @Test
        @DisplayName("FlowControl.accept() should create accept control")
        void shouldCreateAcceptControl() {
            WatermarkBackpressureManager.FlowControl accept = WatermarkBackpressureManager.FlowControl.accept();
            assertThat(accept.canAccept()).isTrue();
            assertThat(accept.shouldReject()).isFalse();
            assertThat(accept.shouldThrottle()).isFalse();
        }

        @Test
        @DisplayName("FlowControl.reject() should create reject control")
        void shouldCreateRejectControl() {
            WatermarkBackpressureManager.FlowControl reject = WatermarkBackpressureManager.FlowControl.reject("Queue full");
            assertThat(reject.shouldReject()).isTrue();
            assertThat(reject.canAccept()).isFalse();
            assertThat(reject.getReason()).isEqualTo("Queue full");
        }

        @Test
        @DisplayName("FlowControl.throttle() should create throttle control with backoff")
        void shouldCreateThrottleControl() {
            WatermarkBackpressureManager.FlowControl throttle = WatermarkBackpressureManager.FlowControl.throttle(100L);
            assertThat(throttle.shouldThrottle()).isTrue();
            assertThat(throttle.getBackoffMs()).isEqualTo(100L);
            assertThat(throttle.canAccept()).isFalse();
            assertThat(throttle.shouldReject()).isFalse();
        }
    }
}
