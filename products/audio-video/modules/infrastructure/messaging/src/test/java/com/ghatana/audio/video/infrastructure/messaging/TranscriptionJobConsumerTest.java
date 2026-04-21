package com.ghatana.audio.video.infrastructure.messaging;

import com.ghatana.platform.messaging.strategy.QueueConsumerStrategy;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for transcription job consumer lifecycle and health semantics
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TranscriptionJobConsumer Tests")
class TranscriptionJobConsumerTest {

    @Mock
    private QueueConsumerStrategy consumerStrategy;

    @Mock
    private MetricsCollector metricsCollector;

    @Test
    @DisplayName("start fails when job processor is missing")
    void startFailsWithoutProcessor() {
        TranscriptionJobConsumer consumer = new TranscriptionJobConsumer("av.jobs", consumerStrategy, metricsCollector);

        var promise = consumer.start();

        assertThat(promise.getException()).isInstanceOf(IllegalStateException.class);
        assertThat(promise.getException()).hasMessageContaining("Job processor not set");
    }

    @Test
    @DisplayName("start and stop delegate lifecycle to strategy")
    void startAndStopDelegateLifecycle() {
        TranscriptionJobConsumer consumer = new TranscriptionJobConsumer("av.jobs", consumerStrategy, metricsCollector);
        consumer.setJobProcessor(job -> Promise.complete());

        when(consumerStrategy.start()).thenReturn(Promise.complete());
        when(consumerStrategy.stop()).thenReturn(Promise.complete());
        when(consumerStrategy.isRunning()).thenReturn(true, false);

        consumer.start().getResult();
        assertThat(consumer.isHealthy()).isTrue();

        consumer.stop().getResult();
        assertThat(consumer.isHealthy()).isFalse();

        verify(consumerStrategy).start();
        verify(consumerStrategy).stop();
        verify(metricsCollector).incrementCounter("av.messaging.consumer.start", "queue", "av.jobs");
        verify(metricsCollector).incrementCounter("av.messaging.consumer.stop", "queue", "av.jobs");
    }
}


