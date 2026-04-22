package com.ghatana.audio.video.infrastructure.messaging;

import com.ghatana.platform.messaging.strategy.QueueConsumerStrategy;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for transcription job consumer lifecycle and health semantics
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("TranscriptionJobConsumer Tests [GH-90000]")
class TranscriptionJobConsumerTest {

    @Mock
    private QueueConsumerStrategy consumerStrategy;

    @Mock
    private MetricsCollector metricsCollector;

    @Test
    @DisplayName("start fails when job processor is missing [GH-90000]")
    void startFailsWithoutProcessor() { // GH-90000
        TranscriptionJobConsumer consumer = new TranscriptionJobConsumer("av.jobs", consumerStrategy, metricsCollector); // GH-90000

        var promise = consumer.start(); // GH-90000

        assertThat(promise.getException()).isInstanceOf(IllegalStateException.class); // GH-90000
        assertThat(promise.getException()).hasMessageContaining("Job processor not set [GH-90000]");
    }

    @Test
    @DisplayName("start and stop delegate lifecycle to strategy [GH-90000]")
    void startAndStopDelegateLifecycle() { // GH-90000
        TranscriptionJobConsumer consumer = new TranscriptionJobConsumer("av.jobs", consumerStrategy, metricsCollector); // GH-90000
        consumer.setJobProcessor(job -> Promise.complete()); // GH-90000

        when(consumerStrategy.start()).thenReturn(Promise.complete()); // GH-90000
        when(consumerStrategy.stop()).thenReturn(Promise.complete()); // GH-90000
        when(consumerStrategy.isRunning()).thenReturn(true, false); // GH-90000

        consumer.start().getResult(); // GH-90000
        assertThat(consumer.isHealthy()).isTrue(); // GH-90000

        consumer.stop().getResult(); // GH-90000
        assertThat(consumer.isHealthy()).isFalse(); // GH-90000

        verify(consumerStrategy).start(); // GH-90000
        verify(consumerStrategy).stop(); // GH-90000
        verify(metricsCollector).incrementCounter("av.messaging.consumer.start", "queue", "av.jobs"); // GH-90000
        verify(metricsCollector).incrementCounter("av.messaging.consumer.stop", "queue", "av.jobs"); // GH-90000
    }

    @Test
    @DisplayName("start without processor does not move consumer to STARTED [GH-90000]")
    void startWithoutProcessorDoesNotChangeState() { // GH-90000
        TranscriptionJobConsumer consumer = new TranscriptionJobConsumer("av.jobs", consumerStrategy, metricsCollector); // GH-90000

        Promise<Void> firstStart = consumer.start(); // GH-90000
        assertThat(firstStart.getException()).isInstanceOf(IllegalStateException.class); // GH-90000

        consumer.setJobProcessor(job -> Promise.complete()); // GH-90000
        when(consumerStrategy.start()).thenReturn(Promise.complete()); // GH-90000

        consumer.start().getResult(); // GH-90000

        verify(consumerStrategy).start(); // GH-90000
    }

    @Test
    @DisplayName("message handler rethrows processor failure for strategy nack/retry [GH-90000]")
    void messageHandlerRethrowsProcessorFailure() { // GH-90000
        TranscriptionJobConsumer consumer = new TranscriptionJobConsumer("av.jobs", consumerStrategy, metricsCollector); // GH-90000
        consumer.setJobProcessor(job -> Promise.ofException(new RuntimeException("simulated failure [GH-90000]")));

        when(consumerStrategy.start()).thenReturn(Promise.complete()); // GH-90000

        consumer.start().getResult(); // GH-90000

        @SuppressWarnings("unchecked [GH-90000]")
        ArgumentCaptor<Consumer<String>> handlerCaptor =
            (ArgumentCaptor<Consumer<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Consumer.class); // GH-90000
        verify(consumerStrategy).setMessageHandler(handlerCaptor.capture()); // GH-90000

        String payload = "{\"jobId\":\"" + UUID.randomUUID() + // GH-90000
            "\",\"tenantId\":\"tenant-1\",\"audioFileId\":\"" + UUID.randomUUID() + // GH-90000
            "\",\"language\":\"en\",\"modelId\":\"m1\",\"submittedAt\":\"" + Instant.now() + "\"}"; // GH-90000

        assertThatThrownBy(() -> handlerCaptor.getValue().accept(payload)) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("simulated failure [GH-90000]");
    }
}
