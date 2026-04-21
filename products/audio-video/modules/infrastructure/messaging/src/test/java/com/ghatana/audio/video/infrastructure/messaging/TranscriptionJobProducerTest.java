package com.ghatana.audio.video.infrastructure.messaging;

import com.ghatana.platform.messaging.strategy.QueueProducerStrategy;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for transcription job producer lifecycle and submission behavior
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TranscriptionJobProducer Tests")
class TranscriptionJobProducerTest {

    @Mock
    private QueueProducerStrategy producerStrategy;

    @Mock
    private MetricsCollector metricsCollector;

    @Test
    @DisplayName("submitJob fails when producer has not started")
    void submitJobFailsWhenNotStarted() {
        TranscriptionJobProducer producer = new TranscriptionJobProducer("av.jobs", producerStrategy, metricsCollector);
        TranscriptionJobProducer.TranscriptionJobMessage job =
            new TranscriptionJobProducer.TranscriptionJobMessage(
                UUID.randomUUID(),
                "tenant-1",
                UUID.randomUUID(),
                "en",
                "m1",
                Instant.now()
            );

        var promise = producer.submitJob(job);

        assertThat(promise.getException()).isInstanceOf(IllegalStateException.class);
        assertThat(promise.getException()).hasMessageContaining("Producer not started");
    }

    @Test
    @DisplayName("start then submitJob delegates to producer strategy")
    void startAndSubmitDelegatesToStrategy() {
        TranscriptionJobProducer producer = new TranscriptionJobProducer("av.jobs", producerStrategy, metricsCollector);
        when(producerStrategy.start()).thenReturn(Promise.complete());
        when(producerStrategy.send(any(String.class), any(String.class))).thenReturn(Promise.of("msg-1"));

        producer.start().getResult();

        TranscriptionJobProducer.TranscriptionJobMessage job =
            new TranscriptionJobProducer.TranscriptionJobMessage(
                UUID.randomUUID(),
                "tenant-1",
                UUID.randomUUID(),
                "en",
                "m1",
                Instant.now()
            );

        String messageId = producer.submitJob(job).getResult();

        assertThat(messageId).isEqualTo("msg-1");
        verify(producerStrategy).send(eq(job.jobId().toString()), any(String.class));
        verify(metricsCollector).incrementCounter("av.messaging.jobs.submitted", "queue", "av.jobs", "tenant_id", "tenant-1");
    }
}


