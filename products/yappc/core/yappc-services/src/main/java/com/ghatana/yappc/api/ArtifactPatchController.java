package com.ghatana.yappc.api;

import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import com.ghatana.yappc.services.artifact.compileback.ChangePlanService;
import com.ghatana.yappc.services.artifact.compileback.PatchSetService;
import com.ghatana.yappc.services.patch.PatchReviewService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.ghatana.yappc.api.HttpResponses.*;

/**
 * @doc.type class
 * @doc.purpose HTTP API controller for artifact patch review and apply operations
 * @doc.layer api
 * @doc.pattern Controller
 * 
 * P1-21: Added ArtifactPatchController for patch review/apply operations.
 * Provides HTTP endpoints for creating review bundles, approving/rejecting patches,
 * applying patches, and rolling back patches with proper scope enforcement.
 */
public final class ArtifactPatchController {
    
    private static final Logger log = LoggerFactory.getLogger(ArtifactPatchController.class);
    
    private final PatchReviewService patchReviewService;
    private final PatchSetService patchSetService;
    private final ChangePlanService changePlanService;
    
    public ArtifactPatchController(
        PatchReviewService patchReviewService,
        PatchSetService patchSetService,
        ChangePlanService changePlanService
    ) {
        this.patchReviewService = patchReviewService;
        this.patchSetService = patchSetService;
        this.changePlanService = changePlanService;
    }
    
    // P1-21: Added plan endpoint for change plan generation
    public Promise<HttpResponse> planPatch(HttpRequest request) {
        try {
            ArtifactRequestScope scope = extractScope(request);
            
            return request.loadBody()
                .then(body -> {
                    String json = body.asString(java.nio.charset.StandardCharsets.UTF_8);
                    try {
                        // Parse JSON body to extract baseModelId and targetModelId
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = com.ghatana.yappc.common.JsonMapper.fromJson(json, Map.class);
                        String baseModelId = (String) payload.get("baseModelId");
                        String targetModelId = (String) payload.get("targetModelId");
                        
                        if (baseModelId == null || targetModelId == null) {
                            return Promise.of(badRequest400("baseModelId and targetModelId are required in request body"));
                        }
                        
                        return changePlanService.createChangePlan(scope, baseModelId, targetModelId)
                            .map(plan -> {
                                try {
                                    return ok200Json(JsonMapper.toJson(Map.of(
                                        "success", true,
                                        "planId", plan.planId(),
                                        "baseModelId", plan.baseModelId(),
                                        "targetModelId", plan.targetModelId(),
                                        "operationCount", plan.getOperationCount(),
                                        "autoApplicableCount", plan.getAutoApplicableCount(),
                                        "reviewRequiredCount", plan.getReviewRequiredCount(),
                                        "createdAt", plan.createdAt(),
                                        "scope", Map.of(
                                            "tenantId", scope.tenantId(),
                                            "workspaceId", scope.workspaceId(),
                                            "projectId", scope.projectId()
                                        )
                                    )));
                                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                    log.error("Error serializing plan response", e);
                                    return error500("Internal server error");
                                }
                            });
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.error("Error parsing plan request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Failed to plan patch", e));
        } catch (Exception e) {
            log.error("Failed to plan patch", e);
            return Promise.of(error500(e.getMessage()));
        }
    }
    
    // P1-21: Added generate endpoint for patch set generation
    public Promise<HttpResponse> generatePatch(HttpRequest request) {
        try {
            ArtifactRequestScope scope = extractScope(request);
            
            return request.loadBody()
                .then(body -> {
                    String json = body.asString(java.nio.charset.StandardCharsets.UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = com.ghatana.yappc.common.JsonMapper.fromJson(json, Map.class);
                        String planId = (String) payload.get("planId");
                        
                        if (planId == null) {
                            return Promise.of(badRequest400("planId is required in request body"));
                        }
                        
                        return patchSetService.generatePatchSet(scope, planId)
                            .map(patchSet -> {
                                try {
                                    return ok200Json(JsonMapper.toJson(Map.of(
                                        "success", true,
                                        "patchSetId", patchSet.patchSetId(),
                                        "planId", planId,
                                        "status", patchSet.status(),
                                        "fileCount", patchSet.patches().size(),
                                        "createdAt", patchSet.createdAt(),
                                        "scope", Map.of(
                                            "tenantId", scope.tenantId(),
                                            "workspaceId", scope.workspaceId(),
                                            "projectId", scope.projectId()
                                        )
                                    )));
                                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                    log.error("Error serializing generate response", e);
                                    return error500("Internal server error");
                                }
                            });
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.error("Error parsing generate request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Failed to generate patch", e));
        } catch (Exception e) {
            log.error("Failed to generate patch", e);
            return Promise.of(error500(e.getMessage()));
        }
    }
    
    // P1-21: Added validate endpoint for patch validation
    public Promise<HttpResponse> validatePatch(HttpRequest request) {
        try {
            ArtifactRequestScope scope = extractScope(request);
            
            return request.loadBody()
                .then(body -> {
                    String json = body.asString(java.nio.charset.StandardCharsets.UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = com.ghatana.yappc.common.JsonMapper.fromJson(json, Map.class);
                        String patchSetId = (String) payload.get("patchSetId");
                        
                        if (patchSetId == null) {
                            return Promise.of(badRequest400("patchSetId is required in request body"));
                        }
                        
                        return changePlanService.validateChangePlan(patchSetId, scope.tenantId())
                            .map(validation -> {
                                try {
                                    return ok200Json(JsonMapper.toJson(Map.of(
                                        "success", true,
                                        "planId", validation.planId(),
                                        "valid", validation.valid(),
                                        "errorCount", validation.errors().size(),
                                        "warningCount", validation.warnings().size(),
                                        "validatedAt", validation.validatedAt(),
                                        "validatorId", validation.validatorId(),
                                        "errors", validation.errors(),
                                        "warnings", validation.warnings()
                                    )));
                                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                    log.error("Error serializing validate response", e);
                                    return error500("Internal server error");
                                }
                            });
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.error("Error parsing validate request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Failed to validate patch", e));
        } catch (Exception e) {
            log.error("Failed to validate patch", e);
            return Promise.of(error500(e.getMessage()));
        }
    }
    
    public Promise<HttpResponse> createReviewBundle(HttpRequest request) {
        try {
            ArtifactRequestScope scope = extractScope(request);
            
            return request.loadBody()
                .then(body -> {
                    String json = body.asString(java.nio.charset.StandardCharsets.UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = com.ghatana.yappc.common.JsonMapper.fromJson(json, Map.class);
                        String patchSetId = (String) payload.get("patchSetId");
                        String snapshotId = (String) payload.get("snapshotId");
                        String versionId = (String) payload.get("versionId");
                        
                        if (patchSetId == null) {
                            return Promise.of(badRequest400("patchSetId is required in request body"));
                        }
                        
                        // P1-21: Create CreateReviewRequest with required fields
                        var createRequest = new PatchReviewService.CreateReviewRequest(
                            scope.tenantId(),
                            scope.projectId(),
                            snapshotId != null ? snapshotId : "",
                            versionId != null ? versionId : "",
                            patchSetId,
                            Map.of()
                        );
                        
                        return patchReviewService.createReviewBundle(createRequest)
                            .map(bundle -> {
                                try {
                                    return ok200Json(JsonMapper.toJson(Map.of(
                                        "success", true,
                                        "bundleId", bundle.id(),
                                        "patchSetId", bundle.patchSetId(),
                                        "status", bundle.status(),
                                        "createdAt", bundle.createdAt(),
                                        "scope", Map.of(
                                            "tenantId", scope.tenantId(),
                                            "workspaceId", scope.workspaceId(),
                                            "projectId", scope.projectId()
                                        )
                                    )));
                                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                    log.error("Error serializing createReviewBundle response", e);
                                    return error500("Internal server error");
                                }
                            });
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.error("Error parsing createReviewBundle request", e);
                        return Promise.of(badRequest400("Invalid JSON format"));
                    }
                })
                .whenException(e -> log.error("Failed to create review bundle", e));
        } catch (Exception e) {
            log.error("Failed to create review bundle", e);
            return Promise.of(error500(e.getMessage()));
        }
    }
    
    // TODO: Implement approve/reject/apply/rollback/list endpoints with proper ActiveJ parameter extraction
    // These require path parameter extraction which needs ActiveJ routing pattern investigation
    
    public ArtifactRequestScope extractScope(HttpRequest request) {
        String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-Id"));
        String projectId = request.getHeader(HttpHeaders.of("X-Project-Id"));
        
        if (tenantId == null || workspaceId == null || projectId == null) {
            throw new IllegalArgumentException("Missing required scope headers");
        }
        
        return new ArtifactRequestScope(tenantId, workspaceId, projectId);
    }
}
