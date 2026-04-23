package com.ghatana.aep.delivery;

import com.ghatana.aep.AepEngine;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EventDeliveryService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>noOp() service no-op behaviour</li> // GH-90000
 *   <li>withDestinations() success path</li> // GH-90000
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
    private static final AepEngine.Event SAMPLE_EVENT = AepEngine.Event.of( // GH-90000
        "order.placed", Map.of("amount", 100)); // GH-90000
    private static final List<AepEngine.Detection> NO_DETECTIONS = List.of(); // GH-90000

    // ──────────────────────────────────────────────────────────────────────────
    // noOp() // GH-90000
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("noOp() delivery service")
    class NoOpTests {

        @Test
        @DisplayName("noOp() returns a successful DeliveryResult with no delivered destinations")
        void shouldReturnSuccessWithNoDeliveries() { // GH-90000
            EventDeliveryService service = EventDeliveryService.noOp(); // GH-90000
            EventDeliveryService.DeliveryResult result = runPromise( // GH-90000
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS)); // GH-90000

            assertThat(result.isFullyDelivered()).isTrue(); // GH-90000
            assertThat(result.hasFailures()).isFalse(); // GH-90000
            assertThat(result.delivered()).isEmpty(); // GH-90000
            assertThat(result.failed()).isEmpty(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // withDestinations() — success path // GH-90000
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withDestinations() — successful delivery")
    class SuccessDeliveryTests {

        @Test
        @DisplayName("all destinations are called and listed in delivered")
        void shouldDeliverToAllDestinations() { // GH-90000
            List<String> capturedA = new ArrayList<>(); // GH-90000
            List<String> capturedB = new ArrayList<>(); // GH-90000

            EventDeliveryService service = EventDeliveryService.withDestinations( // GH-90000
                new EventDeliveryService.EventDestination("dest-a", // GH-90000
                    new CapturingProducer(capturedA)), // GH-90000
                new EventDeliveryService.EventDestination("dest-b", // GH-90000
                    new CapturingProducer(capturedB)) // GH-90000
            );

            EventDeliveryService.DeliveryResult result = runPromise( // GH-90000
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS)); // GH-90000

            assertThat(result.delivered()).containsExactlyInAnyOrder("dest-a", "dest-b"); // GH-90000
            assertThat(result.failed()).isEmpty(); // GH-90000
            assertThat(capturedA).hasSize(1); // GH-90000
            assertThat(capturedB).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("delivered payload contains event type")
        void deliveredMessageShouldContainEventType() { // GH-90000
            List<String> captured = new ArrayList<>(); // GH-90000
            EventDeliveryService service = EventDeliveryService.withDestinations( // GH-90000
                new EventDeliveryService.EventDestination("monitor", // GH-90000
                    new CapturingProducer(captured)) // GH-90000
            );

            AepEngine.Event event = AepEngine.Event.of("payment.received", Map.of("amount", 200)); // GH-90000
            runPromise(() -> service.deliver(TENANT, event, NO_DETECTIONS)); // GH-90000

            assertThat(captured).hasSize(1); // GH-90000
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
        void shouldIsolateFailingDestination() { // GH-90000
            List<String> capturedGood = new ArrayList<>(); // GH-90000

            EventDeliveryService service = EventDeliveryService.withDestinations( // GH-90000
                new EventDeliveryService.EventDestination("bad-dest", // GH-90000
                    new ThrowingProducer("connection refused")),
                new EventDeliveryService.EventDestination("good-dest", // GH-90000
                    new CapturingProducer(capturedGood)) // GH-90000
            );

            EventDeliveryService.DeliveryResult result = runPromise( // GH-90000
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS)); // GH-90000

            assertThat(result.failed()).containsExactly("bad-dest");
            assertThat(result.delivered()).containsExactly("good-dest");
            assertThat(result.failureDetails()) // GH-90000
                .containsEntry( // GH-90000
                    "bad-dest",
                    new EventDeliveryService.DeliveryFailure( // GH-90000
                        EventDeliveryService.DeliveryFailureCategory.UNKNOWN,
                        "connection refused"));
            assertThat(capturedGood).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("all-fail scenario returns fully-failed DeliveryResult")
        void allFailingShouldBeInFailedList() { // GH-90000
            EventDeliveryService service = EventDeliveryService.withDestinations( // GH-90000
                new EventDeliveryService.EventDestination("d1", new ThrowingProducer("err1")),
                new EventDeliveryService.EventDestination("d2", new ThrowingProducer("err2"))
            );

            EventDeliveryService.DeliveryResult result = runPromise( // GH-90000
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS)); // GH-90000

            assertThat(result.isFullyDelivered()).isFalse(); // GH-90000
            assertThat(result.hasFailures()).isTrue(); // GH-90000
            assertThat(result.failed()).containsExactlyInAnyOrder("d1", "d2"); // GH-90000
            assertThat(result.delivered()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("unchecked IO failures are marked retryable")
        void shouldCategorizeUncheckedIoAsRetryable() { // GH-90000
            EventDeliveryService service = EventDeliveryService.withDestinations( // GH-90000
                new EventDeliveryService.EventDestination("io-dest", // GH-90000
                    new UncheckedIoProducer("socket timeout"))
            );

            EventDeliveryService.DeliveryResult result = runPromise( // GH-90000
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS)); // GH-90000

            assertThat(result.failureDetails().get("io-dest").category())
                .isEqualTo(EventDeliveryService.DeliveryFailureCategory.RETRYABLE); // GH-90000
        }

        @Test
        @DisplayName("illegal argument failures are marked non-retryable")
        void shouldCategorizeIllegalArgumentAsNonRetryable() { // GH-90000
            EventDeliveryService service = EventDeliveryService.withDestinations( // GH-90000
                new EventDeliveryService.EventDestination("invalid-dest", // GH-90000
                    new InvalidPayloadProducer("bad payload"))
            );

            EventDeliveryService.DeliveryResult result = runPromise( // GH-90000
                () -> service.deliver(TENANT, SAMPLE_EVENT, NO_DETECTIONS)); // GH-90000

            assertThat(result.failureDetails().get("invalid-dest").category())
                .isEqualTo(EventDeliveryService.DeliveryFailureCategory.NON_RETRYABLE); // GH-90000
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
        void isFullyDeliveredTrueWhenNoFailures() { // GH-90000
            var r = new EventDeliveryService.DeliveryResult(List.of("d1"), List.of());
            assertThat(r.isFullyDelivered()).isTrue(); // GH-90000
            assertThat(r.hasFailures()).isFalse(); // GH-90000
            assertThat(r.failureDetails()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("isFullyDelivered() is false when any failures present")
        void isFullyDeliveredFalseWhenAnyFailure() { // GH-90000
            var r = new EventDeliveryService.DeliveryResult(List.of("d1"), List.of("d2"));
            assertThat(r.isFullyDelivered()).isFalse(); // GH-90000
            assertThat(r.hasFailures()).isTrue(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Test double: captures the JSON payloads and always reports success. */
    private static final class CapturingProducer implements EventDeliveryService.MessageSender {

        private final List<String> captured;

        CapturingProducer(List<String> captured) { // GH-90000
            this.captured = captured;
        }

        @Override
        public boolean send(String tenantId, String eventType, String payloadJson, // GH-90000
                            Map<String, String> headers) {
            captured.add(payloadJson); // GH-90000
            return true;
        }
    }

    /** Test double: always throws on send. */
    private static final class ThrowingProducer implements EventDeliveryService.MessageSender {

        private final String errorMessage;

        ThrowingProducer(String errorMessage) { // GH-90000
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean send(String tenantId, String eventType, String payloadJson, // GH-90000
                            Map<String, String> headers) {
            throw new RuntimeException(errorMessage); // GH-90000
        }
    }

    private static final class UncheckedIoProducer implements EventDeliveryService.MessageSender {

        private final String errorMessage;

        private UncheckedIoProducer(String errorMessage) { // GH-90000
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean send(String tenantId, String eventType, String payloadJson, // GH-90000
                            Map<String, String> headers) {
            throw new UncheckedIOException(new IOException(errorMessage)); // GH-90000
        }
    }

    private static final class InvalidPayloadProducer implements EventDeliveryService.MessageSender {

        private final String errorMessage;

        private InvalidPayloadProducer(String errorMessage) { // GH-90000
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean send(String tenantId, String eventType, String payloadJson, // GH-90000
                            Map<String, String> headers) {
            throw new IllegalArgumentException(errorMessage); // GH-90000
        }
    }
}
