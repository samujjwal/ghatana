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
@DisplayName("KafkaEventSource [GH-90000]")
class KafkaEventSourceTest extends EventloopTestBase {

    @Nested
    @DisplayName("constructor [GH-90000]")
    class Constructor {

        @Test
        @DisplayName("null adapter throws NullPointerException [GH-90000]")
        void nullAdapter() { // GH-90000
            assertThatThrownBy(() -> new KafkaEventSource(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("lifecycle [GH-90000]")
    class Lifecycle {

        @Test
        @DisplayName("start completes successfully [GH-90000]")
        void startCompletes() { // GH-90000
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class); // GH-90000
            KafkaEventSource source = new KafkaEventSource(adapter); // GH-90000
            Void result = runPromise(source::start); // GH-90000
            assertThat(result).isNull(); // GH-90000
        }

        @Test
        @DisplayName("stop calls adapter.close() [GH-90000]")
        void stopClosesAdapter() { // GH-90000
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class); // GH-90000
            KafkaEventSource source = new KafkaEventSource(adapter); // GH-90000
            runPromise(source::start); // GH-90000
            runPromise(source::stop); // GH-90000
            verify(adapter).close(); // GH-90000
        }
    }

    @Nested
    @DisplayName("next [GH-90000]")
    class Next {

        @Test
        @DisplayName("next before start returns failed Promise [GH-90000]")
        void nextBeforeStart() { // GH-90000
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class); // GH-90000
            KafkaEventSource source = new KafkaEventSource(adapter); // GH-90000

            try {
                runPromise(source::next); // GH-90000
                assertThat(false).as("expected exception [GH-90000]").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("source not started [GH-90000]");
            }
        }

        @Test
        @DisplayName("next with events returns first event [GH-90000]")
        void nextReturnsFirstEvent() { // GH-90000
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class); // GH-90000
            IngestEvent event = mock(IngestEvent.class); // GH-90000
            when(adapter.poll()).thenReturn(Promise.of(List.of(event))); // GH-90000

            KafkaEventSource source = new KafkaEventSource(adapter); // GH-90000
            runPromise(source::start); // GH-90000
            IngestEvent result = runPromise(source::next); // GH-90000
            assertThat(result).isSameAs(event); // GH-90000
        }

        @Test
        @DisplayName("next with empty list returns failed Promise [GH-90000]")
        void nextEmptyList() { // GH-90000
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class); // GH-90000
            when(adapter.poll()).thenReturn(Promise.of(List.of())); // GH-90000

            KafkaEventSource source = new KafkaEventSource(adapter); // GH-90000
            runPromise(source::start); // GH-90000

            try {
                runPromise(source::next); // GH-90000
                assertThat(false).as("expected exception [GH-90000]").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("no events available [GH-90000]");
            }
        }

        @Test
        @DisplayName("next with null list returns failed Promise [GH-90000]")
        void nextNullList() { // GH-90000
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class); // GH-90000
            when(adapter.poll()).thenReturn(Promise.of(null)); // GH-90000

            KafkaEventSource source = new KafkaEventSource(adapter); // GH-90000
            runPromise(source::start); // GH-90000

            try {
                runPromise(source::next); // GH-90000
                assertThat(false).as("expected exception [GH-90000]").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("no events available [GH-90000]");
            }
        }

        @Test
        @DisplayName("next after stop returns failed Promise [GH-90000]")
        void nextAfterStop() { // GH-90000
            KafkaConsumerAdapter adapter = mock(KafkaConsumerAdapter.class); // GH-90000
            KafkaEventSource source = new KafkaEventSource(adapter); // GH-90000
            runPromise(source::start); // GH-90000
            runPromise(source::stop); // GH-90000

            try {
                runPromise(source::next); // GH-90000
                assertThat(false).as("expected exception [GH-90000]").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("source not started [GH-90000]");
            }
        }
    }
}
