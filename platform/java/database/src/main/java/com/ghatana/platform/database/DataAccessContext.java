package com.ghatana.platform.database;

import java.util.Optional;
import java.util.Map;

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
     * Gets the audit classification for the operation.
     *
     * @return audit classification if available, otherwise empty
     */
    Optional<String> getAuditClassification();

    /**
     * Gets the product-scoped data owner or resource scope.
     *
     * @return data owner scope if available, otherwise empty
     */
    Optional<String> getDataOwnerScope();

    /**
     * Gets the idempotency key for mutating operations.
     *
     * @return idempotency key if supplied, otherwise empty
     */
    Optional<String> getIdempotencyKey();

    /**
     * Returns the idempotency key or fails closed for mutating operations that require it.
     *
     * @return non-blank idempotency key
     * @throws IllegalStateException if the idempotency key is not initialized
     */
    String requireIdempotencyKey();

    /**
     * Gets safe scalar product metadata for audit, policy, and observability.
     *
     * @return immutable metadata map
     */
    Map<String, String> getMetadata();

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
        private final String auditClassification;
        private final String dataOwnerScope;
        private final String idempotencyKey;
        private final Map<String, String> metadata;

        public Default(
                String tenantId,
                String principalId,
                String correlationId,
                String requestId,
                String clientIp,
                String userAgent) {
            this(tenantId, principalId, correlationId, requestId, clientIp, userAgent, null, null, null, Map.of());
        }

        public Default(
                String tenantId,
                String principalId,
                String correlationId,
                String requestId,
                String clientIp,
                String userAgent,
                String auditClassification,
                String dataOwnerScope,
                String idempotencyKey,
                Map<String, String> metadata) {
            this.tenantId = tenantId;
            this.principalId = principalId;
            this.correlationId = correlationId;
            this.requestId = requestId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.auditClassification = auditClassification;
            this.dataOwnerScope = dataOwnerScope;
            this.idempotencyKey = idempotencyKey;
            this.metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
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
        public Optional<String> getAuditClassification() {
            return Optional.ofNullable(auditClassification).filter(s -> !s.isBlank());
        }

        @Override
        public Optional<String> getDataOwnerScope() {
            return Optional.ofNullable(dataOwnerScope).filter(s -> !s.isBlank());
        }

        @Override
        public Optional<String> getIdempotencyKey() {
            return Optional.ofNullable(idempotencyKey).filter(s -> !s.isBlank());
        }

        @Override
        public String requireIdempotencyKey() {
            return getIdempotencyKey()
                .orElseThrow(() -> new IllegalStateException("DataAccessContext not initialized: idempotencyKey is null or blank"));
        }

        @Override
        public Map<String, String> getMetadata() {
            return metadata;
        }

        @Override
        public boolean isInitialized() {
            return tenantId != null && !tenantId.isBlank()
                && principalId != null && !principalId.isBlank();
        }
    }
}
