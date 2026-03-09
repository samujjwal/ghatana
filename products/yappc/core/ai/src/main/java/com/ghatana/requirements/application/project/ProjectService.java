package com.ghatana.requirements.application.project;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.requirements.application.workspace.WorkspaceRepository;
import com.ghatana.requirements.domain.project.Project;
import com.ghatana.requirements.domain.project.ProjectMetadata;
import com.ghatana.requirements.domain.project.ProjectSettings;
import com.ghatana.requirements.domain.project.ProjectStatus;
import com.ghatana.requirements.domain.project.ProjectTemplate;
import com.ghatana.requirements.domain.workspace.Workspace;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Project business logic service.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates project operations with proper authorization, validation, and
 * integration with workspace and workflow systems. Coordinates creation, updates,
 * and lifecycle management of projects.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProjectService service = new ProjectService(
 *     projectRepository,
 *     workspaceRepository,
 *     authService
 * );
 *
 * // Create project
 * Project project = service.createProject(
 *     currentUser,
 *     workspaceId,
 *     "Mobile App 2.0",
 *     "Description",
 *     "mobile-app"
 * ).getResult();
 *
 * // Update status (may require approval)
 * Project updated = service.updateStatus(
 *     currentUser,
 *     projectId,
 *     ProjectStatus.COMPLETED
 * ).getResult();
 * }</pre>
 *
 * <p><b>Integration Points</b><br>
 * - WorkspaceRepository: Verify workspace access
 * - AuthorizationService: Permission checks
 * - Future: OrganizationManager for sub-unit creation
 * - Future: DecisionWorkflowEngine for approval workflows
 *
 * <p><b>Architecture Role</b><br>
 * Application Service layer that coordinates between domain models, repositories,
 * and external systems. Implements business rules for project management.
 *
 * @doc.type class
 * @doc.purpose Project orchestration service
 * @doc.layer product
 * @doc.pattern Service
 */
public class ProjectService {
  private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

  private final ProjectRepository projectRepository;
  private final WorkspaceRepository workspaceRepository;
  private final SyncAuthorizationService authService;

  /**
   * Construct ProjectService.
   *
   * @param projectRepository project persistence layer
   * @param workspaceRepository workspace persistence layer
   * @param authService authorization service
   */
  public ProjectService(
      ProjectRepository projectRepository,
      WorkspaceRepository workspaceRepository,
      SyncAuthorizationService authService) {
    this.projectRepository = projectRepository;
    this.workspaceRepository = workspaceRepository;
    this.authService = authService;
  }

  /**
   * Create new project in workspace.
   *
   * <p><b>Authorization</b><br>
   * - User must be member of workspace
   * - User must have PROJECT_CREATE permission
   *
   * <p><b>Validation</b><br>
   * - Workspace must exist
   * - Project name must not be empty
   * - Template must exist if provided
   *
   * @param creator user creating the project
   * @param workspaceId parent workspace ID
   * @param name project name
   * @param description project description
   * @param templateId template ID (optional)
   * @return promise completing with created project
   * @throws IllegalArgumentException if validation fails
   * @throws UnauthorizedException if user lacks permissions
   */
  public Promise<Project> createProject(
      User creator,
      String workspaceId,
      String name,
      String description,
      String templateId) {

    logger.debug(
        "Creating project: workspaceId={}, name={}, creator={}", workspaceId, name, creator.getUserId());

    // Check permission
    authService.requirePermission(creator, Permission.PROJECT_CREATE);

    // Verify workspace exists and user is member
    return workspaceRepository
        .findById(workspaceId)
        .map(
            wsOpt -> {
              Workspace workspace =
                  wsOpt.orElseThrow(
                      () -> new IllegalArgumentException("Workspace not found: " + workspaceId));

              if (!workspace.isMember(creator.getUserId())) {
                throw new IllegalArgumentException("User is not a workspace member");
              }

              return workspace;
            })
        .map(
            workspace -> {
              // Load template settings if provided
              ProjectSettings settings = ProjectSettings.defaults();
              if (templateId != null) {
                ProjectTemplate template = loadTemplate(templateId);
                settings = template.defaultSettings();
              }

              // Create project
              Project project =
                  Project.builder()
                      .projectId(UUID.randomUUID().toString())
                      .workspaceId(workspaceId)
                      .name(name)
                      .description(description != null ? description : "")
                      .ownerId(creator.getUserId())
                      .status(ProjectStatus.DRAFT)
                      .settings(settings)
                      .metadata(ProjectMetadata.empty())
                      .templateId(templateId)
                      .createdAt(Instant.now())
                      .updatedAt(Instant.now())
                      .build();

              logger.debug("Project created in memory: projectId={}", project.getProjectId());
              return project;
            })
        .then(
            project ->
                projectRepository
                    .save(project)
                    .whenResult(
                        saved ->
                            logger.info(
                                "Project saved to repository: projectId={}", saved.getProjectId())));
  }

  /**
   * Get project by ID.
   *
   * <p><b>Authorization</b><br>
   * - User must be member of parent workspace
   *
   * @param requester user requesting project
   * @param projectId project identifier
   * @return promise completing with project
   * @throws IllegalArgumentException if project not found
   * @throws UnauthorizedException if user lacks access
   */
  public Promise<Project> getProject(User requester, String projectId) {
    logger.debug("Fetching project: projectId={}, requester={}", projectId, requester.getUserId());

    return projectRepository
        .findById(projectId)
        .map(
            projectOpt -> {
              Project project =
                  projectOpt.orElseThrow(
                      () -> new IllegalArgumentException("Project not found: " + projectId));

              // Verify user access via workspace membership
              return workspaceRepository
                  .findById(project.getWorkspaceId())
                  .map(
                      wsOpt -> {
                        Workspace workspace = wsOpt.orElseThrow();
                        if (!project.hasAccess(requester.getUserId(), workspace)) {
                          throw new IllegalArgumentException("Access denied to project");
                        }
                        return project;
                      });
            })
        .then(p -> p); // Flatten promise
  }

  /**
   * List all projects in workspace.
   *
   * <p><b>Authorization</b><br>
   * - User must be member of workspace
   *
   * @param requester user requesting list
   * @param workspaceId workspace identifier
   * @return promise completing with project list
   */
  public Promise<List<Project>> listWorkspaceProjects(
      User requester, String workspaceId) {
    logger.debug(
        "Listing workspace projects: workspaceId={}, requester={}",
        workspaceId,
        requester.getUserId());

    // Verify user is workspace member
    return workspaceRepository
        .findById(workspaceId)
        .map(
            wsOpt -> {
              Workspace workspace = wsOpt.orElseThrow();
              if (!workspace.isMember(requester.getUserId())) {
                throw new IllegalArgumentException("Access denied to workspace");
              }
              return workspace;
            })
        .then(ws -> projectRepository.findByWorkspaceId(workspaceId));
  }

  /**
   * Update project status.
   *
   * <p><b>Authorization</b><br>
   * - User must be project owner or admin
   * - Some status changes may require approval (future enhancement)
   *
   * @param requester user requesting update
   * @param projectId project identifier
   * @param newStatus new project status
   * @return promise completing with updated project
   * @throws IllegalArgumentException if project not found or invalid transition
   * @throws UnauthorizedException if user lacks permissions
   */
  public Promise<Project> updateStatus(
      User requester, String projectId, ProjectStatus newStatus) {
    logger.debug(
        "Updating project status: projectId={}, newStatus={}, requester={}",
        projectId,
        newStatus,
        requester.getUserId());

    return projectRepository
        .findById(projectId)
        .map(
            projectOpt -> {
              Project project =
                  projectOpt.orElseThrow(
                      () -> new IllegalArgumentException("Project not found: " + projectId));

              // Check authorization
              if (!project.isOwner(requester.getUserId())) {
                authService.requirePermission(requester, Permission.PROJECT_UPDATE);
              }

              // Validate status transition
              validateStatusTransition(project.getStatus(), newStatus);

              project.setStatus(newStatus);
              logger.debug("Project status updated: projectId={}, status={}", projectId, newStatus);
              return project;
            })
        .then(projectRepository::save);
  }

  /**
   * Update project settings.
   *
   * <p><b>Authorization</b><br>
   * - User must be project owner
   *
   * @param requester user requesting update
   * @param projectId project identifier
   * @param newSettings new project settings
   * @return promise completing with updated project
   */
  public Promise<Project> updateSettings(
      User requester, String projectId, ProjectSettings newSettings) {
    logger.debug("Updating project settings: projectId={}, requester={}", projectId, requester.getUserId());

    return projectRepository
        .findById(projectId)
        .map(
            projectOpt -> {
              Project project =
                  projectOpt.orElseThrow(
                      () -> new IllegalArgumentException("Project not found: " + projectId));

              if (!project.isOwner(requester.getUserId())) {
                throw new IllegalArgumentException("Only project owner can update settings");
              }

              project.setSettings(newSettings);
              return project;
            })
        .then(projectRepository::save);
  }

  /**
   * Delete project.
   *
   * <p><b>Authorization</b><br>
   * - User must be project owner or admin
   *
   * @param requester user requesting deletion
   * @param projectId project identifier
   * @return promise completing when deleted
   */
  public Promise<Void> deleteProject(User requester, String projectId) {
    logger.debug("Deleting project: projectId={}, requester={}", projectId, requester.getUserId());

    return projectRepository
        .findById(projectId)
        .map(
            projectOpt -> {
              Project project =
                  projectOpt.orElseThrow(
                      () -> new IllegalArgumentException("Project not found: " + projectId));

              if (!project.isOwner(requester.getUserId())) {
                authService.requirePermission(requester, Permission.PROJECT_DELETE);
              }

              return projectId;
            })
        .then(projectRepository::delete);
  }

  // Helpers

  /**
   * Validate project status transition.
   *
   * @param currentStatus current project status
   * @param targetStatus target project status
   * @throws IllegalArgumentException if transition is invalid
   */
  private void validateStatusTransition(ProjectStatus currentStatus, ProjectStatus targetStatus) {
    // Define allowed transitions
    boolean valid =
        switch (currentStatus) {
          case DRAFT -> targetStatus == ProjectStatus.PLANNING
              || targetStatus == ProjectStatus.ARCHIVED;
          case PLANNING -> targetStatus == ProjectStatus.ACTIVE
              || targetStatus == ProjectStatus.DRAFT
              || targetStatus == ProjectStatus.ARCHIVED;
          case ACTIVE -> targetStatus == ProjectStatus.ON_HOLD
              || targetStatus == ProjectStatus.COMPLETED
              || targetStatus == ProjectStatus.ARCHIVED;
          case ON_HOLD -> targetStatus == ProjectStatus.ACTIVE
              || targetStatus == ProjectStatus.ARCHIVED;
          case COMPLETED, ARCHIVED -> false; // Terminal states
        };

    if (!valid) {
      throw new IllegalArgumentException(
          "Invalid status transition: " + currentStatus + " -> " + targetStatus);
    }
  }

  /**
   * Load project template by ID.
   *
   * <p>Future: This will load from template repository.
   *
   * @param templateId template identifier
   * @return project template
   * @throws IllegalArgumentException if template not found
   */
  private ProjectTemplate loadTemplate(String templateId) {
    return switch (templateId) {
      case "web-app" -> ProjectTemplate.webApplication();
      case "mobile-app" -> ProjectTemplate.mobileApplication();
      case "api-service" -> ProjectTemplate.apiService();
      default -> throw new IllegalArgumentException("Unknown template: " + templateId);
    };
  }

  /**
   * Update a project.
   *
   * <p><b>Purpose</b><br>
   * Updates project properties like name, description, and status.
   * Only project owners can update projects.
   *
   * <p><b>Authorization</b><br>
   * Only project owners can update projects.
   *
   * @param project Updated project data
   * @param requester User requesting update
   * @return Promise of updated project
   * @throws AccessDeniedException if not owner
   * @throws IllegalArgumentException if project not found
   */
  public Promise<Project> updateProject(Project project, User requester) {
    logger.debug("Updating project: projectId={}, requester={}", project.getProjectId(), requester.getUserId());

    return getProject(requester, project.getProjectId())
        .then(existingProject -> {
          // Check owner permission
          if (!existingProject.isOwner(requester.getUserId())) {
            return Promise.ofException(
                new AccessDeniedException(
                    "Only project owners can update projects"
                )
            );
          }

          // Update with new data
          Project updated = Project.builder()
              .projectId(existingProject.getProjectId())
              .name(project.getName() != null ? project.getName() : existingProject.getName())
              .description(project.getDescription() != null ? project.getDescription() : existingProject.getDescription())
              .status(project.getStatus() != null ? project.getStatus() : existingProject.getStatus())
              .workspaceId(existingProject.getWorkspaceId())
              .ownerId(existingProject.getOwnerId())
              .settings(existingProject.getSettings())
              .createdAt(existingProject.getCreatedAt())
              .updatedAt(Instant.now())
              .build();

          return projectRepository.save(updated)
              .map(saved -> {
                logger.info("Project updated: projectId={}, requester={}", saved.getProjectId(), requester.getUserId());
                return saved;
              });
        });
  }

  /**
   * Archive a project.
   *
   * <p><b>Purpose</b><br>
   * Archives a project by setting its status to ARCHIVED.
   * Archived projects are read-only but preserved for history.
   * Only project owners can archive projects.
   *
   * <p><b>Authorization</b><br>
   * Only project owners can archive projects.
   *
   * @param projectId Project ID
   * @param requester User requesting archive
   * @return Promise of archived project
   * @throws AccessDeniedException if not owner
   * @throws IllegalArgumentException if project not found
   */
  public Promise<Project> archiveProject(String projectId, User requester) {
    logger.debug("Archiving project: projectId={}, requester={}", projectId, requester.getUserId());

    return getProject(requester, projectId)
        .then(project -> {
          // Check owner permission
          if (!project.isOwner(requester.getUserId())) {
            return Promise.ofException(
                new AccessDeniedException(
                    "Only project owners can archive projects"
                )
            );
          }

          // Check if already archived
          if (project.getStatus() == ProjectStatus.ARCHIVED) {
            return Promise.of(project); // Already archived
          }

          // Archive the project
          Project archived = Project.builder()
              .projectId(project.getProjectId())
              .name(project.getName())
              .description(project.getDescription())
              .status(ProjectStatus.ARCHIVED)
              .workspaceId(project.getWorkspaceId())
              .ownerId(project.getOwnerId())
              .settings(project.getSettings())
              .createdAt(project.getCreatedAt())
              .updatedAt(Instant.now())
              .build();

          return projectRepository.save(archived)
              .map(saved -> {
                logger.info("Project archived: projectId={}, requester={}", saved.getProjectId(), requester.getUserId());
                return saved;
              });
        });
  }
}