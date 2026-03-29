/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.e2e;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for tenant isolation and multi-tenancy.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantIsolationE2ETest extends EventloopTestBase {

    private static MultiTenantPlatform platform;
    private static String tenantA = "tenant-a-" + UUID.randomUUID();
    private static String tenantB = "tenant-b-" + UUID.randomUUID();
    private static String projectA1, projectA2, projectB1;

    @BeforeAll
    static void setUpPlatform() {
        platform = new MockMultiTenantPlatform();
    }

    @Test
    @Order(1)
    @DisplayName("E2E: Create projects in different tenants")
    void testCreateProjectsInDifferentTenants() throws Exception {
        // Create project in Tenant A
        TenantProjectRequest requestA1 = TenantProjectRequest.builder()
                .tenantId(tenantA)
                .projectName("Tenant A Project 1")
                .intent("Build API")
                .build();

        Promise<TenantProject> promiseA1 = platform.createProject(requestA1);
        TenantProject projectA = runPromise(() -> promiseA1);
        projectA1 = projectA.id();

        assertThat(projectA).isNotNull();
        assertThat(projectA.tenantId()).isEqualTo(tenantA);
        assertThat(projectA.name()).isEqualTo("Tenant A Project 1");

        // Create second project in Tenant A
        TenantProjectRequest requestA2 = TenantProjectRequest.builder()
                .tenantId(tenantA)
                .projectName("Tenant A Project 2")
                .intent("Build Frontend")
                .build();

        TenantProject projectA2Result = runPromise(() -> platform.createProject(requestA2));
        projectA2 = projectA2Result.id();

        // Create project in Tenant B
        TenantProjectRequest requestB1 = TenantProjectRequest.builder()
                .tenantId(tenantB)
                .projectName("Tenant B Project 1")
                .intent("Build Database")
                .build();

        TenantProject projectB = runPromise(() -> platform.createProject(requestB1));
        projectB1 = projectB.id();

        assertThat(projectB.tenantId()).isEqualTo(tenantB);
    }

    @Test
    @Order(2)
    @DisplayName("E2E: Tenant A cannot access Tenant B's projects")
    void testTenantIsolationAccessControl() throws Exception {
        // Tenant A tries to access Tenant B's project
        Promise<TenantProject> promise = platform.getProject(projectB1, tenantA);
        TenantProject project = runPromise(() -> promise);

        assertThat(project).isNull(); // Access denied, returns null

        // Verify access was denied
        Promise<AccessLog[]> logs = platform.getAccessLogs(tenantA);
        AccessLog[] accessLogs = runPromise(() -> logs);

        assertThat(accessLogs).anyMatch(log -> 
            log.action().equals("ACCESS_DENIED") && log.resource().contains(projectB1));
    }

    @Test
    @Order(3)
    @DisplayName("E2E: Each tenant only sees their own projects")
    void testTenantDataIsolation() throws Exception {
        // List projects for Tenant A
        Promise<TenantProject[]> promiseA = platform.listProjects(tenantA);
        TenantProject[] projectsA = runPromise(() -> promiseA);

        assertThat(projectsA).hasSize(2);
        for (TenantProject project : projectsA) {
            assertThat(project.tenantId()).isEqualTo(tenantA);
        }

        // List projects for Tenant B
        Promise<TenantProject[]> promiseB = platform.listProjects(tenantB);
        TenantProject[] projectsB = runPromise(() -> promiseB);

        assertThat(projectsB).hasSize(1);
        assertThat(projectsB[0].tenantId()).isEqualTo(tenantB);
    }

    @Test
    @Order(4)
    @DisplayName("E2E: Tenant data is isolated in events")
    void testEventTenantIsolation() throws Exception {
        // Publish events from both tenants
        platform.publishEvent(tenantA, "PROJECT_UPDATED", Map.of("projectId", projectA1)).get();
        platform.publishEvent(tenantB, "PROJECT_UPDATED", Map.of("projectId", projectB1)).get();

        // Consume events for Tenant A
        Promise<TenantEvent[]> eventsA = platform.consumeEvents(tenantA, 10);
        TenantEvent[] tenantAEvents = runPromise(() -> eventsA);

        // All events should be from Tenant A
        for (TenantEvent event : tenantAEvents) {
            assertThat(event.tenantId()).isEqualTo(tenantA);
        }

        // Tenant A should not see Tenant B's events
        assertThat(tenantAEvents).noneMatch(e -> e.tenantId().equals(tenantB));
    }

    @Test
    @Order(5)
    @DisplayName("E2E: User authentication is tenant-scoped")
    void testTenantScopedAuthentication() throws Exception {
        // Create user in Tenant A
        TenantUserRequest userRequest = TenantUserRequest.builder()
                .tenantId(tenantA)
                .username("user@example.com")
                .password("password123")
                .build();

        Promise<TenantUser> userPromise = platform.createUser(userRequest);
        TenantUser user = runPromise(() -> userPromise);

        assertThat(user.tenantId()).isEqualTo(tenantA);

        // User from Tenant A cannot authenticate in Tenant B context
        TenantAuthRequest authRequest = TenantAuthRequest.builder()
                .tenantId(tenantB) // Wrong tenant
                .username("user@example.com")
                .password("password123")
                .build();

        Promise<TenantAuthResponse> authPromise = platform.authenticate(authRequest);
        TenantAuthResponse authResponse = runPromise(() -> authPromise);

        assertThat(authResponse.success()).isFalse();
        assertThat(authResponse.error()).contains("Tenant mismatch");
    }

    @Test
    @Order(6)
    @DisplayName("E2E: Metrics are isolated per tenant")
    void testTenantIsolatedMetrics() throws Exception {
        // Record metrics for both tenants
        platform.recordMetric(tenantA, "api.calls", 100).get();
        platform.recordMetric(tenantB, "api.calls", 50).get();

        // Get metrics for Tenant A
        Promise<TenantMetrics> metricsA = platform.getMetrics(tenantA);
        TenantMetrics tenantAMetrics = runPromise(() -> metricsA);

        assertThat(tenantAMetrics.apiCalls()).isEqualTo(100);

        // Get metrics for Tenant B
        Promise<TenantMetrics> metricsB = platform.getMetrics(tenantB);
        TenantMetrics tenantBMetrics = runPromise(() -> metricsB);

        assertThat(tenantBMetrics.apiCalls()).isEqualTo(50);
    }

    @Test
    @Order(7)
    @DisplayName("E2E: Audit logs are tenant-isolated")
    void testTenantIsolatedAuditLogs() throws Exception {
        // Create audit events for both tenants
        platform.createAuditEvent(tenantA, "USER_LOGIN", "user-1").get();
        platform.createAuditEvent(tenantA, "PROJECT_CREATED", "project-1").get();
        platform.createAuditEvent(tenantB, "USER_LOGIN", "user-2").get();

        // Get audit logs for Tenant A
        Promise<AuditEvent[]> auditLogsA = platform.getAuditLogs(tenantA);
        AuditEvent[] logsA = runPromise(() -> auditLogsA);

        assertThat(logsA).hasSize(2);
        for (AuditEvent log : logsA) {
            assertThat(log.tenantId()).isEqualTo(tenantA);
        }

        // Get audit logs for Tenant B
        Promise<AuditEvent[]> auditLogsB = platform.getAuditLogs(tenantB);
        AuditEvent[] logsB = runPromise(() -> auditLogsB);

        assertThat(logsB).hasSize(1);
        assertThat(logsB[0].tenantId()).isEqualTo(tenantB);
    }

    @Test
    @Order(8)
    @DisplayName("E2E: Cross-tenant access attempts are blocked and logged")
    void testCrossTenantAccessBlocking() throws Exception {
        // Attempt cross-tenant access
        CrossTenantAccessRequest request = CrossTenantAccessRequest.builder()
                .sourceTenant(tenantA)
                .targetTenant(tenantB)
                .resourceType("PROJECT")
                .resourceId(projectB1)
                .action("READ")
                .build();

        Promise<AccessControlResult> result = platform.attemptCrossTenantAccess(request);
        AccessControlResult accessResult = runPromise(() -> result);

        assertThat(accessResult.allowed()).isFalse();
        assertThat(accessResult.reason()).contains("Cross-tenant access denied");

        // Verify security event was logged
        Promise<SecurityEvent[]> securityLogs = platform.getSecurityEvents();
        SecurityEvent[] events = runPromise(() -> securityLogs);

        assertThat(events).anyMatch(e -> 
            e.eventType().equals("CROSS_TENANT_ACCESS_ATTEMPT") &&
            e.sourceTenant().equals(tenantA) &&
            e.targetTenant().equals(tenantB));
    }

    @Test
    @Order(9)
    @DisplayName("E2E: Resource names can be same across tenants")
    void testResourceNameIsolation() throws Exception {
        // Create project with same name in both tenants
        TenantProjectRequest requestA = TenantProjectRequest.builder()
                .tenantId(tenantA)
                .projectName("Common Project Name")
                .intent("Build something")
                .build();

        TenantProjectRequest requestB = TenantProjectRequest.builder()
                .tenantId(tenantB)
                .projectName("Common Project Name")
                .intent("Build something else")
                .build();

        TenantProject projectA = runPromise(() -> platform.createProject(requestA));
        TenantProject projectB = runPromise(() -> platform.createProject(requestB));

        // Both should be created successfully
        assertThat(projectA.id()).isNotEqualTo(projectB.id());
        assertThat(projectA.tenantId()).isEqualTo(tenantA);
        assertThat(projectB.tenantId()).isEqualTo(tenantB);
    }

    // Mock implementations

    interface MultiTenantPlatform {
        Promise<TenantProject> createProject(TenantProjectRequest request);
        Promise<TenantProject> getProject(String projectId, String tenantId);
        Promise<TenantProject[]> listProjects(String tenantId);
        Promise<Void> publishEvent(String tenantId, String eventType, Map<String, Object> payload);
        Promise<TenantEvent[]> consumeEvents(String tenantId, int limit);
        Promise<TenantUser> createUser(TenantUserRequest request);
        Promise<TenantAuthResponse> authenticate(TenantAuthRequest request);
        Promise<Void> recordMetric(String tenantId, String metricName, double value);
        Promise<TenantMetrics> getMetrics(String tenantId);
        Promise<Void> createAuditEvent(String tenantId, String eventType, String resourceId);
        Promise<AuditEvent[]> getAuditLogs(String tenantId);
        Promise<AccessControlResult> attemptCrossTenantAccess(CrossTenantAccessRequest request);
        Promise<AccessLog[]> getAccessLogs(String tenantId);
        Promise<SecurityEvent[]> getSecurityEvents();
    }

    record TenantProjectRequest(String tenantId, String projectName, String intent) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, projectName, intent;
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder projectName(String v) { projectName = v; return this; }
            Builder intent(String v) { intent = v; return this; }
            TenantProjectRequest build() { return new TenantProjectRequest(tenantId, projectName, intent); }
        }
    }

    record TenantProject(String id, String tenantId, String name, String intent) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String id, tenantId, name, intent;
            Builder id(String v) { id = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder name(String v) { name = v; return this; }
            Builder intent(String v) { intent = v; return this; }
            TenantProject build() { return new TenantProject(id, tenantId, name, intent); }
        }
    }

    record TenantEvent(String eventId, String tenantId, String eventType, Map<String, Object> payload) {}
    record TenantUser(String id, String tenantId, String username) {}
    record TenantUserRequest(String tenantId, String username, String password) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, username, password;
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder username(String v) { username = v; return this; }
            Builder password(String v) { password = v; return this; }
            TenantUserRequest build() { return new TenantUserRequest(tenantId, username, password); }
        }
    }
    record TenantAuthRequest(String tenantId, String username, String password) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, username, password;
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder username(String v) { username = v; return this; }
            Builder password(String v) { password = v; return this; }
            TenantAuthRequest build() { return new TenantAuthRequest(tenantId, username, password); }
        }
    }
    record TenantAuthResponse(boolean success, String userId, String error) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private boolean success;
            private String userId, error;
            Builder success(boolean v) { success = v; return this; }
            Builder userId(String v) { userId = v; return this; }
            Builder error(String v) { error = v; return this; }
            TenantAuthResponse build() { return new TenantAuthResponse(success, userId, error); }
        }
    }
    record TenantMetrics(double apiCalls, double activeUsers, double projects) {}
    record AuditEvent(String eventId, String tenantId, String eventType, String resourceId) {}
    record CrossTenantAccessRequest(String sourceTenant, String targetTenant, String resourceType, String resourceId, String action) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String sourceTenant, targetTenant, resourceType, resourceId, action;
            Builder sourceTenant(String v) { sourceTenant = v; return this; }
            Builder targetTenant(String v) { targetTenant = v; return this; }
            Builder resourceType(String v) { resourceType = v; return this; }
            Builder resourceId(String v) { resourceId = v; return this; }
            Builder action(String v) { action = v; return this; }
            CrossTenantAccessRequest build() { return new CrossTenantAccessRequest(sourceTenant, targetTenant, resourceType, resourceId, action); }
        }
    }
    record AccessControlResult(boolean allowed, String reason) {}
    record AccessLog(String action, String resource, String userId, String tenantId) {}
    record SecurityEvent(String eventType, String sourceTenant, String targetTenant, String resourceId) {}

    static class MockMultiTenantPlatform implements MultiTenantPlatform {
        private final Map<String, TenantProject> projects = new ConcurrentHashMap<>();
        private final Map<String, java.util.List<TenantEvent>> eventsByTenant = new ConcurrentHashMap<>();
        private final Map<String, TenantUser> users = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Double>> metrics = new ConcurrentHashMap<>();
        private final Map<String, java.util.List<AuditEvent>> auditLogs = new ConcurrentHashMap<>();
        private final java.util.List<SecurityEvent> securityEvents = new java.util.ArrayList<>();
        private final java.util.List<AccessLog> accessLogs = new java.util.ArrayList<>();

        @Override
        public Promise<TenantProject> createProject(TenantProjectRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                String id = UUID.randomUUID().toString();
                TenantProject project = TenantProject.builder()
                    .id(id)
                    .tenantId(request.tenantId())
                    .name(request.projectName())
                    .intent(request.intent())
                    .build();
                projects.put(id, project);
                return project;
            });
        }

        @Override
        public Promise<TenantProject> getProject(String projectId, String tenantId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                TenantProject project = projects.get(projectId);
                if (project == null) return null;
                
                // Check tenant isolation
                if (!project.tenantId().equals(tenantId)) {
                    accessLogs.add(new AccessLog("ACCESS_DENIED", projectId, "unknown", tenantId));
                    return null;
                }
                return project;
            });
        }

        @Override
        public Promise<TenantProject[]> listProjects(String tenantId) {
            return Promise.of(projects.values().stream()
                .filter(p -> p.tenantId().equals(tenantId))
                .toArray(TenantProject[]::new));
        }

        @Override
        public Promise<Void> publishEvent(String tenantId, String eventType, Map<String, Object> payload) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                TenantEvent event = new TenantEvent(UUID.randomUUID().toString(), tenantId, eventType, payload);
                eventsByTenant.computeIfAbsent(tenantId, k -> new java.util.ArrayList<>()).add(event);
                return null;
            });
        }

        @Override
        public Promise<TenantEvent[]> consumeEvents(String tenantId, int limit) {
            return Promise.of(eventsByTenant.getOrDefault(tenantId, java.util.List.of())
                .stream()
                .limit(limit)
                .toArray(TenantEvent[]::new));
        }

        @Override
        public Promise<TenantUser> createUser(TenantUserRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                String id = UUID.randomUUID().toString();
                TenantUser user = new TenantUser(id, request.tenantId(), request.username());
                users.put(id, user);
                return user;
            });
        }

        @Override
        public Promise<TenantAuthResponse> authenticate(TenantAuthRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                // Find user by username
                TenantUser user = users.values().stream()
                    .filter(u -> u.username().equals(request.username()))
                    .findFirst()
                    .orElse(null);

                if (user == null) {
                    return TenantAuthResponse.builder()
                        .success(false)
                        .error("User not found")
                        .build();
                }

                // Check tenant match
                if (!user.tenantId().equals(request.tenantId())) {
                    return TenantAuthResponse.builder()
                        .success(false)
                        .error("Tenant mismatch")
                        .build();
                }

                return TenantAuthResponse.builder()
                    .success(true)
                    .userId(user.id())
                    .build();
            });
        }

        @Override
        public Promise<Void> recordMetric(String tenantId, String metricName, double value) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                metrics.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .merge(metricName, value, Double::sum);
                return null;
            });
        }

        @Override
        public Promise<TenantMetrics> getMetrics(String tenantId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                Map<String, Double> tenantMetrics = metrics.getOrDefault(tenantId, Map.of());
                return new TenantMetrics(
                    tenantMetrics.getOrDefault("api.calls", 0.0),
                    tenantMetrics.getOrDefault("active.users", 0.0),
                    tenantMetrics.getOrDefault("projects", 0.0)
                );
            });
        }

        @Override
        public Promise<Void> createAuditEvent(String tenantId, String eventType, String resourceId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                AuditEvent event = new AuditEvent(UUID.randomUUID().toString(), tenantId, eventType, resourceId);
                auditLogs.computeIfAbsent(tenantId, k -> new java.util.ArrayList<>()).add(event);
                return null;
            });
        }

        @Override
        public Promise<AuditEvent[]> getAuditLogs(String tenantId) {
            return Promise.of(auditLogs.getOrDefault(tenantId, java.util.List.of())
                .toArray(new AuditEvent[0]));
        }

        @Override
        public Promise<AccessControlResult> attemptCrossTenantAccess(CrossTenantAccessRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                // Log security event
                securityEvents.add(new SecurityEvent(
                    "CROSS_TENANT_ACCESS_ATTEMPT",
                    request.sourceTenant(),
                    request.targetTenant(),
                    request.resourceId()
                ));

                // Always deny cross-tenant access
                return new AccessControlResult(false, "Cross-tenant access denied");
            });
        }

        @Override
        public Promise<AccessLog[]> getAccessLogs(String tenantId) {
            return Promise.of(accessLogs.stream()
                .filter(log -> log.tenantId().equals(tenantId))
                .toArray(AccessLog[]::new));
        }

        @Override
        public Promise<SecurityEvent[]> getSecurityEvents() {
            return Promise.of(securityEvents.toArray(new SecurityEvent[0]));
        }
    }
}
