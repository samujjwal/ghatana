package com.ghatana.digitalmarketing.application.funnel;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.funnel.DemoWorkspace;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for demo workspace management in self-marketing acquisition funnel.
 *
 * @doc.type interface
 * @doc.purpose Provides demo workspace provisioning and lifecycle management (P3-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DemoWorkspaceService {

    /**
     * Provisions a new demo workspace for a lead.
     *
     * @param ctx the operation context
     * @param command the provisioning command
     * @return the provisioned demo workspace
     */
    Promise<DemoWorkspace> provision(DmOperationContext ctx, ProvisionDemoWorkspaceCommand command);

    /**
     * Activates a demo workspace.
     *
     * @param ctx the operation context
     * @param workspaceId the workspace ID
     * @return the activated demo workspace
     */
    Promise<DemoWorkspace> activate(DmOperationContext ctx, String workspaceId);

    /**
     * Deactivates a demo workspace.
     *
     * @param ctx the operation context
     * @param workspaceId the workspace ID
     * @param reason the deactivation reason
     * @return the deactivated demo workspace
     */
    Promise<DemoWorkspace> deactivate(DmOperationContext ctx, String workspaceId, String reason);

    /**
     * Expires a demo workspace.
     *
     * @param ctx the operation context
     * @param workspaceId the workspace ID
     * @return the expired demo workspace
     */
    Promise<DemoWorkspace> expire(DmOperationContext ctx, String workspaceId);

    /**
     * Finds a demo workspace by ID.
     *
     * @param ctx the operation context
     * @param workspaceId the workspace ID
     * @return the demo workspace if found
     */
    Promise<Optional<DemoWorkspace>> findById(DmOperationContext ctx, String workspaceId);

    /**
     * Finds demo workspaces by lead ID.
     *
     * @param ctx the operation context
     * @param leadId the lead ID
     * @return list of demo workspaces for the lead
     */
    Promise<java.util.List<DemoWorkspace>> findByLeadId(DmOperationContext ctx, String leadId);

    /**
     * Lists demo workspaces for a tenant.
     *
     * @param ctx the operation context
     * @return list of demo workspaces
     */
    Promise<java.util.List<DemoWorkspace>> list(DmOperationContext ctx);

    /**
     * Command to provision a demo workspace.
     */
    record ProvisionDemoWorkspaceCommand(
        String leadId,
        String templateId,
        Map<String, Object> templateConfig,
        Duration trialDuration
    ) {
        public ProvisionDemoWorkspaceCommand {
            if (leadId == null || leadId.isBlank()) {
                throw new IllegalArgumentException("leadId must not be blank");
            }
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId must not be blank");
            }
            if (trialDuration == null || trialDuration.isNegative() || trialDuration.isZero()) {
                throw new IllegalArgumentException("trialDuration must be positive");
            }
        }
    }
}
