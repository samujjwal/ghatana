package com.ghatana.datacloud.client;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared lifecycle support for launcher-side Data Cloud clients.
 *
 * @doc.type class
 * @doc.purpose Provides shared running state and simple status helpers for launcher clients
 * @doc.layer core
 * @doc.pattern Base Class
 */
abstract class ManagedDataCloudClient implements DataCloudClient {

    private final AtomicBoolean running = new AtomicBoolean(true);

    @Override
    public boolean isRunning() {
        return running.get();
    }

    protected final void requireRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("Client is closed");
        }
    }

    protected final boolean markClosed() {
        return running.compareAndSet(true, false);
    }

    protected final HealthStatus closedHealthStatus() {
        return new SimpleHealthStatus(false, Map.of(), "Client closed");
    }

    protected final ComponentStatus componentStatus(String name, boolean healthy, String status) {
        return new SimpleComponentStatus(name, healthy, status);
    }

    protected final SystemMetrics systemMetrics(long requestCount, double averageLatencyMs,
            double errorRate, Map<String, Long> metricsByOperation) {
        return new SimpleSystemMetrics(requestCount, averageLatencyMs, errorRate, metricsByOperation);
    }

    private record SimpleHealthStatus(
        boolean healthy,
        Map<String, ComponentStatus> components,
        String message
    ) implements HealthStatus {
        @Override
        public boolean isHealthy() { return healthy; }

        @Override
        public Map<String, ComponentStatus> getComponents() { return components; }

        @Override
        public String getMessage() { return message; }
    }

    private record SimpleComponentStatus(
        String name,
        boolean healthy,
        String status
    ) implements ComponentStatus {
        @Override
        public String getName() { return name; }

        @Override
        public boolean isHealthy() { return healthy; }

        @Override
        public String getStatus() { return status; }
    }

    private record SimpleSystemMetrics(
        long requestCount,
        double averageLatencyMs,
        double errorRate,
        Map<String, Long> metricsByOperation
    ) implements SystemMetrics {
        @Override
        public long getRequestCount() { return requestCount; }

        @Override
        public double getAverageLatencyMs() { return averageLatencyMs; }

        @Override
        public double getErrorRate() { return errorRate; }

        @Override
        public Map<String, Long> getMetricsByOperation() { return metricsByOperation; }
    }
}