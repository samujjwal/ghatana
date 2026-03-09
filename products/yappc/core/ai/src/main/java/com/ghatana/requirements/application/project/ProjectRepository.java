package com.ghatana.requirements.application.project;

import com.ghatana.requirements.domain.project.Project;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;

/**
 * Project persistence abstraction.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for project storage and retrieval operations. Implementation
 * will use core/database abstractions with PostgreSQL as backend.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProjectRepository repo = new PostgreSQLProjectRepository(dataSource);
 * 
 * // Save project
 * Project saved = repo.save(project).getResult();
 * 
 * // Find project
 * Optional<Project> found = repo.findById(projectId).getResult();
 * 
 * // List workspace projects
 * List<Project> projects = repo.findByWorkspaceId(workspaceId).getResult();
 * }</pre>
 *
 * <p><b>Integration Points</b><br>
 * - Uses core/database abstractions
 * - Returns ActiveJ Promise for async operations
 * - Throws application-specific exceptions
 *
 * @doc.type interface
 * @doc.purpose Project persistence contract
 * @doc.layer product
 * @doc.pattern Port, Repository
 */
public interface ProjectRepository {

  /**
   * Save or update project.
   *
   * @param project project to save
   * @return promise completing with saved project
   * @throws IllegalArgumentException if project is invalid
   */
  Promise<Project> save(Project project);

  /**
   * Find project by ID.
   *
   * @param projectId project identifier
   * @return promise completing with optional project
   */
  Promise<Optional<Project>> findById(String projectId);

  /**
   * Find all projects in workspace.
   *
   * @param workspaceId workspace identifier
   * @return promise completing with list of projects
   */
  Promise<List<Project>> findByWorkspaceId(String workspaceId);

  /**
   * Find all projects owned by user.
   *
   * @param ownerId owner user identifier
   * @return promise completing with list of projects
   */
  Promise<List<Project>> findByOwnerId(String ownerId);

  /**
   * Find all projects with given status.
   *
   * @param status project status name
   * @return promise completing with list of projects
   */
  Promise<List<Project>> findByStatus(String status);

  /**
   * Check if project exists.
   *
   * @param projectId project identifier
   * @return promise completing with existence check
   */
  Promise<Boolean> exists(String projectId);

  /**
   * Delete project.
   *
   * @param projectId project identifier
   * @return promise completing when deleted
   * @throws IllegalArgumentException if project not found
   */
  Promise<Void> delete(String projectId);

  /**
   * Count projects in workspace.
   *
   * @param workspaceId workspace identifier
   * @return promise completing with project count
   */
  Promise<Long> countByWorkspaceId(String workspaceId);

  /**
   * Find projects by template.
   *
   * @param templateId template identifier
   * @return promise completing with list of projects using template
   */
  Promise<List<Project>> findByTemplateId(String templateId);
}