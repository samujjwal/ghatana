package com.ghatana.digitalmarketing.application.admin;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of AdminService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides platform administration operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class AdminServiceImpl implements AdminService {

    private final ConcurrentMap<String, Tenant> tenants = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
    private final List<AuditLogEntry> auditLog = List.of();

    @Override
    public Promise<Tenant> createTenant(DmOperationContext ctx, CreateTenantRequest request) {
        String tenantId = "TENANT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        Tenant tenant = new Tenant(
            tenantId,
            request.name(),
            request.domain(),
            "ACTIVE",
            Instant.now().toString(),
            request.metadata()
        );

        tenants.put(tenantId, tenant);
        return Promise.complete(tenant);
    }

    @Override
    public Promise<Tenant> getTenant(DmOperationContext ctx, String tenantId) {
        Tenant tenant = tenants.get(tenantId);
        if (tenant == null) {
            return Promise.ofException(new IllegalArgumentException("Tenant not found: " + tenantId));
        }
        return Promise.complete(tenant);
    }

    @Override
    public Promise<Tenant> updateTenant(DmOperationContext ctx, String tenantId, UpdateTenantRequest request) {
        Tenant existing = tenants.get(tenantId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Tenant not found: " + tenantId));
        }

        Tenant updated = new Tenant(
            tenantId,
            request.name() != null ? request.name() : existing.name(),
            existing.domain(),
            request.status() != null ? request.status() : existing.status(),
            existing.createdAt(),
            request.metadata() != null ? request.metadata() : existing.metadata()
        );

        tenants.put(tenantId, updated);
        return Promise.complete(updated);
    }

    @Override
    public Promise<User> createUser(DmOperationContext ctx, CreateUserRequest request) {
        String userId = "USER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        User user = new User(
            userId,
            request.tenantId(),
            request.email(),
            request.name(),
            request.roles(),
            Instant.now().toString()
        );

        users.put(userId, user);
        return Promise.complete(user);
    }

    @Override
    public Promise<User> updateUserRoles(DmOperationContext ctx, String userId, List<String> roles) {
        User existing = users.get(userId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("User not found: " + userId));
        }

        User updated = new User(
            userId,
            existing.tenantId(),
            existing.email(),
            existing.name(),
            roles,
            existing.createdAt()
        );

        users.put(userId, updated);
        return Promise.complete(updated);
    }

    @Override
    public Promise<List<AuditLogEntry>> fetchAuditLog(DmOperationContext ctx, int limit) {
        return Promise.complete(auditLog.stream().limit(limit).toList());
    }

    @Override
    public Promise<SystemHealth> getSystemHealth(DmOperationContext ctx) {
        SystemHealth health = new SystemHealth(
            true,
            Map.of(
                "database", "healthy",
                "cache", "healthy",
                "queue", "healthy"
            ),
            Instant.now().toString()
        );
        return Promise.complete(health);
    }
}
