package com.ghatana.pipeline.registry.config;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.eventprocessing.observability.EventPublisherObservability;
import com.ghatana.pipeline.registry.repository.InMemoryPatternRepository;
import com.ghatana.pipeline.registry.repository.PatternRepository;
import com.ghatana.pipeline.registry.publisher.EventCloudRegistryEventPublisher;
import com.ghatana.pipeline.registry.service.PatternRegistryService;
import com.ghatana.pipeline.registry.service.PatternService;
import com.ghatana.pipeline.registry.web.PatternController;
import com.ghatana.pipeline.registry.publisher.RegistryEventPublisher;
import com.ghatana.eventprocessing.observability.RegistryObservability;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Dependency injection configuration for pattern registry components.
 *
 * <p>
 * <b>Purpose</b><br>
 * Wires PatternRepository, PatternService, and PatternController dependencies
 * for ActiveJ dependency injection container.
 *
 * <p>
 * <b>Components</b><br>
 * - PatternRepository: In-memory storage for patterns - PatternService:
 * Business logic for pattern lifecycle management - PatternController: HTTP
 * endpoint handlers
 *
 * @doc.type class
 * @doc.purpose Dependency injection module for pattern registry
 * @doc.layer product
 * @doc.pattern Module
 */
public class PatternRegistryModule extends AbstractModule {

    /**
     * Provides PatternRepository implementation.
     *
     * @return in-memory repository instance
     */
    @Provides
    PatternRepository patternRepository() {
        return new InMemoryPatternRepository();
    }

    /**
     * Provides PatternService implementation.
     *
     * @param repository the pattern repository
     * @param meterRegistry the metrics registry
     * @param registryObservability the registry observability
     * @param eventPublisher the registry event publisher
     * @return service instance
     */
    @Provides
    PatternService patternService(
            PatternRepository repository,
            MeterRegistry meterRegistry,
            RegistryObservability registryObservability,
            RegistryEventPublisher eventPublisher) {
        MetricsCollector metricsCollector = MetricsCollectorFactory.create(meterRegistry);
        return new PatternRegistryService(repository, metricsCollector, registryObservability, eventPublisher);
    }

    /**
     * Provides PatternController.
     *
     * @param service the pattern service
     * @return controller instance
     */
    @Provides
    PatternController patternController(PatternService service) {
        return new PatternController(service);
    }

    @Provides
    RegistryEventPublisher registryEventPublisher(
            EventCloud eventCloud,
            MeterRegistry meterRegistry,
            EventPublisherObservability eventPublisherObservability) {
        return EventCloudRegistryEventPublisher.createWithAepEventCloud(
                eventCloud,
                "aep.pattern.registry",
                meterRegistry,
                eventPublisherObservability);
    }

    @Provides
    EventPublisherObservability eventPublisherObservability() {
        return new EventPublisherObservability();
    }
}
