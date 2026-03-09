package com.ghatana.orchestrator.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Deployment event publisher backed by the AEP EventCloud facade.
 */
@Slf4j
public class EventCloudDeploymentEventPublisher implements DeploymentEventPublisher {

    private final EventCloud eventCloud;
    private final ObjectMapper objectMapper;

    public EventCloudDeploymentEventPublisher(EventCloud eventCloud) {
        this(eventCloud, JsonUtils.getDefaultMapper());
    }

    public EventCloudDeploymentEventPublisher(EventCloud eventCloud, ObjectMapper objectMapper) {
        this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper required");
    }

    @Override
    public Promise<Void> publishDeploymentEvent(String eventType, String deploymentId, DeploymentRequest request) {
        try {
            Map<String, Object> payload = Map.of(
                "type", eventType,
                "deploymentId", deploymentId,
                "tenantId", request.getTenantId(),
                "pipelineId", request.getPipelineId(),
                "environment", request.getEnvironment(),
                "deploymentOptions", request.getDeploymentOptions() == null ? Map.of() : request.getDeploymentOptions(),
                "timestamp", Instant.now().toString()
            );

            byte[] eventBytes = objectMapper.writeValueAsBytes(payload);
            eventCloud.append(request.getTenantId(), eventType, eventBytes);
            return Promise.complete();
        } catch (Exception e) {
            log.error("Failed to publish deployment event: type={}, deploymentId={}", eventType, deploymentId, e);
            return Promise.ofException(e);
        }
    }
}

