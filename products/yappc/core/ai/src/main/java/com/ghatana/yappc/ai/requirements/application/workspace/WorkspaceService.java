package com.ghatana.yappc.ai.requirements.application.workspace;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.yappc.ai.requirements.domain.workspace.Workspace;
import com.ghatana.yappc.ai.requirements.domain.workspace.WorkspaceMember;
import com.ghatana.yappc.ai.requirements.domain.workspace.WorkspaceRole;
import com.ghatana.yappc.ai.requirements.domain.workspace.WorkspaceSettings;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Workspace business logic service.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates workspace operations including creation, member management,
 * and integration with organization and workflow systems.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkspaceService service = new WorkspaceService(repository, authService);
 * Promise<Workspace> workspace = service.createWorkspace(
 *     userPrincipal,
 *     "Q1 Planning",
 *     "2025 Q1 Requirements"
 * );
 * }</pre>
 *
 * <p><b>Integration Points</b><br>
 * - AuthorizationService: Permission checks
 * - WorkspaceRepository: Data persistence
 * - Future: OrganizationManager for OrgUnit integration
 * - Future: DecisionWorkflowEngine for approval workflows
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe for concurrent invocations as long as repository is thread-safe.
 *
 * @doc.type class
 * @doc.purpose Workspace orchestration and business logic
 * @doc.layer product
 * @doc.pattern Service
 * @see Workspace
 * @see WorkspaceRepository
 * @see AuthorizationService
 */
public class WorkspaceService {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository repository;
    private final SyncAuthorizationService authService;

    /**
     * Creates workspace service.
     *
     * @param repository Workspace repository
     * @param authService Authorization service
     */
    public WorkspaceService(WorkspaceRepository repository, SyncAuthorizationService authService) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.authService = Objects.requireNonNull(authService, "authService cannot be null");
    }

    // ============ Workspace Operations ============

    /**
     * Create new workspace.
     *
     * @param creator User creating workspace
     * @param name Workspace name
     * @param description Workspace description
     * @return Promise of created workspace
     * @throws AccessDeniedException if user lacks WORKSPACE_CREATE permission
     * @throws IllegalArgumentException if inputs are invalid
     */
    public Promise<Workspace> createWorkspace(User creator, String name, String description) {
        logger.debug("Creating workspace: name={}, creator={}", name, creator.getUserId());

        // Check permission
        authService.requirePermission(creator, Permission.WORKSPACE_CREATE);

        // Validate inputs
        if (name == null || name.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("Workspace name is required"));
        }

        // Create workspace
        String workspaceId = UUID.randomUUID().toString();
        String orgUnitId = UUID.randomUUID().toString();  // For now, separate IDs
        Instant now = Instant.now();

        Workspace workspace = Workspace.builder()
            .workspaceId(workspaceId)
            .name(name)
            .description(description != null ? description : "")
            .ownerId(creator.getUserId())
            .orgUnitId(orgUnitId)
            .status(Workspace.WorkspaceStatus.ACTIVE)
            .settings(WorkspaceSettings.defaults())
            .members(List.of(
                new WorkspaceMember(creator.getUserId(), WorkspaceRole.OWNER, now)
            ))
            .createdAt(now)
            .updatedAt(now)
            .build();

        logger.info("Saving workspace: id={}, name={}", workspaceId, name);

        return repository.save(workspace)
            .map(savedWs -> {
                logger.info("Workspace created successfully: id={}, name={}", savedWs.workspaceId(), savedWs.name());
                return savedWs;
            });
    }

    /**
     * Get workspace by ID.
     *
     * @param workspaceId Workspace ID
     * @param requester User requesting
     * @return Promise of workspace
     * @throws IllegalArgumentException if workspace not found
     * @throws AccessDeniedException if user lacks access
     */
    public Promise<Workspace> getWorkspace(String workspaceId, User requester) {
        logger.debug("Getting workspace: id={}, requester={}", workspaceId, requester.getUserId());

        return repository.findById(workspaceId)
            .then(optionalWs -> {
                if (optionalWs.isEmpty()) {
                    return Promise.ofException(
                        new IllegalArgumentException("Workspace not found: " + workspaceId)
                    );
                }

                Workspace workspace = optionalWs.get();

                // Check permission
                if (!isAccessible(workspace, requester)) {
                    logger.warn("Access denied to workspace: id={}, user={}", workspaceId, requester.getUserId());
                    return Promise.ofException(
                        new com.ghatana.platform.security.rbac.AccessDeniedException(
                            "User does not have access to this workspace"
                        )
                    );
                }

                logger.debug("Workspace retrieved: id={}", workspaceId);
                return Promise.of(workspace);
            });
    }

    /**
     * List workspaces for a user.
     *
     * @param userId User ID
     * @return Promise of workspace list
     */
    public Promise<List<Workspace>> listUserWorkspaces(String userId) {
        logger.debug("Listing workspaces for user: id={}", userId);

        return repository.findByMember(userId)
            .map(workspaces -> {
                logger.debug("Found {} workspaces for user: id={}", workspaces.size(), userId);
                return workspaces;
            });
    }

    /**
     * Update workspace.
     *
     * @param workspace Updated workspace
     * @param requester User requesting update
     * @return Promise of updated workspace
     * @throws AccessDeniedException if user lacks permission
     */
    public Promise<Workspace> updateWorkspace(Workspace workspace, User requester) {
        logger.debug("Updating workspace: id={}, requester={}", workspace.workspaceId(), requester.getUserId());

        // Permission check
        if (!workspace.isAdmin(requester.getUserId())) {
            logger.warn("Update denied for workspace: id={}, user={}", workspace.workspaceId(), requester.getUserId());
            return Promise.ofException(
                new com.ghatana.platform.security.rbac.AccessDeniedException(
                    "User does not have admin permission for this workspace"
                )
            );
        }

        Workspace updated = Workspace.builder()
            .workspaceId(workspace.workspaceId())
            .name(workspace.name())
            .description(workspace.description())
            .ownerId(workspace.ownerId())
            .orgUnitId(workspace.orgUnitId())
            .settings(workspace.settings())
            .members(workspace.members())
            .status(workspace.status())
            .createdAt(workspace.createdAt())
            .updatedAt(Instant.now())
            .build();

        return repository.update(updated)
            .map(ws -> {
                logger.info("Workspace updated: id={}", ws.workspaceId());
                return ws;
            });
    }

    // ============ Member Management ============

    /**
     * Add member to workspace.
     *
     * @param workspaceId Workspace ID
     * @param userId User ID to add
     * @param role Role to assign
     * @param requester User requesting
     * @return Promise of updated workspace
     */
    public Promise<Workspace> addMember(String workspaceId, String userId, WorkspaceRole role, User requester) {
        logger.debug("Adding member to workspace: workspace={}, user={}, role={}, requester={}", 
            workspaceId, userId, role, requester.getUserId());

        return getWorkspace(workspaceId, requester)
            .then(workspace -> {
                // Check admin permission
                if (!workspace.isAdmin(requester.getUserId())) {
                    return Promise.ofException(
                        new AccessDeniedException(
                            "Only admins can add members"
                        )
                    );
                }

                // Check if already member
                if (workspace.isMember(userId)) {
                    logger.warn("User already member of workspace: workspace={}, user={}", workspaceId, userId);
                    return Promise.ofException(
                        new IllegalArgumentException("User is already a member of this workspace")
                    );
                }

                // Add member
                List<WorkspaceMember> newMembers = new ArrayList<>(workspace.members());
                newMembers.add(new WorkspaceMember(userId, role, Instant.now()));

                Workspace updated = Workspace.builder()
                    .workspaceId(workspace.workspaceId())
                    .name(workspace.name())
                    .description(workspace.description())
                    .ownerId(workspace.ownerId())
                    .orgUnitId(workspace.orgUnitId())
                    .settings(workspace.settings())
                    .members(newMembers)
                    .status(workspace.status())
                    .createdAt(workspace.createdAt())
                    .updatedAt(Instant.now())
                    .build();

                return repository.update(updated)
                    .map(ws -> {
                        logger.info("Member added to workspace: workspace={}, user={}, role={}", 
                            workspaceId, userId, role);
                        return ws;
                    });
            });
    }

    /**
     * Remove member from workspace.
     *
     * @param workspaceId Workspace ID
     * @param userId User ID to remove
     * @param requester User requesting
     * @return Promise of updated workspace
     */
    public Promise<Workspace> removeMember(String workspaceId, String userId, User requester) {
        logger.debug("Removing member from workspace: workspace={}, user={}, requester={}", 
            workspaceId, userId, requester.getUserId());

        return getWorkspace(workspaceId, requester)
            .then(workspace -> {
                // Check admin permission
                if (!workspace.isAdmin(requester.getUserId())) {
                    return Promise.ofException(
                        new AccessDeniedException(
                            "Only admins can remove members"
                        )
                    );
                }

                // Cannot remove owner
                if (workspace.isOwner(userId)) {
                    return Promise.ofException(
                        new IllegalArgumentException("Cannot remove workspace owner")
                    );
                }

                // Remove member
                List<WorkspaceMember> newMembers = workspace.members().stream()
                    .filter(m -> !m.userId().equals(userId))
                    .toList();

                Workspace updated = Workspace.builder()
                    .workspaceId(workspace.workspaceId())
                    .name(workspace.name())
                    .description(workspace.description())
                    .ownerId(workspace.ownerId())
                    .orgUnitId(workspace.orgUnitId())
                    .settings(workspace.settings())
                    .members(new ArrayList<>(newMembers))
                    .status(workspace.status())
                    .createdAt(workspace.createdAt())
                    .updatedAt(Instant.now())
                    .build();

                return repository.update(updated)
                    .map(ws -> {
                        logger.info("Member removed from workspace: workspace={}, user={}", workspaceId, userId);
                        return ws;
                    });
            });
    }

    /**
     * Delete a workspace.
     *
     * <p><b>Purpose</b><br>
     * Permanently deletes a workspace and all its associated data.
     * Only workspace owners can delete workspaces.
     *
     * <p><b>Authorization</b><br>
     * Only workspace owners can delete workspaces.
     *
     * <p><b>Side Effects</b><br>
     * - Deletes workspace from repository
     * - Cascades to delete all projects in the workspace
     * - Removes all member associations
     *
     * @param workspaceId Workspace ID
     * @param requester User requesting deletion
     * @return Promise of deletion completion
     * @throws AccessDeniedException if not owner
     * @throws IllegalArgumentException if workspace not found
     */
    public Promise<Void> deleteWorkspace(String workspaceId, User requester) {
        logger.debug("Deleting workspace: workspace={}, requester={}", workspaceId, requester.getUserId());

        return getWorkspace(workspaceId, requester)
            .then(workspace -> {
                // Check owner permission
                if (!workspace.isOwner(requester.getUserId())) {
                    return Promise.ofException(
                        new AccessDeniedException(
                            "Only workspace owners can delete workspaces"
                        )
                    );
                }

                // Delete workspace (cascade will handle projects)
                return repository.delete(workspaceId)
                    .map(result -> {
                        logger.info("Workspace deleted: workspace={}, requester={}", workspaceId, requester.getUserId());
                        return null;
                    });
            });
    }

    // ============ Helper Methods ============

    /**
     * Check if user has access to workspace.
     *
     * @param workspace Workspace
     * @param user User
     * @return true if user has access
     */
    private boolean isAccessible(Workspace workspace, User user) {
        // Workspace owner and members can access
        return workspace.isMember(user.getUserId());
    }
}