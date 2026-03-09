package com.ghatana.platform.governance.security;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Enforces tenant isolation for multi-tenant operations.
 * 
 * <p>Provides explicit tenant validation and enforcement mechanisms
 * to prevent cross-tenant data access. Used at repository and service
 * layer boundaries to ensure all operations respect tenant isolation.
 * 
 * <h2>Usage Patterns</h2>
 * <pre>{@code
 * // At repository layer - validate before query
 * TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();
 * 
 * public List<Order> findAll() {
 *     enforcer.requireAuthenticated();
 *     return db.query("SELECT * FROM orders WHERE tenant_id = ?", 
 *         enforcer.getTenantIdOrThrow());
 * }
 * 
 * // Validate entity belongs to current tenant before returning
 * public Order findById(String id) {
 *     Order order = db.findById(id);
 *     enforcer.validateTenantAccess(order.getTenantId());
 *     return order;
 * }
 * 
 * // Execute with explicit tenant context
 * TenantIsolationEnforcer.executeAs(tenantId, principal, () -> {
 *     // Code runs with specified tenant context
 *     return processOrder();
 * });
 * }</pre>
 * 
 * <h2>Security Model</h2>
 * <ul>
 *   <li>Fails fast if no tenant context is set (prevents default fallback in production)</li>
 *   <li>Validates entity tenant matches current tenant before access</li>
 *   <li>Provides audit logging hooks for tenant isolation violations</li>
 *   <li>Supports explicit tenant override for admin/system operations</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Enforce tenant isolation at data access boundaries
 * @doc.layer core
 * @doc.pattern Security Enforcer
 * @see TenantContext
 * @see Principal
 */
public class TenantIsolationEnforcer {

    private static final String DEFAULT_TENANT = "default-tenant";
    
    private final boolean strictMode;
    private final TenantViolationHandler violationHandler;

    /**
     * Create enforcer for current thread's tenant context.
     * Uses strict mode by default (fails if no tenant set).
     */
    public static TenantIsolationEnforcer forCurrentTenant() {
        return new TenantIsolationEnforcer(true, TenantViolationHandler.THROW);
    }

    /**
     * Create enforcer with lenient mode (allows default tenant fallback).
     * Use only for development/testing.
     */
    public static TenantIsolationEnforcer lenient() {
        return new TenantIsolationEnforcer(false, TenantViolationHandler.LOG_AND_ALLOW);
    }

    /**
     * Create enforcer with custom violation handler.
     *
     * @param strictMode if true, fails when no tenant context is set
     * @param handler handler for tenant violations
     */
    public TenantIsolationEnforcer(boolean strictMode, TenantViolationHandler handler) {
        this.strictMode = strictMode;
        this.violationHandler = Objects.requireNonNull(handler, "handler required");
    }

    /**
     * Get current tenant ID, throwing if not set in strict mode.
     *
     * @return current tenant ID
     * @throws TenantIsolationException if no tenant context and strict mode
     */
    public String getTenantIdOrThrow() {
        String tenantId = TenantContext.getCurrentTenantId();
        
        if (strictMode && DEFAULT_TENANT.equals(tenantId)) {
            Optional<Principal> principal = TenantContext.current();
            if (principal.isEmpty()) {
                throw new TenantIsolationException(
                    "No tenant context set. Ensure TenantContext.scope() is called at request entry point.");
            }
        }
        
        return tenantId;
    }

    /**
     * Get current tenant ID, or empty if not set.
     *
     * @return optional tenant ID
     */
    public Optional<String> getTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (DEFAULT_TENANT.equals(tenantId) && TenantContext.current().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tenantId);
    }

    /**
     * Require that a principal is authenticated.
     *
     * @throws TenantIsolationException if no principal in context
     */
    public void requireAuthenticated() {
        if (TenantContext.current().isEmpty()) {
            throw new TenantIsolationException(
                "Authentication required. No principal in TenantContext.");
        }
    }

    /**
     * Require that the current principal has one of the specified roles.
     *
     * @param roles required roles (principal must have at least one)
     * @throws TenantIsolationException if principal lacks required role
     */
    public void requireRole(String... roles) {
        requireAuthenticated();
        Principal principal = TenantContext.current().get();
        
        for (String role : roles) {
            if (principal.hasRole(role)) {
                return;
            }
        }
        
        throw new TenantIsolationException(
            "Access denied. Required roles: " + String.join(", ", roles) + 
            ". Principal roles: " + principal.getRoles());
    }

    /**
     * Validate that the entity's tenant matches the current tenant.
     * 
     * <p>Call this before returning any entity from a repository to ensure
     * the caller has access to the entity.
     *
     * @param entityTenantId tenant ID of the entity being accessed
     * @throws TenantIsolationException if tenant mismatch
     */
    public void validateTenantAccess(String entityTenantId) {
        String currentTenantId = getTenantIdOrThrow();
        
        if (!currentTenantId.equals(entityTenantId)) {
            TenantViolation violation = new TenantViolation(
                currentTenantId, 
                entityTenantId, 
                TenantContext.current().map(Principal::getName).orElse("unknown"),
                "Cross-tenant access attempt"
            );
            violationHandler.handle(violation);
        }
    }

    /**
     * Execute code with explicit tenant context.
     * 
     * <p>Use for admin/system operations that need to access specific tenant data.
     *
     * @param tenantId tenant to execute as
     * @param principal principal to use (or null for system context)
     * @param operation the operation to execute
     * @param <T> result type
     * @return operation result
     */
    public static <T> T executeAs(String tenantId, Principal principal, Supplier<T> operation) {
        Principal effectivePrincipal = principal != null ? principal : 
            new Principal("system", java.util.List.of("system"), tenantId);
        
        try (AutoCloseable scope = TenantContext.scope(effectivePrincipal)) {
            return operation.get();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to execute as tenant: " + tenantId, e);
        }
    }

    /**
     * Execute void operation with explicit tenant context.
     */
    public static void executeAsVoid(String tenantId, Principal principal, Runnable operation) {
        executeAs(tenantId, principal, () -> {
            operation.run();
            return null;
        });
    }

    /**
     * Records a tenant isolation violation.
     */
    public record TenantViolation(
        String requestedTenantId,
        String actualTenantId,
        String principalName,
        String message
    ) {}

    /**
     * Handler for tenant isolation violations.
     */
    @FunctionalInterface
    public interface TenantViolationHandler {
        
        /**
         * Throw exception on violation (default for production).
         */
        TenantViolationHandler THROW = violation -> {
            throw new TenantIsolationException(
                "Tenant isolation violation: " + violation.message() +
                ". Requested tenant: " + violation.requestedTenantId() +
                ", Actual tenant: " + violation.actualTenantId() +
                ", Principal: " + violation.principalName());
        };

        /**
         * Log and allow (for development/migration only).
         */
        TenantViolationHandler LOG_AND_ALLOW = violation -> {
            System.err.println("[TENANT_VIOLATION] " + violation);
            // In production, this would be a proper audit log
        };

        void handle(TenantViolation violation);
    }

    /**
     * Exception thrown when tenant isolation is violated.
     */
    public static class TenantIsolationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        
        public TenantIsolationException(String message) {
            super(message);
        }

        public TenantIsolationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
