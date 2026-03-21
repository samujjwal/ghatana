package com.ghatana.eventprocessing.client;

import com.ghatana.platform.observability.NoopMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

/**
 * Observability handler for RealEventCloudClient operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes metrics collection and logging for EventCloud client operations
 * (append, subscribe, metadata queries). Tracks success/failure rates, latency,
 * batch sizes, and connection health.
 *
 * <p>
 * <b>Metrics Emitted</b>
 * - aep.eventcloud.client.append.* (counter, latency) -
 * aep.eventcloud.client.batch.* (counter, latency, size) -
 * aep.eventcloud.client.subscription.* (attempts, active, errors) -
 * aep.eventcloud.client.metadata (queries) - aep.eventcloud.client.reconnect.*
 * (attempts, success)
 *
 * @doc.type class
 * @doc.purpose Observability for RealEventCloudClient operations
 * @doc.layer product
 * @doc.pattern Observability
 */
@Slf4j
public class RealEventCloudClientObservability {

    private final MeterRegistry meterRegistry;

    public RealEventCloudClientObservability() {
        this.meterRegistry = NoopMetricsCollector.getInstance().getMeterRegistry();
    }

    public RealEventCloudClientObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : NoopMetricsCollector.getInstance().getMeterRegistry();
    }

    /**
     * Records start of append operation.
     *
     * @param streamName the stream name
     * @param tenantId the tenant ID
     */
    public void recordAppendStart(String streamName, String tenantId) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter(
                            "aep.eventcloud.client.append.attempts",
                            "stream",
                            streamName,
                            "tenant",
                            tenantId)
                    .increment();
        }
    }

    /**
     * Records successful append operation.
     *
     * @param streamName the stream name
     * @param tenantId the tenant ID
     * @param latencyMs latency in milliseconds
     */
    public void recordAppendSuccess(String streamName, String tenantId, long latencyMs) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter(
                            "aep.eventcloud.client.append.success",
                            "stream",
                            streamName,
                            "tenant",
                            tenantId)
                    .increment();
            meterRegistry
                    .timer("aep.eventcloud.client.append.latency", "stream", streamName, "tenant", tenantId)
                    .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Records failed append operation.
     *
     * @param streamName the stream name
     * @param tenantId the tenant ID
     * @param exception the exception that occurred
     */
    public void recordAppendError(String streamName, String tenantId, Throwable exception) {
        if (meterRegistry != null) {
            String errorType = exception.getClass().getSimpleName();
            meterRegistry
                    .counter(
                            "aep.eventcloud.client.append.errors",
                            "stream",
                            streamName,
                            "tenant",
                            tenantId,
                            "error_type",
                            errorType)
                    .increment();
        }
        log.warn(
                "Append error - stream: {}, tenant: {}, error: {}",
                streamName,
                tenantId,
                exception.getMessage(),
                exception);
    }

    /**
     * Records start of batch append operation.
     *
     * @param streamName the stream name
     * @param tenantId the tenant ID
     * @param eventCount number of events in batch
     */
    public void recordBatchAppendStart(String streamName, String tenantId, int eventCount) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter(
                            "aep.eventcloud.client.batch.append.attempts",
                            "stream",
                            streamName,
                            "tenant",
                            tenantId)
                    .increment();
            meterRegistry.gauge("aep.eventcloud.client.batch.size", 
                    Tags.of("stream", streamName, "tenant", tenantId), 
                    new java.util.concurrent.atomic.AtomicInteger(eventCount),
                    java.util.concurrent.atomic.AtomicInteger::get);
        }
    }

    /**
     * Records successful batch append.
     *
     * @param streamName the stream name
     * @param tenantId the tenant ID
     * @param eventCount number of events appended
     * @param latencyMs latency in milliseconds
     */
    public void recordBatchAppendSuccess(String streamName, String tenantId, int eventCount, long latencyMs) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter(
                            "aep.eventcloud.client.batch.success",
                            "stream",
                            streamName,
                            "tenant",
                            tenantId)
                    .increment();
            meterRegistry
                    .counter(
                            "aep.eventcloud.client.batch.events_appended",
                            "stream",
                            streamName,
                            "tenant",
                            tenantId)
                    .increment(eventCount);
            meterRegistry
                    .timer("aep.eventcloud.client.batch.latency", "stream", streamName, "tenant", tenantId)
                    .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Records failed batch append.
     *
     * @param streamName the stream name
     * @param tenantId the tenant ID
     * @param eventCount number of events that failed
     * @param exception the exception that occurred
     */
    public void recordBatchAppendError(
            String streamName, String tenantId, int eventCount, Throwable exception) {
        if (meterRegistry != null) {
            String errorType = exception.getClass().getSimpleName();
            meterRegistry
                    .counter(
                            "aep.eventcloud.client.batch.errors",
                            "stream",
                            streamName,
                            "tenant",
                            tenantId,
                            "error_type",
                            errorType)
                    .increment();
        }
        log.warn(
                "Batch append error - stream: {}, tenant: {}, events: {}, error: {}",
                streamName,
                tenantId,
                eventCount,
                exception.getMessage(),
                exception);
    }

    /**
     * Records start of subscription.
     *
     * @param streamName the stream name
     * @param startOffset the start offset
     */
    public void recordSubscriptionStart(String streamName, long startOffset) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter("aep.eventcloud.client.subscription.attempts", "stream", streamName)
                    .increment();
            meterRegistry.gauge("aep.eventcloud.client.subscription.active",
                    Tags.of("stream", streamName),
                    new java.util.concurrent.atomic.AtomicInteger(1),
                    java.util.concurrent.atomic.AtomicInteger::get);
        }
    }

    /**
     * Records successful subscription.
     *
     * @param streamName the stream name
     */
    public void recordSubscriptionSuccess(String streamName) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter("aep.eventcloud.client.subscription.success", "stream", streamName)
                    .increment();
        }
    }

    /**
     * Records subscription error.
     *
     * @param streamName the stream name
     * @param exception the exception that occurred
     */
    public void recordSubscriptionError(String streamName, Throwable exception) {
        if (meterRegistry != null) {
            String errorType = exception.getClass().getSimpleName();
            meterRegistry
                    .counter(
                            "aep.eventcloud.client.subscription.errors",
                            "stream",
                            streamName,
                            "error_type",
                            errorType)
                    .increment();
        }
        log.warn("Subscription error - stream: {}, error: {}", streamName, exception.getMessage(), exception);
    }

    /**
     * Records reconnection attempt.
     *
     * @param attemptNumber the attempt number (1-based)
     */
    public void recordReconnectionAttempt(int attemptNumber) {
        if (meterRegistry != null) {
            meterRegistry.counter("aep.eventcloud.client.reconnect.attempts").increment();
        }
    }

    /**
     * Records successful reconnection.
     *
     * @param streamName the stream name
     */
    public void recordReconnectionSuccess(String streamName) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter("aep.eventcloud.client.reconnect.success", "stream", streamName)
                    .increment();
        }
    }

    /**
     * Records metadata query.
     *
     * @param streamName the stream name
     */
    public void recordMetadataQuery(String streamName) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter("aep.eventcloud.client.metadata.queries", "stream", streamName)
                    .increment();
        }
    }

    /**
     * Records client closure.
     */
    public void recordClientClose() {
        if (meterRegistry != null) {
            meterRegistry.counter("aep.eventcloud.client.close").increment();
        }
    }
}
