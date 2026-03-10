package com.ghatana.yappc.services.domain;

import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.shape.ShapeService;
import io.activej.promise.Promise;
import java.util.Objects;

/**
 * Facade aggregating the core domain services of the YAPPC platform.
 *
 * <p>Provides a unified entry point for domain health checks and aggregate
 * metrics such as total entity counts.
 *
 * @doc.type class
 * @doc.purpose Aggregate facade for YAPPC domain services
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class DomainServiceFacade {

    private final IntentService intentService;
    private final ShapeService shapeService;

    public DomainServiceFacade(IntentService intentService, ShapeService shapeService) {
        this.intentService = Objects.requireNonNull(intentService, "intentService");
        this.shapeService = Objects.requireNonNull(shapeService, "shapeService");
    }

    /**
     * Returns a simple health status string.
     *
     * @return promise of {@code "OK"}
     */
    public Promise<String> healthCheck() {
        return Promise.of("OK");
    }

    /**
     * Returns the total number of domain entities (intents + shapes).
     *
     * @return promise of the aggregate entity count
     */
    public Promise<Long> entityCount() {
        return intentService.count()
                .combine(shapeService.count(), Long::sum);
    }
}
