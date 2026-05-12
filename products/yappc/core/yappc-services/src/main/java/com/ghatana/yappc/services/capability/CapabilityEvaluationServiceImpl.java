/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.capability;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.api.PhasePacket;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of CapabilityEvaluationService.
 *
 * <p>This implementation evaluates capabilities by combining multiple sources:
 * <ul>
 *   <li>Tenant subscription tier (FREE, PRO, ENTERPRISE)</li>
 *   <li>Workspace membership and role</li>
 *   <li>Project role (owner, admin, editor, viewer)</li>
 *   <li>Artifact ownership</li>
 *   <li>Feature flags</li>
 *   <li>Policy decisions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.production-grade
 * @doc.purpose Evaluates user capabilities from tenant, workspace, project, and artifact context
 * @doc.layer service
 * @doc.pattern Service
 */
public final class CapabilityEvaluationServiceImpl implements CapabilityEvaluationService {

    private final AuditLogger auditLogger;
    private final MetricsCollector metricsCollector;
    private final TenantCapabilityResolver tenantCapabilityResolver;
    private final WorkspaceCapabilityResolver workspaceCapabilityResolver;
    private final ProjectCapabilityResolver projectCapabilityResolver;
    private final ArtifactCapabilityResolver artifactCapabilityResolver;
    private final PolicyCapabilityResolver policyCapabilityResolver;

    public CapabilityEvaluationServiceImpl(
            @NotNull AuditLogger auditLogger,
            @NotNull MetricsCollector metricsCollector,
            @NotNull TenantCapabilityResolver tenantCapabilityResolver,
            @NotNull WorkspaceCapabilityResolver workspaceCapabilityResolver,
            @NotNull ProjectCapabilityResolver projectCapabilityResolver,
            @NotNull ArtifactCapabilityResolver artifactCapabilityResolver,
            @NotNull PolicyCapabilityResolver policyCapabilityResolver
    ) {
        this.auditLogger = auditLogger;
        this.metricsCollector = metricsCollector;
        this.tenantCapabilityResolver = tenantCapabilityResolver;
        this.workspaceCapabilityResolver = workspaceCapabilityResolver;
        this.projectCapabilityResolver = projectCapabilityResolver;
        this.artifactCapabilityResolver = artifactCapabilityResolver;
        this.policyCapabilityResolver = policyCapabilityResolver;
    }

    @Override
    public Promise<CapabilityModel> evaluate(@NotNull CapabilityEvaluationRequest request) {
        long startTime = System.currentTimeMillis();
        
        Set<String> grantedPermissions = new HashSet<>();
        Set<String> deniedPermissions = new HashSet<>();
        String deniedReason = null;
        
        // Evaluate tenant-level capabilities
        boolean tenantCanRead = tenantCapabilityResolver.canRead(request.tenantId());
        boolean tenantCanCreate = tenantCapabilityResolver.canCreate(request.tenantId());
        boolean tenantCanUpdate = tenantCapabilityResolver.canUpdate(request.tenantId());
        boolean tenantCanDelete = tenantCapabilityResolver.canDelete(request.tenantId());
        boolean tenantCanApprove = tenantCapabilityResolver.canApprove(request.tenantId());
        boolean tenantCanReject = tenantCapabilityResolver.canReject(request.tenantId());
        boolean tenantCanRollback = tenantCapabilityResolver.canRollback(request.tenantId());
        
        if (!tenantCanRead) {
            deniedReason = "Tenant does not have read access";
            deniedPermissions.add("tenant:read");
            metricsCollector.incrementCounter("capability.denied", "source", "tenant");
            return Promise.of(CapabilityModel.allDenied(deniedReason));
        }
        grantedPermissions.add("tenant:read");
        
        // Evaluate workspace-level capabilities if workspaceId is provided
        boolean workspaceCanRead = true;
        boolean workspaceCanUpdate = false;
        if (request.workspaceId() != null && !request.workspaceId().isEmpty()) {
            workspaceCanRead = workspaceCapabilityResolver.canRead(request.tenantId(), request.workspaceId(), request.actorId());
            workspaceCanUpdate = workspaceCapabilityResolver.canUpdate(request.tenantId(), request.workspaceId(), request.actorId());
            
            if (!workspaceCanRead) {
                deniedReason = "Actor is not a member of this workspace";
                deniedPermissions.add("workspace:read");
                metricsCollector.incrementCounter("capability.denied", "source", "workspace");
                return Promise.of(CapabilityModel.allDenied(deniedReason));
            }
            grantedPermissions.add("workspace:read");
            
            if (workspaceCanUpdate) {
                grantedPermissions.add("workspace:write");
            }
        }
        
        // Evaluate project-level capabilities if projectId is provided
        boolean projectCanRead = true;
        boolean projectCanUpdate = false;
        boolean projectCanDelete = false;
        boolean projectCanApprove = false;
        if (request.projectId() != null && !request.projectId().isEmpty()) {
            projectCanRead = projectCapabilityResolver.canRead(request.tenantId(), request.workspaceId(), request.projectId(), request.actorId());
            projectCanUpdate = projectCapabilityResolver.canUpdate(request.tenantId(), request.workspaceId(), request.projectId(), request.actorId());
            projectCanDelete = projectCapabilityResolver.canDelete(request.tenantId(), request.workspaceId(), request.projectId(), request.actorId());
            projectCanApprove = projectCapabilityResolver.canApprove(request.tenantId(), request.workspaceId(), request.projectId(), request.actorId());
            
            if (!projectCanRead) {
                deniedReason = "Actor does not have read access to this project";
                deniedPermissions.add("project:read");
                metricsCollector.incrementCounter("capability.denied", "source", "project");
                return Promise.of(CapabilityModel.allDenied(deniedReason));
            }
            grantedPermissions.add("project:read");
            
            if (projectCanUpdate) {
                grantedPermissions.add("project:write");
            }
            if (projectCanDelete) {
                grantedPermissions.add("project:delete");
            }
            if (projectCanApprove) {
                grantedPermissions.add("project:approve");
            }
        }
        
        // Evaluate artifact-level capabilities if artifactId is provided
        boolean artifactCanRead = true;
        boolean artifactCanUpdate = false;
        boolean artifactCanDelete = false;
        if (request.artifactId() != null && !request.artifactId().isEmpty()) {
            artifactCanRead = artifactCapabilityResolver.canRead(request.tenantId(), request.workspaceId(), request.projectId(), request.artifactId(), request.actorId());
            artifactCanUpdate = artifactCapabilityResolver.canUpdate(request.tenantId(), request.workspaceId(), request.projectId(), request.artifactId(), request.actorId());
            artifactCanDelete = artifactCapabilityResolver.canDelete(request.tenantId(), request.workspaceId(), request.projectId(), request.artifactId(), request.actorId());
            
            if (!artifactCanRead) {
                deniedReason = "Actor does not have read access to this artifact";
                deniedPermissions.add("artifact:read");
                metricsCollector.incrementCounter("capability.denied", "source", "artifact");
                return Promise.of(CapabilityModel.allDenied(deniedReason));
            }
            grantedPermissions.add("artifact:read");
            
            if (artifactCanUpdate) {
                grantedPermissions.add("artifact:write");
            }
        }
        
        // Evaluate policy-based capabilities
        boolean policyAllows = policyCapabilityResolver.allows(request.tenantId(), request.workspaceId(), request.projectId(), request.operation(), request.phase());
        boolean policyDenies = policyCapabilityResolver.denies(request.tenantId(), request.workspaceId(), request.projectId(), request.operation(), request.phase());
        
        if (policyDenies) {
            deniedReason = "Policy denies access to this operation";
            deniedPermissions.add("policy:deny");
            metricsCollector.incrementCounter("capability.denied", "source", "policy");
            return Promise.of(CapabilityModel.allDenied(deniedReason));
        }
        
        // Combine all capabilities
        boolean canRead = tenantCanRead
                && (request.workspaceId() == null || workspaceCanRead)
                && (request.projectId() == null || projectCanRead)
                && (request.artifactId() == null || artifactCanRead)
                && policyAllows;
        
        boolean canCreate = tenantCanCreate && policyAllows;
        
        boolean canUpdate = tenantCanUpdate
                && (request.workspaceId() == null || workspaceCanUpdate)
                && (request.projectId() == null || projectCanUpdate)
                && (request.artifactId() == null || artifactCanUpdate)
                && policyAllows;
        
        boolean canDelete = tenantCanDelete
                && (request.projectId() == null || projectCanDelete)
                && (request.artifactId() == null || artifactCanDelete)
                && policyAllows;
        
        boolean canApprove = tenantCanApprove
                && (request.projectId() == null || projectCanApprove)
                && policyAllows;
        
        boolean canReject = tenantCanReject && policyAllows;
        
        boolean canRollback = tenantCanRollback && policyAllows;
        
        // Log capability evaluation
        auditLogger.log(
                Map.ofEntries(
                        Map.entry("event", "capability_evaluation"),
                        Map.entry("actorId", request.actorId()),
                        Map.entry("tenantId", request.tenantId()),
                        Map.entry("workspaceId", request.workspaceId() != null ? request.workspaceId() : ""),
                        Map.entry("projectId", request.projectId() != null ? request.projectId() : ""),
                        Map.entry("artifactId", request.artifactId() != null ? request.artifactId() : ""),
                        Map.entry("operation", request.operation() != null ? request.operation() : ""),
                        Map.entry("phase", request.phase() != null ? request.phase() : ""),
                        Map.entry("canRead", canRead),
                        Map.entry("canCreate", canCreate),
                        Map.entry("canUpdate", canUpdate),
                        Map.entry("canDelete", canDelete),
                        Map.entry("canApprove", canApprove),
                        Map.entry("canReject", canReject),
                        Map.entry("canRollback", canRollback),
                        Map.entry("grantedPermissions", grantedPermissions.toString()),
                        Map.entry("deniedPermissions", deniedPermissions.toString())
                )
        );
        
        // Record metrics
        metricsCollector.recordTimer("capability.evaluation.duration", System.currentTimeMillis() - startTime);
        metricsCollector.incrementCounter("capability.evaluation", "result", "granted");
        
        return Promise.of(new CapabilityModel(
                canRead,
                canCreate,
                canUpdate,
                canDelete,
                canApprove,
                canReject,
                canRollback,
                deniedReason,
                grantedPermissions,
                deniedPermissions
        ));
    }
}
