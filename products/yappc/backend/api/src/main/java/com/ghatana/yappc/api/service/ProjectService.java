/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.products.yappc.domain.model.Project;
import com.ghatana.yappc.api.repository.ProjectRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing projects.
 *
 * <p><b>Purpose</b><br>
 * Implements business logic for project management with clean API alignment
 * to the domain model.
 *
 * @doc.type class
 * @doc.purpose Project management business logic
 * @doc.layer service
 * @doc.pattern Service
 */
public class ProjectService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository repository;
    private final AuditService auditService;

    @Inject
    public ProjectService(ProjectRepository repository, AuditService auditService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    /**
     * Creates a new project.
     *
     * @param tenantId The tenant identifier
     * @param input The project creation input
     * @return Promise resolving to the created project
     */
    public Promise<Project> createProject(String tenantId, CreateProjectInput input) {
        logger.info("Creating project '{}' in workspace {}", input.name(), input.workspaceId());

        Project project = Project.of(input.workspaceId(), input.name());
        
        if (input.description() != null) {
            project.setDescription(input.description());
        }
        if (input.repositoryUrl() != null) {
            project.setRepositoryUrl(input.repositoryUrl());
        }
        if (input.defaultBranch() != null) {
            project.setDefaultBranch(input.defaultBranch());
        }
        if (input.language() != null) {
            project.setLanguage(input.language());
        }

        return repository.save(project)
            .whenResult(saved -> {
                AuditEvent event = AuditEvent.builder()
                    .eventType("PROJECT_CREATED")
                    .resourceId(saved.getId().toString())
                    .resourceType("PROJECT")
                    .tenantId(tenantId)
                    .timestamp(Instant.now())
                    .build();
                auditService.record(event);
            });
    }

    /**
     * Gets a project by ID.
     *
     * @param tenantId The tenant identifier
     * @param projectId The project identifier
     * @return Promise resolving to the project or empty if not found
     */
    public Promise<Optional<Project>> getProject(String tenantId, UUID projectId) {
        return repository.findById(tenantId, projectId);
    }

    /**
     * Lists projects in a workspace.
     *
     * @param tenantId The tenant identifier
     * @param workspaceId The workspace identifier
     * @return Promise resolving to list of projects
     */
    public Promise<List<Project>> listWorkspaceProjects(String tenantId, UUID workspaceId) {
        return repository.findByWorkspace(tenantId, workspaceId.toString());
    }

    /**
     * Lists all active (non-archived) projects in a workspace.
     *
     * @param tenantId The tenant identifier
     * @param workspaceId The workspace identifier
     * @return Promise resolving to list of active projects
     */
    public Promise<List<Project>> listActiveProjects(String tenantId, UUID workspaceId) {
        return repository.findActiveByWorkspace(tenantId, workspaceId.toString());
    }

    /**
     * Updates a project.
     *
     * @param tenantId The tenant identifier
     * @param projectId The project identifier
     * @param input The update input
     * @return Promise resolving to the updated project
     */
    public Promise<Project> updateProject(String tenantId, UUID projectId, UpdateProjectInput input) {
        return repository.findById(tenantId, projectId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Project not found: " + projectId));
                    }
                    Project project = opt.get();

                    if (input.name() != null) {
                        project.setName(input.name());
                    }
                    if (input.description() != null) {
                        project.setDescription(input.description());
                    }
                    if (input.repositoryUrl() != null) {
                        project.setRepositoryUrl(input.repositoryUrl());
                    }
                    if (input.defaultBranch() != null) {
                        project.setDefaultBranch(input.defaultBranch());
                    }
                    if (input.language() != null) {
                        project.setLanguage(input.language());
                    }

                    project.setUpdatedAt(Instant.now());

                    return repository.save(project)
                        .whenResult(saved -> {
                            AuditEvent event = AuditEvent.builder()
                                .eventType("PROJECT_UPDATED")
                                .resourceId(saved.getId().toString())
                                .resourceType("PROJECT")
                                .tenantId(tenantId)
                                .timestamp(Instant.now())
                                .build();
                            auditService.record(event);
                        });
                });
    }

    /**
     * Archives a project.
     *
     * @param tenantId The tenant identifier
     * @param projectId The project identifier
     * @return Promise resolving to the archived project
     */
    public Promise<Project> archiveProject(String tenantId, UUID projectId) {
        return repository.findById(tenantId, projectId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Project not found: " + projectId));
                    }
                    Project project = opt.get();
                    project.archive();

                    return repository.save(project)
                        .whenResult(saved -> {
                            AuditEvent event = AuditEvent.builder()
                                .eventType("PROJECT_ARCHIVED")
                                .resourceId(saved.getId().toString())
                                .resourceType("PROJECT")
                                .tenantId(tenantId)
                                .timestamp(Instant.now())
                                .build();
                            auditService.record(event);
                        });
                });
    }

    /**
     * Unarchives a project.
     *
     * @param tenantId The tenant identifier
     * @param projectId The project identifier
     * @return Promise resolving to the unarchived project
     */
    public Promise<Project> unarchiveProject(String tenantId, UUID projectId) {
        return repository.findById(tenantId, projectId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Project not found: " + projectId));
                    }
                    Project project = opt.get();
                    project.unarchive();

                    return repository.save(project)
                        .whenResult(saved -> {
                            AuditEvent event = AuditEvent.builder()
                                .eventType("PROJECT_UNARCHIVED")
                                .resourceId(saved.getId().toString())
                                .resourceType("PROJECT")
                                .tenantId(tenantId)
                                .timestamp(Instant.now())
                                .build();
                            auditService.record(event);
                        });
                });
    }

    /**
     * Records a scan for the project.
     *
     * @param tenantId The tenant identifier
     * @param projectId The project identifier
     * @return Promise resolving to the updated project
     */
    public Promise<Project> recordScan(String tenantId, UUID projectId) {
        return repository.findById(tenantId, projectId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Project not found: " + projectId));
                    }
                    Project project = opt.get();
                    project.recordScan();

                    return repository.save(project);
                });
    }

    /**
     * Deletes a project.
     *
     * @param tenantId The tenant identifier
     * @param projectId The project identifier
     * @return Promise resolving to true if deleted
     */
    public Promise<Boolean> deleteProject(String tenantId, UUID projectId) {
        logger.info("Deleting project: {}", projectId);
        return repository.delete(tenantId, projectId)
            .whenResult(deleted -> {
                if (deleted) {
                    AuditEvent event = AuditEvent.builder()
                        .eventType("PROJECT_DELETED")
                        .resourceId(projectId.toString())
                        .resourceType("PROJECT")
                        .tenantId(tenantId)
                        .timestamp(Instant.now())
                        .build();
                    auditService.record(event);
                }
            });
    }

    // ========== Input Records ==========

    public record CreateProjectInput(
            UUID workspaceId,
            String name,
            String description,
            String repositoryUrl,
            String defaultBranch,
            String language
    ) {
        public CreateProjectInput {
            Objects.requireNonNull(workspaceId, "workspaceId must not be null");
            Objects.requireNonNull(name, "name must not be null");
        }
    }

    public record UpdateProjectInput(
            String name,
            String description,
            String repositoryUrl,
            String defaultBranch,
            String language
    ) {}
}
