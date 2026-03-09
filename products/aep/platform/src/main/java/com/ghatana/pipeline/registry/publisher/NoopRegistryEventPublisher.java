package com.ghatana.pipeline.registry.publisher;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.pipeline.registry.model.Pattern;
import com.ghatana.pipeline.registry.model.Pipeline;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * No-op implementation of RegistryEventPublisher for testing.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a test/development publisher that logs events without actually
 * sending to EventCloud. Useful for integration testing and development.
 *
 * <p>
 * <b>Behavior</b><br>
 * - Logs all events at DEBUG level with event type and metadata - Emits metrics
 * for event publication attempts - Returns completed Promise immediately
 * (non-blocking)
 *
 * <p>
 * <b>Production Note</b><br>
 * Replace with EventCloud-backed publisher for production deployments.
 *
 * @doc.type class
 * @doc.purpose No-op registry event publisher implementation
 * @doc.layer product
 * @doc.pattern Publisher Implementation
 */
@Slf4j
public class NoopRegistryEventPublisher implements RegistryEventPublisher {

    private final MetricsCollector metricsCollector;

    public NoopRegistryEventPublisher(MeterRegistry meterRegistry) {
        this.metricsCollector = MetricsCollectorFactory.create(meterRegistry);
    }

    @Override
    public Promise<Void> publishPatternRegistered(Pattern pattern, String userId) {
        log.debug("Event published: pattern.registered (patternId={}, tenantId={}, status={})",
                pattern.getId(), pattern.getTenantId().value(), pattern.getStatus());
        metricsCollector.incrementCounter("registry.event.publish.count",
                "event_type", "pattern.registered",
                "tenant", pattern.getTenantId().value());
        return Promise.complete();
    }

    @Override
    public Promise<Void> publishPatternActivated(Pattern pattern, String userId) {
        log.debug("Event published: pattern.activated (patternId={}, tenantId={})",
                pattern.getId(), pattern.getTenantId().value());
        metricsCollector.incrementCounter("registry.event.publish.count",
                "event_type", "pattern.activated",
                "tenant", pattern.getTenantId().value());
        return Promise.complete();
    }

    @Override
    public Promise<Void> publishPatternDeactivated(Pattern pattern, String userId) {
        log.debug("Event published: pattern.deactivated (patternId={}, tenantId={})",
                pattern.getId(), pattern.getTenantId().value());
        metricsCollector.incrementCounter("registry.event.publish.count",
                "event_type", "pattern.deactivated",
                "tenant", pattern.getTenantId().value());
        return Promise.complete();
    }

    @Override
    public Promise<Void> publishPipelineRegistered(Pipeline pipeline, String userId) {
        log.debug("Event published: pipeline.registered (pipelineId={}, tenantId={})",
                pipeline.getId(), pipeline.getTenantId().value());
        metricsCollector.incrementCounter("registry.event.publish.count",
                "event_type", "pipeline.registered",
                "tenant", pipeline.getTenantId().value());
        return Promise.complete();
    }

    @Override
    public Promise<Void> publishPipelineActivated(Pipeline pipeline, String userId) {
        log.debug("Event published: pipeline.activated (pipelineId={}, tenantId={})",
                pipeline.getId(), pipeline.getTenantId().value());
        metricsCollector.incrementCounter("registry.event.publish.count",
                "event_type", "pipeline.activated",
                "tenant", pipeline.getTenantId().value());
        return Promise.complete();
    }

    @Override
    public Promise<Void> publishPipelineDeactivated(Pipeline pipeline, String userId) {
        log.debug("Event published: pipeline.deactivated (pipelineId={}, tenantId={})",
                pipeline.getId(), pipeline.getTenantId().value());
        metricsCollector.incrementCounter("registry.event.publish.count",
                "event_type", "pipeline.deactivated",
                "tenant", pipeline.getTenantId().value());
        return Promise.complete();
    }
}
