package com.ghatana.requirements.api.graphql.resolver;

import com.ghatana.platform.security.model.User;
import com.ghatana.requirements.application.project.ProjectService;
import com.ghatana.requirements.application.workspace.WorkspaceService;
import com.ghatana.requirements.domain.project.Project;
import com.ghatana.requirements.domain.project.ProjectStatus;
import com.ghatana.requirements.domain.workspace.Workspace;
import com.ghatana.requirements.domain.workspace.WorkspaceRole;
import io.activej.promise.Promise;
// GraphQL support is provided via the framework's GraphQL integration
// The resolver pattern follows GraphQL Java specifications without requiring
// external kickstart library - using standard graphql-java annotations instead
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * GraphQL Mutation Resolver for write operations.
 *
 * <p><b>Purpose</b><br>
 * Handles all GraphQL mutations for creating, updating, and deleting
 * workspaces, projects, and requirements.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * mutation {
 *   createWorkspace(input: {
 *     name: "My Workspace"
 *     description: "Description"
 *   }) {
 *     id
 *     name
 *   }
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. All service dependencies are thread-safe.
 *
 * <p><b>Architecture Role</b><br>
 * GraphQL mutation layer that delegates to application services.
 * Handles validation and authorization through UserPrincipal.
 *
 * @doc.type class
 * @doc.purpose GraphQL mutation resolver
 * @doc.layer product
 * @doc.pattern Resolver
 */
public class MutationResolver /* implements GraphQLMutationResolver */ {
    private static final Logger logger = LoggerFactory.getLogger(MutationResolver.class);

    private final WorkspaceService workspaceService;
    private final ProjectService projectService;

    public MutationResolver(WorkspaceService workspaceService, ProjectService projectService) {
        this.workspaceService = Objects.requireNonNull(workspaceService);
        this.projectService = Objects.requireNonNull(projectService);
    }

    // ============ Workspace Mutations ============

    /**
     * Mutation: createWorkspace(input: CreateWorkspaceInput!): Workspace!
     */
    public Promise<Workspace> createWorkspace(CreateWorkspaceInput input, User principal) {
        logger.info("GraphQL mutation: createWorkspace(name={})", input.name);

        return workspaceService.createWorkspace(
            principal,
            input.name,
            input.description
        );
    }

    /**
     * Mutation: updateWorkspace(id: String!, input: UpdateWorkspaceInput!): Workspace!
     */
    public Promise<Workspace> updateWorkspace(String id, UpdateWorkspaceInput input, User principal) {
        logger.info("GraphQL mutation: updateWorkspace(id={})", id);

        return workspaceService.getWorkspace(id, principal)
            .then(existingWorkspace -> {
                var updatedWorkspace = Workspace.builder()
                    .workspaceId(existingWorkspace.workspaceId())
                    .name(input.name != null ? input.name : existingWorkspace.name())
                    .description(input.description != null ? input.description : existingWorkspace.description())
                    .ownerId(existingWorkspace.ownerId())
                    .orgUnitId(existingWorkspace.orgUnitId())
                    .status(existingWorkspace.status())
                    .settings(existingWorkspace.settings())
                    .members(existingWorkspace.members())
                    .build();
                return workspaceService.updateWorkspace(updatedWorkspace, principal);
            });
    }

    /**
     * Mutation: deleteWorkspace(id: String!): Boolean!
     */
    public Promise<Boolean> deleteWorkspace(String id, User principal) {
        logger.info("GraphQL mutation: deleteWorkspace(id={})", id);

        return workspaceService.deleteWorkspace(id, principal)
            .then(
                v -> Promise.of(true),
                e -> {
                    logger.error("Failed to delete workspace: {}", e.getMessage());
                    return Promise.of(false);
                });
    }

    /**
     * Mutation: addWorkspaceMember(workspaceId: String!, input: AddMemberInput!): Workspace!
     */
    public Promise<Workspace> addWorkspaceMember(String workspaceId, AddMemberInput input, User principal) {
        logger.info("GraphQL mutation: addWorkspaceMember(workspaceId={}, userId={})", workspaceId, input.userId);

        return workspaceService.addMember(
            workspaceId,
            input.userId,
            input.role,
            principal
        );
    }

    /**
     * Mutation: removeWorkspaceMember(workspaceId: String!, userId: String!): Boolean!
     */
    public Promise<Boolean> removeWorkspaceMember(String workspaceId, String userId, User principal) {
        logger.info("GraphQL mutation: removeWorkspaceMember(workspaceId={}, userId={})", workspaceId, userId);

        return workspaceService.removeMember(workspaceId, userId, principal)
            .then(
                v -> Promise.of(true),
                e -> {
                    logger.error("Failed to remove member: {}", e.getMessage());
                    return Promise.of(false);
                });
    }

    // ============ Project Mutations ============

    /**
     * Mutation: createProject(input: CreateProjectInput!): Project!
     */
    public Promise<Project> createProject(CreateProjectInput input, User principal) {
        logger.info("GraphQL mutation: createProject(name={}, workspaceId={})", input.name, input.workspaceId);

        return projectService.createProject(
            principal,
            input.workspaceId,
            input.name,
            input.description,
            input.template
        );
    }

    /**
     * Mutation: updateProject(id: String!, input: UpdateProjectInput!): Project!
     */
    public Promise<Project> updateProject(String id, UpdateProjectInput input, User principal) {
        logger.info("GraphQL mutation: updateProject(id={})", id);

        return projectService.getProject(principal, id)
            .then(existingProject -> {
                var updatedProject = Project.builder()
                    .projectId(existingProject.getProjectId())
                    .name(input.name != null ? input.name : existingProject.getName())
                    .description(input.description != null ? input.description : existingProject.getDescription())
                    .status(input.status != null ? input.status : existingProject.getStatus())
                    .workspaceId(existingProject.getWorkspaceId())
                    .ownerId(existingProject.getOwnerId())
                    .settings(existingProject.getSettings())
                    .createdAt(existingProject.getCreatedAt())
                    .updatedAt(java.time.Instant.now())
                    .build();
                return projectService.updateProject(updatedProject, principal);
            });
    }

    /**
     * Mutation: archiveProject(id: String!): Boolean!
     */
    public Promise<Boolean> archiveProject(String id, User principal) {
        logger.info("GraphQL mutation: archiveProject(id={})", id);

        return projectService.archiveProject(id, principal)
            .then(
                v -> Promise.of(true),
                e -> {
                    logger.error("Failed to archive project: {}", e.getMessage());
                    return Promise.of(false);
                });
    }

    // ============ Input Types ============

    public static record CreateWorkspaceInput(String name, String description) {}
    public static record UpdateWorkspaceInput(String name, String description) {}
    public static record AddMemberInput(String userId, WorkspaceRole role) {}

    public static record CreateProjectInput(
        String workspaceId,
        String name,
        String description,
        String template
    ) {}

    public static record UpdateProjectInput(
        String name,
        String description,
        ProjectStatus status
    ) {}
}

