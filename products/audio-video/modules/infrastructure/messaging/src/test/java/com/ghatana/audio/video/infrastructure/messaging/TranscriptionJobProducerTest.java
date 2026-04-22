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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("TranscriptionJobProducer Tests [GH-90000]")
class TranscriptionJobProducerTest {

    @Mock
    private QueueProducerStrategy producerStrategy;

    @Mock
    private MetricsCollector metricsCollector;

    @Test
    @DisplayName("submitJob fails when producer has not started [GH-90000]")
    void submitJobFailsWhenNotStarted() { // GH-90000
        TranscriptionJobProducer producer = new TranscriptionJobProducer("av.jobs", producerStrategy, metricsCollector); // GH-90000
        TranscriptionJobProducer.TranscriptionJobMessage job =
            new TranscriptionJobProducer.TranscriptionJobMessage( // GH-90000
                UUID.randomUUID(), // GH-90000
                "tenant-1",
                UUID.randomUUID(), // GH-90000
                "en",
                "m1",
                Instant.now() // GH-90000
            );

        var promise = producer.submitJob(job); // GH-90000

        assertThat(promise.getException()).isInstanceOf(IllegalStateException.class); // GH-90000
        assertThat(promise.getException()).hasMessageContaining("Producer not started [GH-90000]");
    }

    @Test
    @DisplayName("start then submitJob delegates to producer strategy [GH-90000]")
    void startAndSubmitDelegatesToStrategy() { // GH-90000
        TranscriptionJobProducer producer = new TranscriptionJobProducer("av.jobs", producerStrategy, metricsCollector); // GH-90000
        when(producerStrategy.start()).thenReturn(Promise.complete()); // GH-90000
        when(producerStrategy.send(any(String.class), any(String.class))).thenReturn(Promise.of("msg-1 [GH-90000]"));

        producer.start().getResult(); // GH-90000

        TranscriptionJobProducer.TranscriptionJobMessage job =
            new TranscriptionJobProducer.TranscriptionJobMessage( // GH-90000
                UUID.randomUUID(), // GH-90000
                "tenant-1",
                UUID.randomUUID(), // GH-90000
                "en",
                "m1",
                Instant.now() // GH-90000
            );

        String messageId = producer.submitJob(job).getResult(); // GH-90000

        assertThat(messageId).isEqualTo("msg-1 [GH-90000]");
        verify(producerStrategy).send(eq(job.jobId().toString()), any(String.class)); // GH-90000
        verify(metricsCollector).incrementCounter("av.messaging.jobs.submitted", "queue", "av.jobs", "tenant_id", "tenant-1"); // GH-90000
    }
}


