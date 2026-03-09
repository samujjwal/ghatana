package com.ghatana.core.connectors.impl.source;

import com.ghatana.core.connectors.IngestEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link KafkaEventSource}.
 *
 * Uses a mock KafkaConsumerAdapter to verify delegation, lifecycle state,
 * and error handling without requiring a real Kafka broker.
 */
@DisplayName("KafkaEventSource")
class KafkaEventSourceTest extends EventloopTestBase {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("null adapter throws NullPointerException")
        void nullAdapter() {
            assertThatThrownBy(() -> new KafkaEventSource(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("start completes successfully")
        void startCompletes() {
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class);
            KafkaEventSource source = new KafkaEventSource(adapter);
            Void result = runPromise(source::start);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("stop calls adapter.close()")
        void stopClosesAdapter() {
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class);
            KafkaEventSource source = new KafkaEventSource(adapter);
            runPromise(source::start);
            runPromise(source::stop);
            verify(adapter).close();
        }
    }

    @Nested
    @DisplayName("next")
    class Next {

        @Test
        @DisplayName("next before start returns failed Promise")
        void nextBeforeStart() {
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class);
            KafkaEventSource source = new KafkaEventSource(adapter);

            try {
                runPromise(source::next);
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("source not started");
            }
        }

        @Test
        @DisplayName("next with events returns first event")
        void nextReturnsFirstEvent() {
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class);
            IngestEvent event = mock(IngestEvent.class);
            when(adapter.poll()).thenReturn(Promise.of(List.of(event)));

            KafkaEventSource source = new KafkaEventSource(adapter);
            runPromise(source::start);
            IngestEvent result = runPromise(source::next);
            assertThat(result).isSameAs(event);
        }

        @Test
        @DisplayName("next with empty list returns failed Promise")
        void nextEmptyList() {
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class);
            when(adapter.poll()).thenReturn(Promise.of(List.of()));

            KafkaEventSource source = new KafkaEventSource(adapter);
            runPromise(source::start);

            try {
                runPromise(source::next);
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("no events available");
            }
        }

        @Test
        @DisplayName("next with null list returns failed Promise")
        void nextNullList() {
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class);
            when(adapter.poll()).thenReturn(Promise.of(null));

            KafkaEventSource source = new KafkaEventSource(adapter);
            runPromise(source::start);

            try {
                runPromise(source::next);
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("no events available");
            }
        }

        @Test
        @DisplayName("next after stop returns failed Promise")
        void nextAfterStop() {
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class);
            KafkaEventSource source = new KafkaEventSource(adapter);
            runPromise(source::start);
            runPromise(source::stop);

            try {
                runPromise(source::next);
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("source not started");
            }
        }
    }
}
