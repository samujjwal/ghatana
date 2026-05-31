package com.ghatana.audio.video.infrastructure.messaging;

import com.ghatana.platform.messaging.strategy.QueueProducerStrategy;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for transcription job producer lifecycle, submission behavior, and correlation ID propagation
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

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private TranscriptionJobProducer.TranscriptionJobMessage testJob(String tenantId) {
        return new TranscriptionJobProducer.TranscriptionJobMessage(
            UUID.randomUUID(), tenantId, UUID.randomUUID(), "correlation-123", "GRANTED", "STANDARD", "en", "whisper-large-v3", Instant.now()
        );
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("submitJob fails when producer has not started")
        void submitJobFailsWhenNotStarted() {
            TranscriptionJobProducer producer = new TranscriptionJobProducer("av.jobs", producerStrategy, metricsCollector);

            var promise = producer.submitJob(testJob("tenant-1"));

            assertThat(promise.getException()).isInstanceOf(IllegalStateException.class);
            assertThat(promise.getException()).hasMessageContaining("Producer not started");
        }

        @Test
        @DisplayName("start then submitJob delegates to producer strategy with 3-arg send")
        void startAndSubmitDelegatesToStrategy() {
            TranscriptionJobProducer producer = new TranscriptionJobProducer("av.jobs", producerStrategy, metricsCollector);
            when(producerStrategy.start()).thenReturn(Promise.complete());
            when(producerStrategy.send(any(String.class), any(String.class), anyMap())).thenReturn(Promise.of("msg-1"));

            producer.start().getResult();

            TranscriptionJobProducer.TranscriptionJobMessage job = testJob("tenant-1");

            String messageId = producer.submitJob(job).getResult();

            assertThat(messageId).isEqualTo("msg-1");
            verify(producerStrategy).send(eq(job.jobId().toString()), any(String.class), anyMap());
            verify(metricsCollector).incrementCounter(
                "av.messaging.jobs.submitted", "queue", "av.jobs", "tenant_id", "tenant-1");
        }
    }

    @Nested
    @DisplayName("Correlation ID propagation")
    class CorrelationIdTests {

        @Test
        @DisplayName("X-Correlation-ID header is forwarded when correlationId is in MDC")
        void correlationIdHeaderPropagatedFromMdc() {
            TranscriptionJobProducer producer = new TranscriptionJobProducer("av.jobs", producerStrategy, metricsCollector);
            when(producerStrategy.start()).thenReturn(Promise.complete());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
            when(producerStrategy.send(any(), any(), headersCaptor.capture())).thenReturn(Promise.of("msg-2"));

            producer.start().getResult();
            MDC.put("correlationId", "trace-abc-123");

            producer.submitJob(testJob("tenant-2")).getResult();

            assertThat(headersCaptor.getValue())
                .containsEntry(TranscriptionJobProducer.HEADER_CORRELATION_ID, "trace-abc-123");
        }

        @Test
        @DisplayName("X-Correlation-ID header is absent when correlationId is not in MDC")
        void correlationIdHeaderAbsentWhenNoMdcEntry() {
            TranscriptionJobProducer producer = new TranscriptionJobProducer("av.jobs", producerStrategy, metricsCollector);
            when(producerStrategy.start()).thenReturn(Promise.complete());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
            when(producerStrategy.send(any(), any(), headersCaptor.capture())).thenReturn(Promise.of("msg-3"));

            producer.start().getResult();
            // MDC has no correlationId — nothing set

            producer.submitJob(testJob("tenant-3")).getResult();

            assertThat(headersCaptor.getValue())
                .doesNotContainKey(TranscriptionJobProducer.HEADER_CORRELATION_ID);
        }
    }
}


