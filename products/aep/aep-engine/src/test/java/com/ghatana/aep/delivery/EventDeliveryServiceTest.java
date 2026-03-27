package com.ghatana.aep.delivery;

import com.ghatana.aep.AepEngine;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EventDeliveryService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>noOp() service no-op behaviour</li>
 *   <li>withDestinations() success path</li>
 *   <li>Partial delivery failure isolation</li>
 *   <li>DeliveryResult value type behaviour</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Verify event delivery to external destinations via MessageSender abstraction
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EventDeliveryService")
class EventDeliveryServiceTest extends EventloopTestBase {

    private static final String TENANT = "tenant-delivery";
    private static final AepEngine.Event SAMPLE_EVENT = AepEngine.Event.of(
        "order.placed", Map.of("amount", 100));
    private static final List<AepEngine.Detection> NO_DETECTIONS = List.of();

    // ──────────────────────────────────────────────────────────────────────────
    // noOp()
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("noOp() delivery service")
    class NoOpTests {

        @Test
        @DisplayName("noOp() returns a successful DeliveryResult with no delivered destinations")
        void shouldReturnSuccessWithNoDeliveries() {
            EventDeliveryService service = EventDeliveryService.noOp();
            EventDeliveryService.DeliveryResult result = runPromise(
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS));

            assertThat(result.isFullyDelivered()).isTrue();
            assertThat(result.hasFailures()).isFalse();
            assertThat(result.delivered()).isEmpty();
            assertThat(result.failed()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // withDestinations() — success path
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withDestinations() — successful delivery")
    class SuccessDeliveryTests {

        @Test
        @DisplayName("all destinations are called and listed in delivered")
        void shouldDeliverToAllDestinations() {
            List<String> capturedA = new ArrayList<>();
            List<String> capturedB = new ArrayList<>();

            EventDeliveryService service = EventDeliveryService.withDestinations(
                new EventDeliveryService.EventDestination("dest-a",
                    new CapturingProducer(capturedA)),
                new EventDeliveryService.EventDestination("dest-b",
                    new CapturingProducer(capturedB))
            );

            EventDeliveryService.DeliveryResult result = runPromise(
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS));

            assertThat(result.delivered()).containsExactlyInAnyOrder("dest-a", "dest-b");
            assertThat(result.failed()).isEmpty();
            assertThat(capturedA).hasSize(1);
            assertThat(capturedB).hasSize(1);
        }

        @Test
        @DisplayName("delivered payload contains event type")
        void deliveredMessageShouldContainEventType() {
            List<String> captured = new ArrayList<>();
            EventDeliveryService service = EventDeliveryService.withDestinations(
                new EventDeliveryService.EventDestination("monitor",
                    new CapturingProducer(captured))
            );

            AepEngine.Event event = AepEngine.Event.of("payment.received", Map.of("amount", 200));
            runPromise(() -> service.deliver(TENANT, event, NO_DETECTIONS));

            assertThat(captured).hasSize(1);
            assertThat(captured.get(0)).contains("payment.received");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Partial failure isolation
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Partial delivery failures are isolated")
    class PartialFailureTests {

        @Test
        @DisplayName("failure in one destination does not block other destinations")
        void shouldIsolateFailingDestination() {
            List<String> capturedGood = new ArrayList<>();

            EventDeliveryService service = EventDeliveryService.withDestinations(
                new EventDeliveryService.EventDestination("bad-dest",
                    new ThrowingProducer("connection refused")),
                new EventDeliveryService.EventDestination("good-dest",
                    new CapturingProducer(capturedGood))
            );

            EventDeliveryService.DeliveryResult result = runPromise(
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS));

            assertThat(result.failed()).containsExactly("bad-dest");
            assertThat(result.delivered()).containsExactly("good-dest");
            assertThat(capturedGood).hasSize(1);
        }

        @Test
        @DisplayName("all-fail scenario returns fully-failed DeliveryResult")
        void allFailingShouldBeInFailedList() {
            EventDeliveryService service = EventDeliveryService.withDestinations(
                new EventDeliveryService.EventDestination("d1", new ThrowingProducer("err1")),
                new EventDeliveryService.EventDestination("d2", new ThrowingProducer("err2"))
            );

            EventDeliveryService.DeliveryResult result = runPromise(
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS));

            assertThat(result.isFullyDelivered()).isFalse();
            assertThat(result.hasFailures()).isTrue();
            assertThat(result.failed()).containsExactlyInAnyOrder("d1", "d2");
            assertThat(result.delivered()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DeliveryResult value type
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DeliveryResult value type")
    class DeliveryResultTests {

        @Test
        @DisplayName("isFullyDelivered() is true when no failures")
        void isFullyDeliveredTrueWhenNoFailures() {
            var r = new EventDeliveryService.DeliveryResult(List.of("d1"), List.of());
            assertThat(r.isFullyDelivered()).isTrue();
            assertThat(r.hasFailures()).isFalse();
        }

        @Test
        @DisplayName("isFullyDelivered() is false when any failures present")
        void isFullyDeliveredFalseWhenAnyFailure() {
            var r = new EventDeliveryService.DeliveryResult(List.of("d1"), List.of("d2"));
            assertThat(r.isFullyDelivered()).isFalse();
            assertThat(r.hasFailures()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Test double: captures the JSON payloads and always reports success. */
    private static final class CapturingProducer implements EventDeliveryService.MessageSender {

        private final List<String> captured;

        CapturingProducer(List<String> captured) {
            this.captured = captured;
        }

        @Override
        public boolean send(String tenantId, String eventType, String payloadJson,
                            Map<String, String> headers) {
            captured.add(payloadJson);
            return true;
        }
    }

    /** Test double: always throws on send. */
    private static final class ThrowingProducer implements EventDeliveryService.MessageSender {

        private final String errorMessage;

        ThrowingProducer(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean send(String tenantId, String eventType, String payloadJson,
                            Map<String, String> headers) {
            throw new RuntimeException(errorMessage);
        }
    }
}
