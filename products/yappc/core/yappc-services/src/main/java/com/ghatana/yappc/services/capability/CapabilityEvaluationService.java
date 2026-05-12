/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.capability;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Service for evaluating user capabilities based on tenant, workspace, project, and artifact context.
 *
 * <p>This service centralizes authorization logic by evaluating capabilities from multiple sources:
 * <ul>
 *   <li>Tenant configuration and subscription tier</li>
 *   <li>Workspace membership and role</li>
 *   <li>Project role and ownership</li>
 *   <li>Artifact ownership</li>
 *   <li>Feature flags</li>
 *   <li>Policy decisions</li>
 * </ul>
 *
 * <p>The service returns a CapabilityModel that indicates what actions the current actor can perform.
 * This model is used in the phase packet to drive UI action rendering and backend authorization checks.
 *
 * @doc.type interface
 * @doc.purpose Evaluates user capabilities for authorization and UI action gating
 * @doc.layer service
 * @doc.pattern Service
 */
public interface CapabilityEvaluationService {

    /**
     * Evaluates capabilities for a given context.
     *
     * @param request the capability evaluation request
     * @return a promise containing the capability model
     */
    Promise<CapabilityModel> evaluate(@NotNull CapabilityEvaluationRequest request);

    /**
     * Request for capability evaluation.
     */
    record CapabilityEvaluationRequest(
            @NotNull String tenantId,
            @NotNull String actorId,
            @Nullable String workspaceId,
            @Nullable String projectId,
            @Nullable String artifactId,
            @Nullable String operation,
            @Nullable String phase
    ) {
        public CapabilityEvaluationRequest {
            if (tenantId == null || tenantId.isEmpty()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (actorId == null || actorId.isEmpty()) {
                throw new IllegalArgumentException("actorId is required");
            }
        }
    }

    /**
     * Capability model indicating what actions the actor can perform.
     *
     * <p>This model is returned by the CapabilityEvaluationService and used to:
     * <ul>
     *   <li>Gate UI actions in the phase cockpit</li>
     *   <li>Authorize backend operations</li>
     *   <li>Provide user-facing permission error messages</li>
     * </ul>
     */
    record CapabilityModel(
            boolean canRead,
            boolean canCreate,
            boolean canUpdate,
            boolean canDelete,
            boolean canApprove,
            boolean canReject,
            boolean canRollback,
            @Nullable String deniedReason,
            @NotNull Set<String> grantedPermissions,
            @NotNull Set<String> deniedPermissions
    ) {
        /**
         * Creates a capability model with all capabilities granted.
         *
         * @return a capability model with all capabilities set to true
         */
        public static CapabilityModel allGranted() {
            return new CapabilityModel(
                    true, true, true, true, true, true, true,
                    null,
                    Set.of(),
                    Set.of()
            );
        }

        /**
         * Creates a capability model with all capabilities denied.
         *
         * @param reason the reason for denial
         * @return a capability model with all capabilities set to false
         */
        public static CapabilityModel allDenied(@Nullable String reason) {
            return new CapabilityModel(
                    false, false, false, false, false, false, false,
                    reason,
                    Set.of(),
                    Set.of()
            );
        }

        /**
         * Checks if the actor has any of the specified capabilities.
         *
         * @param capabilities the capabilities to check
         * @return true if any of the specified capabilities are granted
         */
        public boolean hasAny(@NotNull String... capabilities) {
            for (String capability : capabilities) {
                switch (capability) {
                    case "read":
                        if (canRead) return true;
                        break;
                    case "create":
                        if (canCreate) return true;
                        break;
                    case "update":
                        if (canUpdate) return true;
                        break;
                    case "delete":
                        if (canDelete) return true;
                        break;
                    case "approve":
                        if (canApprove) return true;
                        break;
                    case "reject":
                        if (canReject) return true;
                        break;
                    case "rollback":
                        if (canRollback) return true;
                        break;
                }
            }
            return false;
        }

        /**
         * Checks if the actor has all of the specified capabilities.
         *
         * @param capabilities the capabilities to check
         * @return true if all of the specified capabilities are granted
         */
        public boolean hasAll(@NotNull String... capabilities) {
            for (String capability : capabilities) {
                if (!hasAny(capability)) {
                    return false;
                }
            }
            return true;
        }
    }
}
