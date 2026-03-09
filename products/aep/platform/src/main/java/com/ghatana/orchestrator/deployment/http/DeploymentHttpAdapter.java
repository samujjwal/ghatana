package com.ghatana.orchestrator.deployment.http;

import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import com.ghatana.orchestrator.deployment.contract.DeploymentResponse;
import com.ghatana.orchestrator.deployment.service.DeploymentOrchestrator;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * HTTP adapter for deployment endpoints.
 *
 * <p>
 * <b>Purpose</b><br>
 * Handles HTTP requests for pipeline deployment operations: - POST
 * /api/v1/deployments - Request deployment - PUT
 * /api/v1/deployments/{deploymentId} - Request update - DELETE
 * /api/v1/deployments/{deploymentId} - Request undeploy
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * DeploymentHttpAdapter adapter = new DeploymentHttpAdapter(orchestrator);
 * // Wire to HTTP server routes
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP deployment endpoint adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */
@Slf4j
@RequiredArgsConstructor
public class DeploymentHttpAdapter {

    private final DeploymentOrchestrator orchestrator;

    /**
     * Handle POST /api/v1/deployments - Request deployment.
     *
     * @param request deployment request
     * @return deployment response
     */
    public DeploymentResponse handleDeploymentRequest(DeploymentRequest request) {
        MDC.put("operation", "deployment");
        MDC.put("pipelineId", request.getPipelineId());
        MDC.put("tenantId", request.getTenantId());

        try {
            log.info("Deployment request received for pipeline {} (tenant={})",
                    request.getPipelineId(), request.getTenantId());

            return awaitOrThrow(orchestrator.requestDeployment(request), "deployment");
        } finally {
            MDC.remove("operation");
            MDC.remove("pipelineId");
            MDC.remove("tenantId");
        }
    }

    /**
     * Handle PUT /api/v1/deployments/{deploymentId} - Request update.
     *
     * @param deploymentId deployment identifier
     * @param request update request
     * @return deployment response
     */
    public DeploymentResponse handleUpdateRequest(String deploymentId, DeploymentRequest request) {
        MDC.put("operation", "update");
        MDC.put("deploymentId", deploymentId);
        MDC.put("pipelineId", request.getPipelineId());
        MDC.put("tenantId", request.getTenantId());

        try {
            log.info("Update request received for deployment {} (pipeline={}, tenant={})",
                    deploymentId, request.getPipelineId(), request.getTenantId());

            return awaitOrThrow(orchestrator.requestUpdate(deploymentId, request), "update");
        } finally {
            MDC.remove("operation");
            MDC.remove("deploymentId");
            MDC.remove("pipelineId");
            MDC.remove("tenantId");
        }
    }

    /**
     * Handle DELETE /api/v1/deployments/{deploymentId} - Request undeploy.
     *
     * @param deploymentId deployment identifier
     * @param tenantId tenant for isolation
     * @return deployment response
     */
    public DeploymentResponse handleUndeployRequest(String deploymentId, String tenantId) {
        MDC.put("operation", "undeploy");
        MDC.put("deploymentId", deploymentId);
        MDC.put("tenantId", tenantId);

        try {
            log.info("Undeploy request received for deployment {} (tenant={})",
                    deploymentId, tenantId);

            return awaitOrThrow(orchestrator.requestUndeploy(deploymentId, tenantId), "undeploy");
        } finally {
            MDC.remove("operation");
            MDC.remove("deploymentId");
            MDC.remove("tenantId");
        }
    }

    /**
     * Validate tenant isolation.
     *
     * @param headerTenant tenant from X-Tenant-ID header
     * @param requestTenant tenant from request
     * @return true if match, false otherwise
     */
    public boolean validateTenantIsolation(String headerTenant, String requestTenant) {
        if (headerTenant == null || requestTenant == null) {
            log.warn("Tenant validation failed: missing tenant identifiers");
            return false;
        }

        if (!headerTenant.equals(requestTenant)) {
            log.warn("Tenant mismatch: header={}, request={}", headerTenant, requestTenant);
            return false;
        }

        return true;
    }

    private static DeploymentResponse awaitOrThrow(Promise<DeploymentResponse> promise, String operation) {
        Throwable error = promise.getException();
        if (error != null) {
            throw new IllegalStateException("Deployment " + operation + " failed", error);
        }
        return promise.getResult();
    }
}
