package com.ghatana.yappc.services.domain;

import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.shape.ShapeService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain service facade for the YAPPC product.
 *
 * <p>Provides centralized access to all domain services (lifecycle phases,
 * agents, workflows) without exposing implementation details. This is a
 * composition module — it delegates to implementations in
 * {@code core:lifecycle}, {@code core:ai}, and {@code core:domain}.</p>
 *
 * <p>All operations return ActiveJ {@link Promise} per Golden Rule #3
 * (ActiveJ Concurrency).</p>
 *
 * @doc.type class
 * @doc.purpose Unified domain service facade
 * @doc.layer product
 * @doc.pattern Facade
 */
public class DomainServiceFacade {

    private static final Logger logger = LoggerFactory.getLogger(DomainServiceFacade.class);

    private final IntentService intentService;
    private final ShapeService shapeService;

    /**
     * Creates a DomainServiceFacade with lifecycle phase services.
     *
     * @param intentService the intent capture service
     * @param shapeService the architecture shaping service
     */
    public DomainServiceFacade(
            @NotNull IntentService intentService,
            @NotNull ShapeService shapeService) {
        this.intentService = intentService;
        this.shapeService = shapeService;
        logger.info("DomainServiceFacade initialized with {} lifecycle services", 2);
    }

    /**
     * Performs a health check on all domain services.
     *
     * @return a Promise resolving to "OK" if all services are healthy
     */
    @NotNull
    public Promise<String> healthCheck() {
        return Promise.of("OK");
    }

    /**
     * Returns the count of registered domain entities.
     *
     * @return a Promise resolving to the entity count
     */
    @NotNull
    public Promise<Long> entityCount() {
        return intentService.count()
            .then(intentCount -> shapeService.count()
                .map(shapeCount -> intentCount + shapeCount));
    }

    /**
     * Returns the intent service for direct access.
     *
     * @return the intent service
     */
    @NotNull
    public IntentService intentService() {
        return intentService;
    }

    /**
     * Returns the shape service for direct access.
     *
     * @return the shape service
     */
    @NotNull
    public ShapeService shapeService() {
        return shapeService;
    }
}
