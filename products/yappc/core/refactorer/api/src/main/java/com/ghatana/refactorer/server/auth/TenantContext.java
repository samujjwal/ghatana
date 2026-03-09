package com.ghatana.refactorer.server.auth;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical representation of request-scoped tenant information. Instances are immutable and safe

 * to share across threads.

 *

 * @doc.type record

 * @doc.purpose Capture tenant, subject, roles, and claims for downstream authorization decisions.

 * @doc.layer product

 * @doc.pattern Value Object

 */

public record TenantContext(
        String tenantId, String subject, Set<String> roles, Map<String, String> claims) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        Objects.requireNonNull(claims, "claims must not be null");
    }

    public static TenantContext of(
            String tenantId, String subject, Set<String> roles, Map<String, String> claims) {
        return new TenantContext(
                tenantId,
                subject,
                Collections.unmodifiableSet(Set.copyOf(roles)),
                Collections.unmodifiableMap(Map.copyOf(claims)));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
