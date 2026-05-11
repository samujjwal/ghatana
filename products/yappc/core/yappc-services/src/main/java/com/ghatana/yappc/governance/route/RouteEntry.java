package com.ghatana.yappc.governance.route;

import java.util.List;
import java.util.Set;

/**
 * @doc.type record
 * @doc.purpose Represents a single route entry from the route manifest
 * @doc.layer governance
 * @doc.pattern Data Record
 */
public record RouteEntry(
    String method,
    String path,
    AuthMode auth,
    Set<String> scopes,
    String owner,
    Boundary boundary,
    String operationId,
    String auditEventType,
    PrivacyClassification privacyClassification
) {
    /**
     * Validates that this route entry has consistent configuration.
     * 
     * @throws IllegalStateException if the configuration is invalid
     */
    public void validate() {
        if (method == null || method.isBlank()) {
            throw new IllegalStateException("Route entry must have a method");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Route entry must have a path");
        }
        if (auth == null) {
            throw new IllegalStateException("Route entry must have an auth mode");
        }
        if (scopes == null) {
            throw new IllegalStateException("Route entry must have a scopes set (can be empty)");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalStateException("Route entry must have an owner");
        }
        if (boundary == null) {
            throw new IllegalStateException("Route entry must have a boundary");
        }
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalStateException("Route entry must have an operationId");
        }
        if (auditEventType == null || auditEventType.isBlank()) {
            throw new IllegalStateException("Route entry must have an audit event type");
        }
        if (privacyClassification == null) {
            throw new IllegalStateException("Route entry must have a privacy classification");
        }
        
        // Validate auth/scopes consistency
        if (auth == AuthMode.REQUIRED && scopes.isEmpty()) {
            throw new IllegalStateException(
                String.format("Route %s %s has auth=required but empty scopes", method, path)
            );
        }
        if (auth == AuthMode.PUBLIC && !scopes.isEmpty()) {
            throw new IllegalStateException(
                String.format("Route %s %s has auth=public but non-empty scopes: %s", method, path, scopes)
            );
        }
    }
}
