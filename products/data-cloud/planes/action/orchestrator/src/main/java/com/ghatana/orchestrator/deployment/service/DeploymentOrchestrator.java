package com.ghatana.orchestrator.deployment.service;

import com.ghatana.orchestrator.deployment.contract.DeploymentEventType;
import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import com.ghatana.orchestrator.deployment.contract.DeploymentResponse;
import com.ghatana.orchestrator.deployment.http.KernelLifecycleClient;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Service for orchestrating pipeline deployments.
 *
 * <p><b>Purpose</b><br>
 * Validates deployment requests, generates unique deployment IDs, submits governed Kernel lifecycle
 * action requests, and publishes deployment events to EventCloud. Acts as orchestrator entry point
 * for all deployment operations (deploy, update, undeploy). All lifecycle actions go through the
 * governed Kernel system with proper adapter contract compliance.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(
 *     deploymentEventPublisher, metricsCollector, kernelLifecycleClient);
 *
 * DeploymentRequest request = DeploymentRequest.builder()
 *     .pipelineId("fraud-detection")
 *     .tenantId("acme-corp")
 *     .environment("production")
 *     .build();
 *
 * Promise<DeploymentResponse> result = orchestrator.requestDeployment(request);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Deployment orchestration service with governed Kernel lifecycle integration
 * @doc.layer product
 * @doc.pattern Service
 */
@Slf4j
@RequiredArgsConstructor
public class DeploymentOrchestrator {
    private final DeploymentEventPublisher eventPublisher;
    private final MetricsCollector metricsCollector;
    @Nullable
    private final KernelLifecycleClient kernelLifecycleClient;

    /**
     * Request pipeline deployment.
     *
     * <p>Validates request, generates deployment ID, submits governed Kernel lifecycle action request,
     * and publishes PIPELINE_DEPLOY_REQUESTED event for event-processing instances to consume.
     * Returns immediately with deployment ID (async processing via event).
     *
     * @param request deployment request with pipelineId, tenantId, environment
     * @return Promise<DeploymentResponse> with deploymentId and status
     */
    public Promise<DeploymentResponse> requestDeployment(DeploymentRequest request) {
        MDC.put("pipelineId", request.getPipelineId());
        MDC.put("tenantId", request.getTenantId());

        try {
            // Validate request
            if (!request.isValid()) {
                log.warn("Invalid deployment request: missing required fields");
                metricsCollector.incrementCounter(
                        "aep.deployment.request.validation_error",
                        "pipeline_id",
                        request.getPipelineId(),
                        "tenant_id",
                        request.getTenantId());

                return Promise.of(DeploymentResponse.builder()
                        .deploymentId("invalid")
                        .pipelineId(request.getPipelineId())
                        .tenantId(request.getTenantId())
                        .status("FAILED")
                        .error("Invalid deployment request: missing required fields")
                        .timestamp(Instant.now().toString())
                        .build());
            }

            // Generate deployment ID
            String deploymentId = UUID.randomUUID().toString();
            MDC.put("deploymentId", deploymentId);

            log.info("Deployment request accepted: {} for pipeline {}", deploymentId, request.getPipelineId());

            // Submit governed Kernel lifecycle action request if client is available
            if (kernelLifecycleClient != null) {
                Map<String, String> headers = Map.of(
                        "X-Ghatana-Tenant-Id", request.getTenantId(),
                        "X-Ghatana-Workspace-Id", request.getPipelineId()
                );

                Promise<KernelLifecycleClient.LifecycleRunResult> lifecyclePromise =
                        kernelLifecycleClient.executeLifecyclePhase(
                                request.getPipelineId(),
                                "deploy",
                                request.getEnvironment(),
                                headers
                        );

                // Publish deployment event after lifecycle request is submitted
                return lifecyclePromise.then(lifecycleResult -> {
                    eventPublisher.publishDeploymentEvent(DeploymentEventType.PIPELINE_DEPLOY_REQUESTED, deploymentId, request);

                    // Emit metrics
                    metricsCollector.incrementCounter(
                            "aep.deployment.request.count",
                            "pipeline_id",
                            request.getPipelineId(),
                            "tenant_id",
                            request.getTenantId(),
                            "environment",
                            request.getEnvironment());

                    // Return response with deployment ID and Kernel run ID
                    DeploymentResponse response = DeploymentResponse.builder()
                            .deploymentId(deploymentId)
                            .pipelineId(request.getPipelineId())
                            .tenantId(request.getTenantId())
                            .status(lifecycleResult.status())
                            .runId(lifecycleResult.runId())
                            .timestamp(Instant.now().toString())
                            .build();

                    return Promise.of(response);
                });
            } else {
                // Fallback: publish event without Kernel lifecycle integration
                eventPublisher.publishDeploymentEvent(DeploymentEventType.PIPELINE_DEPLOY_REQUESTED, deploymentId, request);

                // Emit metrics
                metricsCollector.incrementCounter(
                        "aep.deployment.request.count",
                        "pipeline_id",
                        request.getPipelineId(),
                        "tenant_id",
                        request.getTenantId(),
                        "environment",
                        request.getEnvironment());

                // Return response with deployment ID (legacy behavior)
                DeploymentResponse response = DeploymentResponse.builder()
                        .deploymentId(deploymentId)
                        .pipelineId(request.getPipelineId())
                        .tenantId(request.getTenantId())
                        .status("DEPLOYED")
                        .timestamp(Instant.now().toString())
                        .build();

                return Promise.of(response);
            }
        } catch (Exception e) {
            log.error("Deployment request processing failed", e);
            return Promise.of(DeploymentResponse.builder()
                    .deploymentId("error")
                    .pipelineId(request.getPipelineId())
                    .tenantId(request.getTenantId())
                    .status("FAILED")
                    .error("Deployment request processing failed: " + e.getMessage())
                    .timestamp(Instant.now().toString())
                    .build());
        } finally {
            MDC.remove("pipelineId");
            MDC.remove("tenantId");
            MDC.remove("deploymentId");
        }
    }

    /**
     * Request pipeline update.
     *
     * <p>Validates update request for existing deployment, publishes PIPELINE_UPDATE_REQUESTED event.
     * Allows changing pipeline spec without full redeployment.
     *
     * @param deploymentId existing deployment identifier
     * @param request update request
     * @return Promise<DeploymentResponse> with update status
     */
    public Promise<DeploymentResponse> requestUpdate(String deploymentId, DeploymentRequest request) {
        MDC.put("deploymentId", deploymentId);
        MDC.put("pipelineId", request.getPipelineId());
        MDC.put("tenantId", request.getTenantId());

        try {
            // Validate
            if (!request.isValid() || deploymentId == null || deploymentId.isEmpty()) {
                log.warn("Invalid update request: missing deploymentId or invalid request");
                metricsCollector.incrementCounter(
                        "aep.deployment.update.validation_error",
                        "deployment_id",
                        deploymentId,
                        "tenant_id",
                        request.getTenantId());

                return Promise.of(DeploymentResponse.builder()
                        .deploymentId(deploymentId)
                        .pipelineId(request.getPipelineId())
                        .tenantId(request.getTenantId())
                        .status("FAILED")
                        .error("Invalid update request")
                        .timestamp(Instant.now().toString())
                        .build());
            }

            log.info("Update request accepted for deployment {}", deploymentId);

            // Publish update event
            eventPublisher.publishDeploymentEvent(DeploymentEventType.PIPELINE_UPDATE_REQUESTED, deploymentId, request);

            // Emit metrics
            metricsCollector.incrementCounter(
                    "aep.deployment.update.request.count",
                    "deployment_id",
                    deploymentId,
                    "tenant_id",
                    request.getTenantId());

            DeploymentResponse response = DeploymentResponse.builder()
                    .deploymentId(deploymentId)
                    .pipelineId(request.getPipelineId())
                    .tenantId(request.getTenantId())
                    .status("UPDATED")
                    .timestamp(Instant.now().toString())
                    .build();

            return Promise.of(response);
        } finally {
            MDC.remove("deploymentId");
            MDC.remove("pipelineId");
            MDC.remove("tenantId");
        }
    }

    /**
     * Request pipeline undeploy.
     *
     * <p>Validates undeploy request, publishes PIPELINE_UNDEPLOY_REQUESTED event
     * to cleanly shut down pipeline.
     *
     * @param deploymentId deployment identifier to undeploy
     * @param tenantId tenant for multi-tenancy isolation
     * @return Promise<DeploymentResponse> with undeploy status
     */
    public Promise<DeploymentResponse> requestUndeploy(String deploymentId, String tenantId) {
        MDC.put("deploymentId", deploymentId);
        MDC.put("tenantId", tenantId);

        try {
            // Validate
            if (deploymentId == null || deploymentId.isEmpty() || tenantId == null || tenantId.isEmpty()) {
                log.warn("Invalid undeploy request: missing deploymentId or tenantId");
                metricsCollector.incrementCounter(
                        "aep.deployment.undeploy.validation_error",
                        "deployment_id",
                        deploymentId,
                        "tenant_id",
                        tenantId);

                return Promise.of(DeploymentResponse.builder()
                        .deploymentId(deploymentId)
                        .tenantId(tenantId)
                        .status("FAILED")
                        .error("Invalid undeploy request: missing required fields")
                        .timestamp(Instant.now().toString())
                        .build());
            }

            log.info("Undeploy request accepted for deployment {}", deploymentId);

            // Create dummy request for event publishing (only needs tenantId)
            DeploymentRequest dummyRequest = DeploymentRequest.builder()
                    .pipelineId("")
                    .tenantId(tenantId)
                    .environment("")
                    .build();

            // Publish undeploy event
            eventPublisher.publishDeploymentEvent(
                    DeploymentEventType.PIPELINE_UNDEPLOY_REQUESTED, deploymentId, dummyRequest);

            // Emit metrics
            metricsCollector.incrementCounter(
                    "aep.deployment.undeploy.request.count", "deployment_id", deploymentId, "tenant_id", tenantId);

            DeploymentResponse response = DeploymentResponse.builder()
                    .deploymentId(deploymentId)
                    .tenantId(tenantId)
                    .status("UNDEPLOYED")
                    .timestamp(Instant.now().toString())
                    .build();

            return Promise.of(response);
        } finally {
            MDC.remove("deploymentId");
            MDC.remove("tenantId");
        }
    }
}
