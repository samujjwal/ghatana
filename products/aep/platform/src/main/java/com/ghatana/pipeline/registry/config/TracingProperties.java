package com.ghatana.pipeline.registry.config;

/**
 * Configuration properties for distributed tracing.
 *
 * <p>Purpose: Holds configuration values for OpenTelemetry tracing including
 * service name, exporter endpoint, and batch processing settings. Used to
 * configure trace collection and export behavior.</p>
 *
 * @doc.type class
 * @doc.purpose Configuration POJO for OpenTelemetry tracing settings
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class TracingProperties {
    private boolean enabled = true;
    private String serviceName = "pipeline-registry";
    private String exporterEndpoint = "http://localhost:4317";
    private int maxQueueSize = 2048;
    private int maxExportBatchSize = 512;
    private long exportTimeoutMs = 30000; // 30 seconds
    private long scheduleDelayMs = 100; // 100ms

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getExporterEndpoint() {
        return exporterEndpoint;
    }

    public void setExporterEndpoint(String exporterEndpoint) {
        this.exporterEndpoint = exporterEndpoint;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public int getMaxExportBatchSize() {
        return maxExportBatchSize;
    }

    public void setMaxExportBatchSize(int maxExportBatchSize) {
        this.maxExportBatchSize = maxExportBatchSize;
    }

    public long getExportTimeoutMs() {
        return exportTimeoutMs;
    }

    public void setExportTimeoutMs(long exportTimeoutMs) {
        this.exportTimeoutMs = exportTimeoutMs;
    }

    public long getScheduleDelayMs() {
        return scheduleDelayMs;
    }

    public void setScheduleDelayMs(long scheduleDelayMs) {
        this.scheduleDelayMs = scheduleDelayMs;
    }
}
