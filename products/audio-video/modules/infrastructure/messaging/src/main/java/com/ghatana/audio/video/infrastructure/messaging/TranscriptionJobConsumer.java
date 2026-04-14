package com.ghatana.audio.video.infrastructure.messaging;

import com.ghatana.platform.messaging.strategy.QueueConsumerStrategy;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * @doc.type class
 * @doc.purpose Consumer for transcription job messages using platform messaging
 * @doc.layer infrastructure
 * @doc.pattern Consumer
 */
public class TranscriptionJobConsumer {
    
    private static final Logger LOG = LoggerFactory.getLogger(TranscriptionJobConsumer.class);
    
    private enum ConsumerState {
        CREATED, STARTED, STOPPED
    }
    
    private final String queueName;
    private final QueueConsumerStrategy consumerStrategy;
    private final MetricsCollector metricsCollector;
    private final ExecutorService processingExecutor;
    private final AtomicReference<ConsumerState> state = new AtomicReference<>(ConsumerState.CREATED);
    private Function<TranscriptionJobProducer.TranscriptionJobMessage, Promise<Void>> jobProcessor;
    
    public TranscriptionJobConsumer(String queueName,
                                    QueueConsumerStrategy consumerStrategy,
                                    MetricsCollector metricsCollector) {
        this(queueName, consumerStrategy, metricsCollector, 
            Executors.newVirtualThreadPerTaskExecutor());
    }
    
    public TranscriptionJobConsumer(String queueName,
                                    QueueConsumerStrategy consumerStrategy,
                                    MetricsCollector metricsCollector,
                                    ExecutorService processingExecutor) {
        this.queueName = Objects.requireNonNull(queueName, "queueName cannot be null");
        this.consumerStrategy = Objects.requireNonNull(consumerStrategy, "consumerStrategy cannot be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector cannot be null");
        this.processingExecutor = Objects.requireNonNull(processingExecutor, "processingExecutor cannot be null");
    }
    
    /**
     * Set the job processor function
     */
    public void setJobProcessor(Function<TranscriptionJobProducer.TranscriptionJobMessage, Promise<Void>> processor) {
        this.jobProcessor = processor;
    }
    
    /**
     * Start consuming messages
     */
    public Promise<Void> start() {
        if (!state.compareAndSet(ConsumerState.CREATED, ConsumerState.STARTED)) {
            LOG.warn("Consumer already started or stopped");
            return Promise.complete();
        }
        
        if (jobProcessor == null) {
            return Promise.ofException(new IllegalStateException("Job processor not set"));
        }
        
        return consumerStrategy.start((key, payload) -> {
            // Process message
            return processMessage(key, payload);
        })
        .whenResult(v -> {
            LOG.info("TranscriptionJobConsumer started for queue: {}", queueName);
            metricsCollector.incrementCounter("av.messaging.consumer.start",
                "queue", queueName);
        })
        .whenException(e -> {
            LOG.error("Failed to start consumer: {}", queueName, e);
            state.set(ConsumerState.CREATED);
        });
    }
    
    /**
     * Stop consuming messages
     */
    public Promise<Void> stop() {
        if (!state.compareAndSet(ConsumerState.STARTED, ConsumerState.STOPPED)) {
            LOG.warn("Consumer not in STARTED state: {}", state.get());
            return Promise.complete();
        }
        
        return consumerStrategy.stop()
            .whenResult(v -> {
                LOG.info("TranscriptionJobConsumer stopped for queue: {}", queueName);
                metricsCollector.incrementCounter("av.messaging.consumer.stop",
                    "queue", queueName);
                processingExecutor.shutdown();
            });
    }
    
    private Promise<Void> processMessage(String key, String payload) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Parse the job message
            TranscriptionJobProducer.TranscriptionJobMessage job = parseJob(payload);
            
            try (MDC.MDCCloseable ignored = MDC.putCloseable("jobId", job.jobId().toString())) {
                LOG.debug("Processing transcription job: {}", job.jobId());
                
                return jobProcessor.apply(job)
                    .whenResult(v -> {
                        long latencyMs = System.currentTimeMillis() - startTime;
                        metricsCollector.incrementCounter("av.messaging.jobs.processed",
                            "queue", queueName,
                            "tenant_id", job.tenantId(),
                            "status", "success");
                        metricsCollector.recordTimer("av.messaging.process.latency_ms",
                            latencyMs,
                            "queue", queueName);
                        
                        LOG.info("Transcription job completed: jobId={}", job.jobId());
                    })
                    .whenException(e -> {
                        metricsCollector.incrementCounter("av.messaging.jobs.failed",
                            "queue", queueName,
                            "tenant_id", job.tenantId(),
                            "phase", "process");
                        LOG.error("Failed to process transcription job: {}", job.jobId(), e);
                    });
            }
        } catch (Exception e) {
            LOG.error("Failed to parse message: key={}", key, e);
            metricsCollector.incrementCounter("av.messaging.jobs.failed",
                "queue", queueName,
                "phase", "parse");
            return Promise.ofException(e);
        }
    }
    
    private TranscriptionJobProducer.TranscriptionJobMessage parseJob(String payload) {
        // Simple parsing - in production use Jackson
        // This is a simplified version for demonstration
        throw new UnsupportedOperationException("JSON parsing not implemented - use Jackson ObjectMapper");
    }
    
    /**
     * Check if consumer is healthy
     */
    public boolean isHealthy() {
        if (state.get() != ConsumerState.STARTED) {
            return false;
        }
        return consumerStrategy.getStatus() == QueueConsumerStrategy.ConsumerStatus.RUNNING;
    }
}
