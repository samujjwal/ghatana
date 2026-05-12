package com.ghatana.platform.http.security;

import java.util.Optional;

/**
 * Kernel-owned product entitlement context interface.
 * <p>
 * Provides server-derived identity information for route entitlement evaluation.
 * This context must be populated by trusted authentication/authorization middleware
 * and never derived from client-supplied headers.
 * </p>
 *
 * @doc.type interface
 * @doc.purpose Holds server-derived identity for entitlement evaluation
 * @doc.layer kernel
 * @doc.pattern Context
 */
public interface ProductEntitlementContext {

    /**
     * Gets the authenticated principal ID.
     *
     * @return principal ID; never null for authenticated requests
     * @throws IllegalStateException if the request is not authenticated
     */
    String getPrincipalId();

    /**
     * Gets the tenant ID.
     *
     * @return tenant ID; never null for authenticated requests
     * @throws IllegalStateException if the request is not authenticated
     */
    String getTenantId();

    /**
     * Gets the current role.
     *
     * @return role; never null for authenticated requests
     * @throws IllegalStateException if the request is not authenticated
     */
    String getRole();

    /**
     * Gets the persona.
     *
     * @return persona if available, otherwise empty
     */
    Optional<String> getPersona();

    /**
     * Gets the commercial tier.
     *
     * @return tier if available, otherwise empty
     */
    Optional<String> getTier();

    /**
     * Gets the correlation ID for request tracing.
     *
     * @return correlation ID if available, otherwise empty
     */
    Optional<String> getCorrelationId();

    /**
     * Checks if the request is authenticated.
     *
     * @return true if authenticated with a valid principal
     */
    boolean isAuthenticated();

    /**
     * Default implementation that fails closed for unauthenticated requests.
     */
    final class FailClosed implements ProductEntitlementContext {
        private final String principalId;
        private final String tenantId;
        private final String role;
        private final String persona;
        private final String tier;
        private final String correlationId;

        public FailClosed(
                String principalId,
                String tenantId,
                String role,
                String persona,
                String tier,
                String correlationId) {
            if (principalId == null || principalId.isBlank()) {
                throw new IllegalArgumentException("principalId must not be null or blank");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be null or blank");
            }
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("role must not be null or blank");
            }
            this.principalId = principalId;
            this.tenantId = tenantId;
            this.role = role;
            this.persona = persona;
            this.tier = tier;
            this.correlationId = correlationId;
        }

        @Override
        public String getPrincipalId() {
            return principalId;
        }

        @Override
        public String getTenantId() {
            return tenantId;
        }

        @Override
        public String getRole() {
            return role;
        }

        @Override
        public Optional<String> getPersona() {
            return Optional.ofNullable(persona);
        }

        @Override
        public Optional<String> getTier() {
            return Optional.ofNullable(tier);
        }

        @Override
        public Optional<String> getCorrelationId() {
            return Optional.ofNullable(correlationId);
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }
    }
}
