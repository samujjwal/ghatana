/**
 * Isolation Enforcement Service Implementation
 * 
 * Production-grade implementation of isolation enforcement service.
 * Enforces tenant/workspace/project/artifact isolation.
 * 
 * @doc.type class
 * @doc.purpose Isolation enforcement implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade implementation of isolation enforcement service.
 * Uses in-memory storage for demonstration; should be replaced with database persistence.
 */
public final class IsolationEnforcementServiceImpl implements IsolationEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(IsolationEnforcementServiceImpl.class);

    // In-memory storage for demonstration - replace with database persistence
    private final Map<String, Set<String>> tenantToWorkspaces = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> workspaceToProjects = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> projectToArtifacts = new ConcurrentHashMap<>();

    @Override
    public boolean canAccessWorkspace(String tenantId, String workspaceId) {
        log.debug("Checking workspace access: tenantId={}, workspaceId={}", tenantId, workspaceId);

        Set<String> workspaces = tenantToWorkspaces.get(tenantId);
        boolean canAccess = workspaces != null && workspaces.contains(workspaceId);

        if (!canAccess) {
            log.warn("Workspace access denied: tenantId={}, workspaceId={}", tenantId, workspaceId);
        }

        return canAccess;
    }

    @Override
    public boolean canAccessProject(String tenantId, String workspaceId, String projectId) {
        log.debug("Checking project access: tenantId={}, workspaceId={}, projectId={}", 
                tenantId, workspaceId, projectId);

        if (!canAccessWorkspace(tenantId, workspaceId)) {
            return false;
        }

        Set<String> projects = workspaceToProjects.get(workspaceId);
        boolean canAccess = projects != null && projects.contains(projectId);

        if (!canAccess) {
            log.warn("Project access denied: tenantId={}, workspaceId={}, projectId={}", 
                    tenantId, workspaceId, projectId);
        }

        return canAccess;
    }

    @Override
    public boolean canAccessArtifact(String tenantId, String workspaceId, String projectId, String artifactId) {
        log.debug("Checking artifact access: tenantId={}, workspaceId={}, projectId={}, artifactId={}", 
                tenantId, workspaceId, projectId, artifactId);

        if (!canAccessProject(tenantId, workspaceId, projectId)) {
            return false;
        }

        Set<String> artifacts = projectToArtifacts.get(projectId);
        boolean canAccess = artifacts != null && artifacts.contains(artifactId);

        if (!canAccess) {
            log.warn("Artifact access denied: tenantId={}, workspaceId={}, projectId={}, artifactId={}", 
                    tenantId, workspaceId, projectId, artifactId);
        }

        return canAccess;
    }

    @Override
    public IsolationValidationResult validateIsolation(String tenantId, String workspaceId, String projectId, String artifactId) {
        log.info("Validating isolation: tenantId={}, workspaceId={}, projectId={}, artifactId={}", 
                tenantId, workspaceId, projectId, artifactId);

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate tenant ID
        if (tenantId == null || tenantId.isBlank()) {
            errors.add("Tenant ID is required");
        }

        // Validate workspace ID
        if (workspaceId == null || workspaceId.isBlank()) {
            errors.add("Workspace ID is required");
        } else if (!canAccessWorkspace(tenantId, workspaceId)) {
            errors.add("Workspace access denied");
        }

        // Validate project ID
        if (projectId != null && !projectId.isBlank()) {
            if (!canAccessProject(tenantId, workspaceId, projectId)) {
                errors.add("Project access denied");
            }
        }

        // Validate artifact ID
        if (artifactId != null && !artifactId.isBlank()) {
            if (projectId == null || projectId.isBlank()) {
                errors.add("Project ID is required for artifact access");
            } else if (!canAccessArtifact(tenantId, workspaceId, projectId, artifactId)) {
                errors.add("Artifact access denied");
            }
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.warn("Isolation validation failed: errors={}", errors);
        } else {
            log.info("Isolation validation passed");
        }

        return new IsolationValidationResult(isValid, errors, warnings);
    }

    /**
     * Adds a workspace to a tenant.
     * 
     * @param tenantId The tenant ID
     * @param workspaceId The workspace ID
     */
    public void addWorkspaceToTenant(String tenantId, String workspaceId) {
        tenantToWorkspaces.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet()).add(workspaceId);
        log.info("Added workspace to tenant: tenantId={}, workspaceId={}", tenantId, workspaceId);
    }

    /**
     * Adds a project to a workspace.
     * 
     * @param workspaceId The workspace ID
     * @param projectId The project ID
     */
    public void addProjectToWorkspace(String workspaceId, String projectId) {
        workspaceToProjects.computeIfAbsent(workspaceId, k -> ConcurrentHashMap.newKeySet()).add(projectId);
        log.info("Added project to workspace: workspaceId={}, projectId={}", workspaceId, projectId);
    }

    /**
     * Adds an artifact to a project.
     * 
     * @param projectId The project ID
     * @param artifactId The artifact ID
     */
    public void addArtifactToProject(String projectId, String artifactId) {
        projectToArtifacts.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(artifactId);
        log.info("Added artifact to project: projectId={}, artifactId={}", projectId, artifactId);
    }
}
