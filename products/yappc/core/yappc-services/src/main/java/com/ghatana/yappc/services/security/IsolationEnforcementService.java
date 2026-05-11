/**
 * Isolation Enforcement Service
 * 
 * Enforces tenant/workspace/project/artifact isolation.
 * Ensures no leakage across scopes and proper isolation boundaries.
 * 
 * @doc.type interface
 * @doc.purpose Isolation enforcement
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.security;

/**
 * Service interface for enforcing isolation.
 */
public interface IsolationEnforcementService {

    /**
     * Checks if a tenant can access a workspace.
     * 
     * @param tenantId The tenant ID
     * @param workspaceId The workspace ID
     * @return true if access is allowed, false otherwise
     */
    boolean canAccessWorkspace(String tenantId, String workspaceId);

    /**
     * Checks if a workspace can access a project.
     * 
     * @param tenantId The tenant ID
     * @param workspaceId The workspace ID
     * @param projectId The project ID
     * @return true if access is allowed, false otherwise
     */
    boolean canAccessProject(String tenantId, String workspaceId, String projectId);

    /**
     * Checks if a project can access an artifact.
     * 
     * @param tenantId The tenant ID
     * @param workspaceId The workspace ID
     * @param projectId The project ID
     * @param artifactId The artifact ID
     * @return true if access is allowed, false otherwise
     */
    boolean canAccessArtifact(String tenantId, String workspaceId, String projectId, String artifactId);

    /**
     * Validates isolation for a request.
     * 
     * @param tenantId The tenant ID
     * @param workspaceId The workspace ID
     * @param projectId The project ID
     * @param artifactId The artifact ID (optional)
     * @return IsolationValidationResult containing validation status and any errors
     */
    IsolationValidationResult validateIsolation(String tenantId, String workspaceId, String projectId, String artifactId);
}

/**
 * Isolation validation result.
 */
record IsolationValidationResult(
    boolean isValid,
    java.util.List<String> errors,
    java.util.List<String> warnings
) {
    public IsolationValidationResult {
        if (errors == null) {
            errors = java.util.List.of();
        }
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}
