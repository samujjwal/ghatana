package com.ghatana.platform.governance.security;

import java.util.List;
import java.util.Objects;

/**
 * Represents an authenticated principal (user or service) in the system.
 * Contains identity information, roles, and tenant association for authorization and isolation.
 *
 * <h2>Purpose</h2>
 * Encapsulates authenticated identity with security attributes:
 * <ul>
 *   <li>User/service identity (name/identifier)</li>
 *   <li>Role-based authorization (RBAC roles)</li>
 *   <li>Multi-tenant isolation (tenant ID)</li>
 *   <li>Immutable security attributes (defensive copied)</li>
 * </ul>
 *
 * <h2>Structure</h2>
 * Immutable value object with:
 * <ul>
 *   <li><b>name</b>: Unique principal identifier (email, username, service ID)</li>
 *   <li><b>roles</b>: Unmodifiable list of role strings (e.g., "admin", "viewer", "editor")</li>
 *   <li><b>tenantId</b>: Multi-tenant isolation key (defaults to "default-tenant")</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * Central identity abstraction for security and governance:
 * <ul>
 *   <li><b>Created by</b>: Authentication services, JWT token processors, OAuth providers</li>
 *   <li><b>Used by</b>: TenantContext, authorization filters, access control checks</li>
 *   <li><b>Stored in</b>: Thread-local TenantContext for request propagation</li>
 *   <li><b>Related to</b>: TenantContext, SecurityContext, RBAC</li>
 * </ul>
 *
 * <h2>Immutability & Safety</h2>
 * <ul>
 *   <li>All fields final and private</li>
 *   <li>Roles list defensively copied (List.copyOf for immutable copy)</li>
 *   <li>No setters - create new instance for different principal</li>
 *   <li>equals/hashCode based on name + tenantId (tenant-scoped identity)</li>
 *   <li>Safe to share across threads without synchronization</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * {@code
 * // Create principal for user
 * Principal user = new Principal("alice@example.com", List.of("viewer", "editor"), "acme-tenant");
 *
 * // Create principal for service account
 * Principal service = new Principal("fraud-detector-svc", List.of("processor", "admin"), "platform-tenant");
 *
 * // Check authorization
 * if (user.hasRole("admin")) {
 *     // Admin-only operation
 * }
 *
 * // Set in thread context
 * try (AutoCloseable scope = TenantContext.scope(user)) {
 *     // All downstream code has user's tenant/identity
 *     Order order = orderRepository.findById(orderId);  // Tenant-isolated query
 * }
 *
 * // Role-based access control
 * boolean canDelete = user.hasRole("admin") || user.hasRole("owner");
 * }
 *
 * <h2>Role Patterns</h2>
 * <ul>
 *   <li><b>admin</b>: Full system access</li>
 *   <li><b>viewer</b>: Read-only access</li>
 *   <li><b>editor</b>: Read and write access</li>
 *   <li><b>processor</b>: System processor/service account</li>
 *   <li><b>owner</b>: Owner of specific resource</li>
 *   <li>Custom roles supported for domain-specific authorization</li>
 * </ul>
 *
 * <h2>Tenant Scoping</h2>
 * Two principals are equal only if both name AND tenantId match:
 * {@code
 * // These are different principals (different tenants)
 * Principal p1 = new Principal("alice", List.of("admin"), "tenant-1");
 * Principal p2 = new Principal("alice", List.of("admin"), "tenant-2");
 * assert !p1.equals(p2);  // Different tenants = different principals
 * }
 *
 * @see TenantContext Stores and propagates principals via thread-local
 * @see SecurityContext Related security context abstraction
 * @doc.type value-object
 * @doc.layer core
 * @doc.purpose authenticated principal with identity, roles, and tenant association
 * @doc.pattern immutable value-object tenant-scoped role-based-authorization
 */
public class Principal {
    private final String name;
    private final List<String> roles;
    private final String tenantId;
    
    public Principal(String name, List<String> roles) {
        this(name, roles, "default-tenant");
    }
    
    public Principal(String name, List<String> roles, String tenantId) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.roles = List.copyOf(Objects.requireNonNull(roles, "roles cannot be null"));
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
    }
    
    public String getName() {
        return name;
    }
    
    public List<String> getRoles() {
        return roles;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Principal principal = (Principal) o;
        return name.equals(principal.name) && 
               tenantId.equals(principal.tenantId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, tenantId);
    }
    
    @Override
    public String toString() {
        return "Principal{name='" + name + "', tenantId='" + tenantId + "', roles=" + roles + "}";
    }
}
