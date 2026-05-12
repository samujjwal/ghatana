package com.ghatana.platform.database;

import java.util.Optional;

/**
 * Kernel-owned data access context for database operations.
 * <p>
 * Provides tenant, principal, and request correlation information to database
 * operations for audit logging, row-level security, and observability.
 * </p>
 *
 * @doc.type interface
 * @doc.purpose Provides tenant and principal context for database operations
 * @doc.layer kernel
 * @doc.pattern Context
 */
public interface DataAccessContext {

    /**
     * Gets the tenant ID for multi-tenancy.
     *
     * @return tenant ID; never null for authenticated requests
     * @throws IllegalStateException if the context is not initialized
     */
    String getTenantId();

    /**
     * Gets the principal/user ID.
     *
     * @return principal ID; never null for authenticated requests
     * @throws IllegalStateException if the context is not initialized
     */
    String getPrincipalId();

    /**
     * Gets the correlation ID for request tracing.
     *
     * @return correlation ID if available, otherwise empty
     */
    Optional<String> getCorrelationId();

    /**
     * Gets the request ID for tracking.
     *
     * @return request ID if available, otherwise empty
     */
    Optional<String> getRequestId();

    /**
     * Gets the client IP address.
     *
     * @return client IP if available, otherwise empty
     */
    Optional<String> getClientIp();

    /**
     * Gets the user agent string.
     *
     * @return user agent if available, otherwise empty
     */
    Optional<String> getUserAgent();

    /**
     * Checks if the context is initialized with valid values.
     *
     * @return true if tenantId and principalId are present
     */
    boolean isInitialized();

    /**
     * Default implementation that fails closed for uninitialized contexts.
     */
    final class Default implements DataAccessContext {
        private final String tenantId;
        private final String principalId;
        private final String correlationId;
        private final String requestId;
        private final String clientIp;
        private final String userAgent;

        public Default(
                String tenantId,
                String principalId,
                String correlationId,
                String requestId,
                String clientIp,
                String userAgent) {
            this.tenantId = tenantId;
            this.principalId = principalId;
            this.correlationId = correlationId;
            this.requestId = requestId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
        }

        @Override
        public String getTenantId() {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalStateException("DataAccessContext not initialized: tenantId is null or blank");
            }
            return tenantId;
        }

        @Override
        public String getPrincipalId() {
            if (principalId == null || principalId.isBlank()) {
                throw new IllegalStateException("DataAccessContext not initialized: principalId is null or blank");
            }
            return principalId;
        }

        @Override
        public Optional<String> getCorrelationId() {
            return Optional.ofNullable(correlationId).filter(s -> !s.isBlank());
        }

        @Override
        public Optional<String> getRequestId() {
            return Optional.ofNullable(requestId).filter(s -> !s.isBlank());
        }

        @Override
        public Optional<String> getClientIp() {
            return Optional.ofNullable(clientIp).filter(s -> !s.isBlank());
        }

        @Override
        public Optional<String> getUserAgent() {
            return Optional.ofNullable(userAgent).filter(s -> !s.isBlank());
        }

        @Override
        public boolean isInitialized() {
            return tenantId != null && !tenantId.isBlank()
                && principalId != null && !principalId.isBlank();
        }
    }
}
