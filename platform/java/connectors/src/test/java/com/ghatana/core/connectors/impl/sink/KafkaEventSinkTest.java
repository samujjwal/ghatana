package com.ghatana.core.connectors.impl.sink;

import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link KafkaEventSink}.
 *
 * Uses a mock KafkaProducerAdapter to verify delegation, lifecycle state,
 * and error handling without requiring a real Kafka broker.
 */
@DisplayName("KafkaEventSink")
class KafkaEventSinkTest extends EventloopTestBase {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("null adapter throws NullPointerException")
        void nullAdapter() {
            assertThatThrownBy(() -> new KafkaEventSink(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("start completes successfully")
        void startCompletes() {
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class);
            KafkaEventSink sink = new KafkaEventSink(adapter);
            Void result = runPromise(sink::start);
            assertThat(result).isNull(); // Promise<Void> completes
        }

        @Test
        @DisplayName("stop calls adapter.close()")
        void stopClosesAdapter() {
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class);
            KafkaEventSink sink = new KafkaEventSink(adapter);
            runPromise(sink::start);
            runPromise(sink::stop);
            verify(adapter).close();
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("send before start returns failed Promise")
        void sendBeforeStart() {
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class);
            KafkaEventSink sink = new KafkaEventSink(adapter);
            EventRecord record = mock(EventRecord.class);

            try {
                runPromise(() -> sink.send(record));
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("sink not started");
            }
        }

        @Test
        @DisplayName("send after start delegates to adapter")
        void sendDelegatesToAdapter() {
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class);
            EventRecord record = mock(EventRecord.class);
            when(adapter.send(any())).thenReturn(Promise.complete());

            KafkaEventSink sink = new KafkaEventSink(adapter);
            runPromise(sink::start);
            runPromise(() -> sink.send(record));

            verify(adapter).send(record);
        }

        @Test
        @DisplayName("send after stop returns failed Promise")
        void sendAfterStop() {
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class);
            KafkaEventSink sink = new KafkaEventSink(adapter);
            runPromise(sink::start);
            runPromise(sink::stop);

            EventRecord record = mock(EventRecord.class);
            try {
                runPromise(() -> sink.send(record));
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("sink not started");
            }
        }
    }
}
