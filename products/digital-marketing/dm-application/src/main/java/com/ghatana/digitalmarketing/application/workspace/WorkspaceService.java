package com.ghatana.digitalmarketing.application.workspace;

import com.ghatana.digitalmarketing.application.capabilities.DmosCapabilityRegistry;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Application service contract for DMOS workspace management.
 *
 * <p>Workspace operations are always scoped to the tenant resolved from
 * {@link DmOperationContext}. All mutating operations are audited.</p>
 *
 * @doc.type interface
 * @doc.purpose Workspace lifecycle application service contract for DMOS
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface WorkspaceService {

    /**
     * Creates a new workspace for the tenant in the given context.
     *
     * @param ctx     operation context carrying tenant, actor, correlation, and idempotency key
     * @param command workspace creation parameters; must not be null
     * @return promise resolving to the created workspace
     * @throws SecurityException if the actor is not authorized to create workspaces
     */
    Promise<Workspace> createWorkspace(DmOperationContext ctx, CreateWorkspaceCommand command);

    /**
     * Retrieves a workspace by ID, enforcing tenant isolation.
     *
     * @param ctx         operation context
     * @param workspaceId the workspace identifier string; must not be blank
     * @return promise resolving to the workspace
     * @throws SecurityException      if the actor is not authorized to read the workspace
     * @throws java.util.NoSuchElementException if the workspace does not exist in the tenant
     */
    Promise<Workspace> getWorkspace(DmOperationContext ctx, String workspaceId);

    /**
     * Lists all workspaces visible to the actor within the tenant.
     *
     * @param ctx operation context
     * @return promise resolving to a list of workspaces; never null
     * @throws SecurityException if the actor is not authorized to list workspaces
     */
    Promise<List<Workspace>> listWorkspaces(DmOperationContext ctx);

    /**
     * Suspends an active workspace.
     *
     * @param ctx         operation context
     * @param workspaceId the workspace identifier string
     * @return promise resolving to the suspended workspace
     * @throws SecurityException if the actor is not authorized to suspend workspaces
     * @throws java.util.NoSuchElementException if the workspace does not exist
     * @throws IllegalStateException if the workspace is not in ACTIVE status
     */
    Promise<Workspace> suspendWorkspace(DmOperationContext ctx, String workspaceId);

    /**
     * Reactivates a suspended workspace.
     *
     * @param ctx         operation context
     * @param workspaceId the workspace identifier string
     * @return promise resolving to the reactivated workspace
     * @throws SecurityException if the actor is not authorized
     * @throws java.util.NoSuchElementException if the workspace does not exist
     * @throws IllegalStateException if the workspace is not in SUSPENDED status
     */
    Promise<Workspace> reactivateWorkspace(DmOperationContext ctx, String workspaceId);

    /**
     * P0-002, P0-004: Returns all capabilities for a workspace.
     *
     * @param ctx         operation context
     * @param workspaceId the workspace identifier string
     * @return promise resolving to capability entries with metadata and enabled state
     * @throws SecurityException if the actor is not authorized to read the workspace
     * @throws java.util.NoSuchElementException if the workspace does not exist
     */
    Promise<List<WorkspaceCapability>> getWorkspaceCapabilities(DmOperationContext ctx, String workspaceId);

    /**
     * P0-6: Checks if a specific capability is enabled for the workspace.
     *
     * @param ctx           operation context
     * @param capabilityKey the capability key to check
     * @return promise resolving to true if the capability is enabled, false otherwise
     */
    Promise<Boolean> isCapabilityEnabled(DmOperationContext ctx, String capabilityKey);

    /**
     * API-facing capability entry for workspace capability responses.
     */
    record WorkspaceCapability(
        String key,
        boolean enabled,
        String description,
        String requiresRole,
        String tier
    ) {}

    // -----------------------------------------------------------------------
    // Command record
    // -----------------------------------------------------------------------

    /**
     * Command for creating a new workspace.
     *
     * @param name        display name; must not be blank
     * @param description optional description; may be null or empty
     */
    record CreateWorkspaceCommand(String name, String description) {
        /**
         * Validates command fields.
         *
         * @throws IllegalArgumentException if name is blank
         */
        public CreateWorkspaceCommand {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Workspace name must not be blank");
            }
        }
    }
}
