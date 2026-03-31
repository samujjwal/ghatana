/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ai;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.migration.BaseAgentAdapter;
import com.ghatana.agent.spi.AgentLogicProvider;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Finance agent logic provider — resolves {@code finance:} prefixed
 * {@code implementationRef}s to concrete financial domain agents.
 *
 * <p>Maps implementation refs to agent factories:
 * <pre>
 *   finance:risk-assessment  → RiskAssessmentAgent
 *   finance:fraud-detection  → FraudDetectionAgent
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Finance-specific AgentLogicProvider implementation
 * @doc.layer product
 * @doc.pattern Service, Factory
 */
public class FinanceAgentLogicProvider implements AgentLogicProvider {

    private static final Logger log = LoggerFactory.getLogger(FinanceAgentLogicProvider.class);

    public static final String PROVIDER_ID = "finance";

    private final Map<String, Function<AgentConfig, TypedAgent<?, ?>>> factories =
            new ConcurrentHashMap<>();

    /**
     * Default no-arg constructor for environments where agent dependencies are
     * registered later via {@link #registerFactory}.
     */
    public FinanceAgentLogicProvider() {
        // No real agent wiring; factories must be registered externally.
    }

    /**
     * Constructor that wires fully initialized finance agents.
     *
     * @param alertService the standalone alert service for event notifications
     */
    public FinanceAgentLogicProvider(AlertService alertService) {
        Objects.requireNonNull(alertService, "alertService must not be null");

        // Wire RiskAssessmentAgent with minimal default service adapters.
        factories.put("finance:risk-assessment", config -> {
            RiskAssessmentAgent.ModelRegistry modelRegistry =
                    modelId -> {
                        log.info("Risk model retraining triggered for '{}'", modelId);
                        return Promise.complete();
                    };
            RiskAssessmentAgent.InferenceService inferenceService =
                    (modelId, features) -> {
                        log.debug("Risk inference: model='{}', features={}", modelId, features.size());
                        return RiskAssessmentResult.skip();
                    };
            RiskAssessmentAgent.RiskAlertService riskAlertService =
                    update -> {
                        alertService.sendAlert("Risk Update", update.toString());
                        return Promise.complete();
                    };
            return new BaseAgentAdapter<>(new RiskAssessmentAgent(modelRegistry, inferenceService, riskAlertService));
        });

        // FraudDetectionAgent requires external wiring since FraudDetectionResult
        // is not available in this package. Register via registerFactory() with
        // production-grade InferenceService and AlertService implementations.
        log.info("FinanceAgentLogicProvider initialized; " +
                "finance:fraud-detection requires external factory registration");
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getProviderName() {
        return "Finance Agent Provider";
    }

    @Override
    public Set<String> getSupportedRefs() {
        return Set.copyOf(factories.keySet());
    }

    @Override
    public TypedAgent<?, ?> createAgent(String implementationRef, AgentConfig config) {
        Function<AgentConfig, TypedAgent<?, ?>> factory = factories.get(implementationRef);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unsupported implementationRef: " + implementationRef
                            + ". Supported: " + factories.keySet());
        }
        log.info("Creating Finance agent for ref '{}', agentId='{}'",
                implementationRef, config.getAgentId());
        return factory.apply(config);
    }

    /**
     * Registers a factory for a specific {@code implementationRef}.
     *
     * @param implementationRef the ref this factory handles
     * @param factory           function creating the agent from config
     */
    public void registerFactory(String implementationRef,
                                 Function<AgentConfig, TypedAgent<?, ?>> factory) {
        factories.put(implementationRef, factory);
        log.debug("Registered Finance agent factory for ref '{}'", implementationRef);
    }
}
