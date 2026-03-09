package com.ghatana.platform.governance.security;

import java.util.Optional;

/**
 * Thread-local context for storing the current tenant and principal information.
 * Provides centralized access to security context without explicit parameter passing through call stacks.
 *
 * <h2>Purpose</h2>
 * Maintains thread-scoped security context (tenant ID, principal) for multi-tenant isolation:
 * <ul>
 *   <li>Thread-local storage of current tenant ID and principal</li>
 *   <li>Implicit propagation of security context across service layers</li>
 *   <li>Automatic tenant isolation enforcement at data access points</li>
 *   <li>Support for scoped context transitions with automatic cleanup</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * Central governance abstraction for multi-tenancy:
 * <ul>
 *   <li><b>Set by</b>: HTTP middleware, gRPC interceptors, request filters</li>
 *   <li><b>Accessed by</b>: Repositories, services, business logic layers</li>
 *   <li><b>Enforces</b>: Tenant isolation at database access time</li>
 *   <li><b>Related to</b>: {@link Principal}, SecurityContext</li>
 * </ul>
 *
 * <h2>Thread-Local Semantics</h2>
 * Uses ThreadLocal to store per-thread context:
 * <ul>
 *   <li>Each thread has independent tenant/principal context</li>
 *   <li>No cross-thread contamination in multi-threaded servers</li>
 *   <li>Automatic isolation in async/thread-pool scenarios (with care)</li>
 *   <li>MUST clean up on thread termination (use scope() for automatic cleanup)</li>
 * </ul>
 *
 * <h2>Multi-Tenant Isolation Patterns</h2>
 * {@code
 * // At request entry point (controller/handler)
 * try (AutoCloseable scope = TenantContext.scope(principal)) {
 *     // All downstream code accesses tenant via TenantContext.getCurrentTenantId()
 *     List<Order> orders = orderRepository.findByTenant();  // Uses implicit tenant
 *     // ...
 * }
 *
 * // At repository layer (applies tenant filter automatically)
 * public List<Order> findByTenant() {
 *     String tenantId = TenantContext.getCurrentTenantId();
 *     return db.query("SELECT * FROM orders WHERE tenant_id = ?", tenantId);
 * }
 *
 * // Manual tenant access
 * Optional<Principal> principal = TenantContext.current();
 * if (principal.isPresent()) {
 *     String userId = principal.get().getUserId();
 *     // ...
 * }
 * }
 *
 * <h2>Default Tenant Handling</h2>
 * If no tenant is set in ThreadLocal, defaults to "default-tenant".
 * This enables graceful fallback for single-tenant or development scenarios.
 *
 * <h2>Scope Pattern for Async/Batch Operations</h2>
 * For operations that spawn new threads (thread pools, async executors):
 * {@code
 * // ✅ CORRECT: Explicit scope transfer
 * String originalTenant = TenantContext.getCurrentTenantId();
 * Principal originalPrincipal = TenantContext.current().orElse(null);
 * 
 * executor.submit(() -> {
 *     try (AutoCloseable scope = TenantContext.scope(originalPrincipal)) {
 *         // New thread has correct tenant/principal context
 *         processAsyncWork();
 *     }
 * });
 *
 * // ❌ WRONG: ThreadLocal doesn't transfer to new threads automatically
 * executor.submit(() -> {
 *     String tenantId = TenantContext.getCurrentTenantId();  // Will be null or "default-tenant"!
 * });
 * }
 *
 * <h2>Lifecycle & Cleanup</h2>
 * <ul>
 *   <li><b>Setup</b>: Call scope(principal) at request entry point</li>
 *   <li><b>Propagation</b>: Automatically available in same thread via getCurrentTenantId()</li>
 *   <li><b>Cleanup</b>: AutoCloseable scope handles cleanup on try-with-resources exit</li>
 *   <li><b>Critical</b>: Manual cleanup() should be called if scope not used</li>
 * </ul>
 *
 * @see Principal The principal type stored in context
 * @see SecurityContext Related security context abstraction
 * @doc.type context-holder
 * @doc.layer core
 * @doc.purpose thread-local tenant and principal context management
 * @doc.pattern thread-local implicit-context-propagation multi-tenant-isolation
 */
public class TenantContext {
    private static final ThreadLocal<Principal> CURRENT_PRINCIPAL = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TENANT_ID = new ThreadLocal<>();
    
    private TenantContext() {
        // Utility class
    }
    
    /**
     * Gets the current tenant ID.
     *
     * @return The current tenant ID, or "default-tenant" if not set
     */
    public static String getCurrentTenantId() {
        String tenantId = CURRENT_TENANT_ID.get();
        return tenantId != null ? tenantId : "default-tenant";
    }
    
    /**
     * Sets the current tenant ID.
     *
     * @param tenantId The tenant ID to set
     */
    public static void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }
    
    /**
     * Gets the current principal.
     *
     * @return An Optional containing the current principal, or empty if not set
     */
    public static Optional<Principal> current() {
        return Optional.ofNullable(CURRENT_PRINCIPAL.get());
    }
    
    /**
     * A scope handle that can be closed without throwing checked exceptions.
     * Used with try-with-resources to restore the previous tenant context.
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Creates a scope with the given principal.
     * The principal will be set for the current thread and automatically cleared when the scope is closed.
     *
     * @param principal The principal to set
     * @return A Scope that clears the principal when closed
     */
    public static Scope scope(Principal principal) {
        Principal previous = CURRENT_PRINCIPAL.get();
        String previousTenantId = CURRENT_TENANT_ID.get();
        
        CURRENT_PRINCIPAL.set(principal);
        if (principal != null) {
            CURRENT_TENANT_ID.set(principal.getTenantId());
        }
        
        return () -> {
            CURRENT_PRINCIPAL.set(previous);
            CURRENT_TENANT_ID.set(previousTenantId);
        };
    }
    
    /**
     * Clears the current principal and tenant context.
     */
    public static void clear() {
        CURRENT_PRINCIPAL.remove();
        CURRENT_TENANT_ID.remove();
    }
}
