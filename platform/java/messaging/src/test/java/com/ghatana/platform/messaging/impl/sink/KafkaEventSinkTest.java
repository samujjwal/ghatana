package com.ghatana.core.connectors.impl.sink;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


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
        void nullAdapter() { // GH-90000
            assertThatThrownBy(() -> new KafkaEventSink(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("start completes successfully")
        void startCompletes() { // GH-90000
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class); // GH-90000
            KafkaEventSink sink = new KafkaEventSink(adapter); // GH-90000
            Void result = runPromise(sink::start); // GH-90000
            assertThat(result).isNull(); // Promise<Void> completes // GH-90000
        }

        @Test
        @DisplayName("stop calls adapter.close()")
        void stopClosesAdapter() { // GH-90000
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class); // GH-90000
            KafkaEventSink sink = new KafkaEventSink(adapter); // GH-90000
            runPromise(sink::start); // GH-90000
            runPromise(sink::stop); // GH-90000
            verify(adapter).close(); // GH-90000
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("send before start returns failed Promise")
        void sendBeforeStart() { // GH-90000
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class); // GH-90000
            KafkaEventSink sink = new KafkaEventSink(adapter); // GH-90000
            EventLogStore.EventEntry entry = mock(EventLogStore.EventEntry.class); // GH-90000
            TenantContext tenant = TenantContext.of("test-tenant");

            try {
                runPromise(() -> sink.send(tenant, entry)); // GH-90000
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("sink not started");
            }
        }

        @Test
        @DisplayName("send after start delegates to adapter")
        void sendDelegatesToAdapter() { // GH-90000
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class); // GH-90000
            EventLogStore.EventEntry entry = mock(EventLogStore.EventEntry.class); // GH-90000
            TenantContext tenant = TenantContext.of("test-tenant");
            when(adapter.send(any())).thenReturn(Promise.complete()); // GH-90000

            KafkaEventSink sink = new KafkaEventSink(adapter); // GH-90000
            runPromise(sink::start); // GH-90000
            runPromise(() -> sink.send(tenant, entry)); // GH-90000

            verify(adapter).send(entry); // GH-90000
        }

        @Test
        @DisplayName("send after stop returns failed Promise")
        void sendAfterStop() { // GH-90000
            KafkaProducerAdapter adapter = mock(KafkaProducerAdapter.class); // GH-90000
            KafkaEventSink sink = new KafkaEventSink(adapter); // GH-90000
            runPromise(sink::start); // GH-90000
            runPromise(sink::stop); // GH-90000

            EventLogStore.EventEntry entry = mock(EventLogStore.EventEntry.class); // GH-90000
            TenantContext tenant = TenantContext.of("test-tenant");
            try {
                runPromise(() -> sink.send(tenant, entry)); // GH-90000
                assertThat(false).as("expected exception").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("sink not started");
            }
        }
    }
}
