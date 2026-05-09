package com.ghatana.aiplatform.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Default implementation of {@link ExperimentalTrafficAllocationPort} using
 * the platform {@link ABTestingService}.
 *
 * <p>This implementation enables the platform to provide A/B testing and traffic
 * allocation capabilities to products while maintaining a clean port-based interface.
 *
 * @doc.type class
 * @doc.purpose Default implementation: Platform ABTestingService → ExperimentalTrafficAllocationPort
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class DefaultExperimentalTrafficAllocationAdapter implements ExperimentalTrafficAllocationPort {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExperimentalTrafficAllocationAdapter.class);

    private final ABTestingService abTesting;

    public DefaultExperimentalTrafficAllocationAdapter(ABTestingService abTesting) {
        this.abTesting = Objects.requireNonNull(abTesting, "abTesting required");
    }

    @Override
    public void registerExperiment(String tenantId, String experimentId, String name,
                                  String trafficSplit, String baselineModel, String variantModel) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experimentId, "experimentId required");
        Objects.requireNonNull(trafficSplit, "trafficSplit required");
        Objects.requireNonNull(baselineModel, "baselineModel required");
        Objects.requireNonNull(variantModel, "variantModel required");

        ABTestingService.Experiment experiment = new ABTestingService.Experiment(
            experimentId,
            name != null ? name : experimentId,
            trafficSplit,
            baselineModel,
            variantModel
        );
        abTesting.registerExperiment(tenantId, experiment);
    }

    @Override
    public String assignVariant(String tenantId, String experimentId, String requestContext) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experimentId, "experimentId required");
        Objects.requireNonNull(requestContext, "requestContext required");

        return abTesting.assignVariant(tenantId, experimentId, requestContext);
    }

    @Override
    public void endExperiment(String tenantId, String experimentId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experimentId, "experimentId required");

        abTesting.endExperiment(tenantId, experimentId);
    }
}
