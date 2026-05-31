package com.ghatana.audio.video.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.messaging.strategy.QueueProducerStrategy;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @doc.type class
 * @doc.purpose Producer for transcription job messages using platform messaging
 * @doc.layer infrastructure
 * @doc.pattern Producer
 */
public class TranscriptionJobProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger(TranscriptionJobProducer.class);
    
    private enum ProducerState {
        CREATED, STARTED, STOPPED
    }
    
    private final String queueName;
    private final QueueProducerStrategy producerStrategy;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;
    private final AtomicReference<ProducerState> state = new AtomicReference<>(ProducerState.CREATED);

    public TranscriptionJobProducer(String queueName,
                                    QueueProducerStrategy producerStrategy,
                                    MetricsCollector metricsCollector) {
        this(queueName, producerStrategy, metricsCollector, createDefaultObjectMapper());
    }

    public TranscriptionJobProducer(String queueName,
                                    QueueProducerStrategy producerStrategy,
                                    MetricsCollector metricsCollector,
                                    ObjectMapper objectMapper) {
        this.queueName = Objects.requireNonNull(queueName, "queueName cannot be null");
        this.producerStrategy = Objects.requireNonNull(producerStrategy, "producerStrategy cannot be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    /**
     * Start the producer
     */
    public Promise<Void> start() {
        if (!state.compareAndSet(ProducerState.CREATED, ProducerState.STARTED)) {
            LOG.warn("Producer already started or stopped");
            return Promise.complete();
        }
        
        return producerStrategy.start()
            .whenResult(v -> {
                LOG.info("TranscriptionJobProducer started for queue: {}", queueName);
                metricsCollector.incrementCounter("av.messaging.producer.start",
                    "queue", queueName);
            })
            .whenException(e -> {
                LOG.error("Failed to start producer: {}", queueName, e);
                state.set(ProducerState.CREATED);
            });
    }
    
    /**
     * Stop the producer
     */
    public Promise<Void> stop() {
        if (!state.compareAndSet(ProducerState.STARTED, ProducerState.STOPPED)) {
            LOG.warn("Producer not in STARTED state: {}", state.get());
            return Promise.complete();
        }
        
        return producerStrategy.stop()
            .whenResult(v -> {
                LOG.info("TranscriptionJobProducer stopped for queue: {}", queueName);
                metricsCollector.incrementCounter("av.messaging.producer.stop",
                    "queue", queueName);
            });
    }
    
    /**
     * Header name for correlation ID propagation across service boundaries.
     */
    static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

    /**
     * Submit a transcription job
     */
    public Promise<String> submitJob(TranscriptionJobMessage job) {
        Objects.requireNonNull(job, "job cannot be null");
        
        if (state.get() != ProducerState.STARTED) {
            return Promise.ofException(new IllegalStateException("Producer not started"));
        }
        
        try (MDC.MDCCloseable ignored = MDC.putCloseable("jobId", job.jobId().toString())) {
            LOG.debug("Submitting transcription job: {}", job.jobId());
            
            long startTime = System.currentTimeMillis();
            String payload = serializeJob(job);

            Map<String, String> headers = buildHeaders();
            
            return producerStrategy.send(job.jobId().toString(), payload, headers)
                .map(messageId -> {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    metricsCollector.incrementCounter("av.messaging.jobs.submitted",
                        "queue", queueName,
                        "tenant_id", job.tenantId());
                    metricsCollector.recordTimer("av.messaging.submit.latency_ms",
                        latencyMs,
                        "queue", queueName);
                    
                    LOG.info("Transcription job submitted: jobId={}, messageId={}", 
                        job.jobId(), messageId);
                    return messageId;
                })
                .whenException(e -> {
                    metricsCollector.incrementCounter("av.messaging.jobs.failed",
                        "queue", queueName,
                        "phase", "submit");
                    LOG.error("Failed to submit transcription job: {}", job.jobId(), e);
                });
        }
    }

    /**
     * Build outbound message headers, propagating correlation ID from the current MDC context.
     * Callers that set {@code correlationId} in MDC before invoking {@link #submitJob} will
     * have the value forwarded to the message broker so downstream consumers can join traces.
     */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            headers.put(HEADER_CORRELATION_ID, correlationId);
        }
        return Map.copyOf(headers);
    }
    
    /**
     * Check if producer is healthy
     */
    public boolean isHealthy() {
        if (state.get() != ProducerState.STARTED) {
            return false;
        }
        return producerStrategy.getStatus() == QueueProducerStrategy.ProducerStatus.RUNNING;
    }
    
    private String serializeJob(TranscriptionJobMessage job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize TranscriptionJobMessage: {}", job.jobId(), e);
            throw new RuntimeException("Failed to serialize job message: " + e.getMessage(), e);
        }
    }
    
    /**
     * Transcription job message
     *
     * K3: Enhanced with Data Cloud integration fields for media processing events
     */
    public record TranscriptionJobMessage(
        UUID jobId,
        String tenantId,
        UUID artifactId,
        String correlationId,
        String consentStatus,
        String retentionPolicy,
        String language,
        String modelId,
        Instant submittedAt
    ) {
        public TranscriptionJobMessage {
            Objects.requireNonNull(jobId, "jobId cannot be null");
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(artifactId, "artifactId cannot be null");
            Objects.requireNonNull(submittedAt, "submittedAt cannot be null");
        }

        public static TranscriptionJobMessage create(String tenantId, UUID artifactId, String language) {
            return new TranscriptionJobMessage(
                UUID.randomUUID(),
                tenantId,
                artifactId,
                UUID.randomUUID().toString(),
                "GRANTED",
                "STANDARD",
                language,
                null,
                Instant.now()
            );
        }

        public static TranscriptionJobMessage createWithDataCloudMetadata(
                String tenantId,
                UUID artifactId,
                String correlationId,
                String consentStatus,
                String retentionPolicy,
                String language) {
            return new TranscriptionJobMessage(
                UUID.randomUUID(),
                tenantId,
                artifactId,
                correlationId,
                consentStatus,
                retentionPolicy,
                language,
                null,
                Instant.now()
            );
        }
    }
}
