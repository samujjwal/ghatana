/*
 * Copyright (c) 2026 Ghatana // GH-90000
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class TenantIsolationE2ETest extends EventloopTestBase {

    private static MultiTenantPlatform platform;
    private static String tenantA = "tenant-a-" + UUID.randomUUID(); // GH-90000
    private static String tenantB = "tenant-b-" + UUID.randomUUID(); // GH-90000
    private static String projectA1, projectA2, projectB1;

    @BeforeAll
    static void setUpPlatform() { // GH-90000
        platform = new MockMultiTenantPlatform(); // GH-90000
    }

    @Test
    @Order(1) // GH-90000
    @DisplayName("E2E: Create projects in different tenants [GH-90000]")
    void testCreateProjectsInDifferentTenants() throws Exception { // GH-90000
        // Create project in Tenant A
        TenantProjectRequest requestA1 = TenantProjectRequest.builder() // GH-90000
                .tenantId(tenantA) // GH-90000
                .projectName("Tenant A Project 1 [GH-90000]")
                .intent("Build API [GH-90000]")
                .build(); // GH-90000

        Promise<TenantProject> promiseA1 = platform.createProject(requestA1); // GH-90000
        TenantProject projectA = runPromise(() -> promiseA1); // GH-90000
        projectA1 = projectA.id(); // GH-90000

        assertThat(projectA).isNotNull(); // GH-90000
        assertThat(projectA.tenantId()).isEqualTo(tenantA); // GH-90000
        assertThat(projectA.name()).isEqualTo("Tenant A Project 1 [GH-90000]");

        // Create second project in Tenant A
        TenantProjectRequest requestA2 = TenantProjectRequest.builder() // GH-90000
                .tenantId(tenantA) // GH-90000
                .projectName("Tenant A Project 2 [GH-90000]")
                .intent("Build Frontend [GH-90000]")
                .build(); // GH-90000

        TenantProject projectA2Result = runPromise(() -> platform.createProject(requestA2)); // GH-90000
        projectA2 = projectA2Result.id(); // GH-90000

        // Create project in Tenant B
        TenantProjectRequest requestB1 = TenantProjectRequest.builder() // GH-90000
                .tenantId(tenantB) // GH-90000
                .projectName("Tenant B Project 1 [GH-90000]")
                .intent("Build Database [GH-90000]")
                .build(); // GH-90000

        TenantProject projectB = runPromise(() -> platform.createProject(requestB1)); // GH-90000
        projectB1 = projectB.id(); // GH-90000

        assertThat(projectB.tenantId()).isEqualTo(tenantB); // GH-90000
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("E2E: Tenant A cannot access Tenant B's projects [GH-90000]")
    void testTenantIsolationAccessControl() throws Exception { // GH-90000
        // Tenant A tries to access Tenant B's project
        Promise<TenantProject> promise = platform.getProject(projectB1, tenantA); // GH-90000
        TenantProject project = runPromise(() -> promise); // GH-90000

        assertThat(project).isNull(); // Access denied, returns null // GH-90000

        // Verify access was denied
        Promise<AccessLog[]> logs = platform.getAccessLogs(tenantA); // GH-90000
        AccessLog[] accessLogs = runPromise(() -> logs); // GH-90000

        assertThat(accessLogs).anyMatch(log ->  // GH-90000
            log.action().equals("ACCESS_DENIED [GH-90000]") && log.resource().contains(projectB1));
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("E2E: Each tenant only sees their own projects [GH-90000]")
    void testTenantDataIsolation() throws Exception { // GH-90000
        // List projects for Tenant A
        Promise<TenantProject[]> promiseA = platform.listProjects(tenantA); // GH-90000
        TenantProject[] projectsA = runPromise(() -> promiseA); // GH-90000

        assertThat(projectsA).hasSize(2); // GH-90000
        for (TenantProject project : projectsA) { // GH-90000
            assertThat(project.tenantId()).isEqualTo(tenantA); // GH-90000
        }

        // List projects for Tenant B
        Promise<TenantProject[]> promiseB = platform.listProjects(tenantB); // GH-90000
        TenantProject[] projectsB = runPromise(() -> promiseB); // GH-90000

        assertThat(projectsB).hasSize(1); // GH-90000
        assertThat(projectsB[0].tenantId()).isEqualTo(tenantB); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("E2E: Tenant data is isolated in events [GH-90000]")
    void testEventTenantIsolation() throws Exception { // GH-90000
        // Publish events from both tenants
        platform.publishEvent(tenantA, "PROJECT_UPDATED", Map.of("projectId", projectA1)).get(); // GH-90000
        platform.publishEvent(tenantB, "PROJECT_UPDATED", Map.of("projectId", projectB1)).get(); // GH-90000

        // Consume events for Tenant A
        Promise<TenantEvent[]> eventsA = platform.consumeEvents(tenantA, 10); // GH-90000
        TenantEvent[] tenantAEvents = runPromise(() -> eventsA); // GH-90000

        // All events should be from Tenant A
        for (TenantEvent event : tenantAEvents) { // GH-90000
            assertThat(event.tenantId()).isEqualTo(tenantA); // GH-90000
        }

        // Tenant A should not see Tenant B's events
        assertThat(tenantAEvents).noneMatch(e -> e.tenantId().equals(tenantB)); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("E2E: User authentication is tenant-scoped [GH-90000]")
    void testTenantScopedAuthentication() throws Exception { // GH-90000
        // Create user in Tenant A
        TenantUserRequest userRequest = TenantUserRequest.builder() // GH-90000
                .tenantId(tenantA) // GH-90000
                .username("user@example.com [GH-90000]")
                .password("password123 [GH-90000]")
                .build(); // GH-90000

        Promise<TenantUser> userPromise = platform.createUser(userRequest); // GH-90000
        TenantUser user = runPromise(() -> userPromise); // GH-90000

        assertThat(user.tenantId()).isEqualTo(tenantA); // GH-90000

        // User from Tenant A cannot authenticate in Tenant B context
        TenantAuthRequest authRequest = TenantAuthRequest.builder() // GH-90000
                .tenantId(tenantB) // Wrong tenant // GH-90000
                .username("user@example.com [GH-90000]")
                .password("password123 [GH-90000]")
                .build(); // GH-90000

        Promise<TenantAuthResponse> authPromise = platform.authenticate(authRequest); // GH-90000
        TenantAuthResponse authResponse = runPromise(() -> authPromise); // GH-90000

        assertThat(authResponse.success()).isFalse(); // GH-90000
        assertThat(authResponse.error()).contains("Tenant mismatch [GH-90000]");
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("E2E: Metrics are isolated per tenant [GH-90000]")
    void testTenantIsolatedMetrics() throws Exception { // GH-90000
        // Record metrics for both tenants
        platform.recordMetric(tenantA, "api.calls", 100).get(); // GH-90000
        platform.recordMetric(tenantB, "api.calls", 50).get(); // GH-90000

        // Get metrics for Tenant A
        Promise<TenantMetrics> metricsA = platform.getMetrics(tenantA); // GH-90000
        TenantMetrics tenantAMetrics = runPromise(() -> metricsA); // GH-90000

        assertThat(tenantAMetrics.apiCalls()).isEqualTo(100); // GH-90000

        // Get metrics for Tenant B
        Promise<TenantMetrics> metricsB = platform.getMetrics(tenantB); // GH-90000
        TenantMetrics tenantBMetrics = runPromise(() -> metricsB); // GH-90000

        assertThat(tenantBMetrics.apiCalls()).isEqualTo(50); // GH-90000
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("E2E: Audit logs are tenant-isolated [GH-90000]")
    void testTenantIsolatedAuditLogs() throws Exception { // GH-90000
        // Create audit events for both tenants
        platform.createAuditEvent(tenantA, "USER_LOGIN", "user-1").get(); // GH-90000
        platform.createAuditEvent(tenantA, "PROJECT_CREATED", "project-1").get(); // GH-90000
        platform.createAuditEvent(tenantB, "USER_LOGIN", "user-2").get(); // GH-90000

        // Get audit logs for Tenant A
        Promise<AuditEvent[]> auditLogsA = platform.getAuditLogs(tenantA); // GH-90000
        AuditEvent[] logsA = runPromise(() -> auditLogsA); // GH-90000

        assertThat(logsA).hasSize(2); // GH-90000
        for (AuditEvent log : logsA) { // GH-90000
            assertThat(log.tenantId()).isEqualTo(tenantA); // GH-90000
        }

        // Get audit logs for Tenant B
        Promise<AuditEvent[]> auditLogsB = platform.getAuditLogs(tenantB); // GH-90000
        AuditEvent[] logsB = runPromise(() -> auditLogsB); // GH-90000

        assertThat(logsB).hasSize(1); // GH-90000
        assertThat(logsB[0].tenantId()).isEqualTo(tenantB); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("E2E: Cross-tenant access attempts are blocked and logged [GH-90000]")
    void testCrossTenantAccessBlocking() throws Exception { // GH-90000
        // Attempt cross-tenant access
        CrossTenantAccessRequest request = CrossTenantAccessRequest.builder() // GH-90000
                .sourceTenant(tenantA) // GH-90000
                .targetTenant(tenantB) // GH-90000
                .resourceType("PROJECT [GH-90000]")
                .resourceId(projectB1) // GH-90000
                .action("READ [GH-90000]")
                .build(); // GH-90000

        Promise<AccessControlResult> result = platform.attemptCrossTenantAccess(request); // GH-90000
        AccessControlResult accessResult = runPromise(() -> result); // GH-90000

        assertThat(accessResult.allowed()).isFalse(); // GH-90000
        assertThat(accessResult.reason()).contains("Cross-tenant access denied [GH-90000]");

        // Verify security event was logged
        Promise<SecurityEvent[]> securityLogs = platform.getSecurityEvents(); // GH-90000
        SecurityEvent[] events = runPromise(() -> securityLogs); // GH-90000

        assertThat(events).anyMatch(e ->  // GH-90000
            e.eventType().equals("CROSS_TENANT_ACCESS_ATTEMPT [GH-90000]") &&
            e.sourceTenant().equals(tenantA) && // GH-90000
            e.targetTenant().equals(tenantB)); // GH-90000
    }

    @Test
    @Order(9) // GH-90000
    @DisplayName("E2E: Resource names can be same across tenants [GH-90000]")
    void testResourceNameIsolation() throws Exception { // GH-90000
        // Create project with same name in both tenants
        TenantProjectRequest requestA = TenantProjectRequest.builder() // GH-90000
                .tenantId(tenantA) // GH-90000
                .projectName("Common Project Name [GH-90000]")
                .intent("Build something [GH-90000]")
                .build(); // GH-90000

        TenantProjectRequest requestB = TenantProjectRequest.builder() // GH-90000
                .tenantId(tenantB) // GH-90000
                .projectName("Common Project Name [GH-90000]")
                .intent("Build something else [GH-90000]")
                .build(); // GH-90000

        TenantProject projectA = runPromise(() -> platform.createProject(requestA)); // GH-90000
        TenantProject projectB = runPromise(() -> platform.createProject(requestB)); // GH-90000

        // Both should be created successfully
        assertThat(projectA.id()).isNotEqualTo(projectB.id()); // GH-90000
        assertThat(projectA.tenantId()).isEqualTo(tenantA); // GH-90000
        assertThat(projectB.tenantId()).isEqualTo(tenantB); // GH-90000
    }

    // Mock implementations

    interface MultiTenantPlatform {
        Promise<TenantProject> createProject(TenantProjectRequest request); // GH-90000
        Promise<TenantProject> getProject(String projectId, String tenantId); // GH-90000
        Promise<TenantProject[]> listProjects(String tenantId); // GH-90000
        Promise<Void> publishEvent(String tenantId, String eventType, Map<String, Object> payload); // GH-90000
        Promise<TenantEvent[]> consumeEvents(String tenantId, int limit); // GH-90000
        Promise<TenantUser> createUser(TenantUserRequest request); // GH-90000
        Promise<TenantAuthResponse> authenticate(TenantAuthRequest request); // GH-90000
        Promise<Void> recordMetric(String tenantId, String metricName, double value); // GH-90000
        Promise<TenantMetrics> getMetrics(String tenantId); // GH-90000
        Promise<Void> createAuditEvent(String tenantId, String eventType, String resourceId); // GH-90000
        Promise<AuditEvent[]> getAuditLogs(String tenantId); // GH-90000
        Promise<AccessControlResult> attemptCrossTenantAccess(CrossTenantAccessRequest request); // GH-90000
        Promise<AccessLog[]> getAccessLogs(String tenantId); // GH-90000
        Promise<SecurityEvent[]> getSecurityEvents(); // GH-90000
    }

    record TenantProjectRequest(String tenantId, String projectName, String intent) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String tenantId, projectName, intent;
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder projectName(String v) { projectName = v; return this; } // GH-90000
            Builder intent(String v) { intent = v; return this; } // GH-90000
            TenantProjectRequest build() { return new TenantProjectRequest(tenantId, projectName, intent); } // GH-90000
        }
    }

    record TenantProject(String id, String tenantId, String name, String intent) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String id, tenantId, name, intent;
            Builder id(String v) { id = v; return this; } // GH-90000
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder name(String v) { name = v; return this; } // GH-90000
            Builder intent(String v) { intent = v; return this; } // GH-90000
            TenantProject build() { return new TenantProject(id, tenantId, name, intent); } // GH-90000
        }
    }

    record TenantEvent(String eventId, String tenantId, String eventType, Map<String, Object> payload) {} // GH-90000
    record TenantUser(String id, String tenantId, String username) {} // GH-90000
    record TenantUserRequest(String tenantId, String username, String password) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String tenantId, username, password;
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder username(String v) { username = v; return this; } // GH-90000
            Builder password(String v) { password = v; return this; } // GH-90000
            TenantUserRequest build() { return new TenantUserRequest(tenantId, username, password); } // GH-90000
        }
    }
    record TenantAuthRequest(String tenantId, String username, String password) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String tenantId, username, password;
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder username(String v) { username = v; return this; } // GH-90000
            Builder password(String v) { password = v; return this; } // GH-90000
            TenantAuthRequest build() { return new TenantAuthRequest(tenantId, username, password); } // GH-90000
        }
    }
    record TenantAuthResponse(boolean success, String userId, String error) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private boolean success;
            private String userId, error;
            Builder success(boolean v) { success = v; return this; } // GH-90000
            Builder userId(String v) { userId = v; return this; } // GH-90000
            Builder error(String v) { error = v; return this; } // GH-90000
            TenantAuthResponse build() { return new TenantAuthResponse(success, userId, error); } // GH-90000
        }
    }
    record TenantMetrics(double apiCalls, double activeUsers, double projects) {} // GH-90000
    record AuditEvent(String eventId, String tenantId, String eventType, String resourceId) {} // GH-90000
    record CrossTenantAccessRequest(String sourceTenant, String targetTenant, String resourceType, String resourceId, String action) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String sourceTenant, targetTenant, resourceType, resourceId, action;
            Builder sourceTenant(String v) { sourceTenant = v; return this; } // GH-90000
            Builder targetTenant(String v) { targetTenant = v; return this; } // GH-90000
            Builder resourceType(String v) { resourceType = v; return this; } // GH-90000
            Builder resourceId(String v) { resourceId = v; return this; } // GH-90000
            Builder action(String v) { action = v; return this; } // GH-90000
            CrossTenantAccessRequest build() { return new CrossTenantAccessRequest(sourceTenant, targetTenant, resourceType, resourceId, action); } // GH-90000
        }
    }
    record AccessControlResult(boolean allowed, String reason) {} // GH-90000
    record AccessLog(String action, String resource, String userId, String tenantId) {} // GH-90000
    record SecurityEvent(String eventType, String sourceTenant, String targetTenant, String resourceId) {} // GH-90000

    static class MockMultiTenantPlatform implements MultiTenantPlatform {
        private final Map<String, TenantProject> projects = new ConcurrentHashMap<>(); // GH-90000
        private final Map<String, java.util.List<TenantEvent>> eventsByTenant = new ConcurrentHashMap<>(); // GH-90000
        private final Map<String, TenantUser> users = new ConcurrentHashMap<>(); // GH-90000
        private final Map<String, Map<String, Double>> metrics = new ConcurrentHashMap<>(); // GH-90000
        private final Map<String, java.util.List<AuditEvent>> auditLogs = new ConcurrentHashMap<>(); // GH-90000
        private final java.util.List<SecurityEvent> securityEvents = new java.util.ArrayList<>(); // GH-90000
        private final java.util.List<AccessLog> accessLogs = new java.util.ArrayList<>(); // GH-90000

        @Override
        public Promise<TenantProject> createProject(TenantProjectRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                String id = UUID.randomUUID().toString(); // GH-90000
                TenantProject project = TenantProject.builder() // GH-90000
                    .id(id) // GH-90000
                    .tenantId(request.tenantId()) // GH-90000
                    .name(request.projectName()) // GH-90000
                    .intent(request.intent()) // GH-90000
                    .build(); // GH-90000
                projects.put(id, project); // GH-90000
                return project;
            });
        }

        @Override
        public Promise<TenantProject> getProject(String projectId, String tenantId) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                TenantProject project = projects.get(projectId); // GH-90000
                if (project == null) return null; // GH-90000
                
                // Check tenant isolation
                if (!project.tenantId().equals(tenantId)) { // GH-90000
                    accessLogs.add(new AccessLog("ACCESS_DENIED", projectId, "unknown", tenantId)); // GH-90000
                    return null;
                }
                return project;
            });
        }

        @Override
        public Promise<TenantProject[]> listProjects(String tenantId) { // GH-90000
            return Promise.of(projects.values().stream() // GH-90000
                .filter(p -> p.tenantId().equals(tenantId)) // GH-90000
                .toArray(TenantProject[]::new)); // GH-90000
        }

        @Override
        public Promise<Void> publishEvent(String tenantId, String eventType, Map<String, Object> payload) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                TenantEvent event = new TenantEvent(UUID.randomUUID().toString(), tenantId, eventType, payload); // GH-90000
                eventsByTenant.computeIfAbsent(tenantId, k -> new java.util.ArrayList<>()).add(event); // GH-90000
                return null;
            });
        }

        @Override
        public Promise<TenantEvent[]> consumeEvents(String tenantId, int limit) { // GH-90000
            return Promise.of(eventsByTenant.getOrDefault(tenantId, java.util.List.of()) // GH-90000
                .stream() // GH-90000
                .limit(limit) // GH-90000
                .toArray(TenantEvent[]::new)); // GH-90000
        }

        @Override
        public Promise<TenantUser> createUser(TenantUserRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                String id = UUID.randomUUID().toString(); // GH-90000
                TenantUser user = new TenantUser(id, request.tenantId(), request.username()); // GH-90000
                users.put(id, user); // GH-90000
                return user;
            });
        }

        @Override
        public Promise<TenantAuthResponse> authenticate(TenantAuthRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                // Find user by username
                TenantUser user = users.values().stream() // GH-90000
                    .filter(u -> u.username().equals(request.username())) // GH-90000
                    .findFirst() // GH-90000
                    .orElse(null); // GH-90000

                if (user == null) { // GH-90000
                    return TenantAuthResponse.builder() // GH-90000
                        .success(false) // GH-90000
                        .error("User not found [GH-90000]")
                        .build(); // GH-90000
                }

                // Check tenant match
                if (!user.tenantId().equals(request.tenantId())) { // GH-90000
                    return TenantAuthResponse.builder() // GH-90000
                        .success(false) // GH-90000
                        .error("Tenant mismatch [GH-90000]")
                        .build(); // GH-90000
                }

                return TenantAuthResponse.builder() // GH-90000
                    .success(true) // GH-90000
                    .userId(user.id()) // GH-90000
                    .build(); // GH-90000
            });
        }

        @Override
        public Promise<Void> recordMetric(String tenantId, String metricName, double value) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                metrics.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>()) // GH-90000
                    .merge(metricName, value, Double::sum); // GH-90000
                return null;
            });
        }

        @Override
        public Promise<TenantMetrics> getMetrics(String tenantId) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                Map<String, Double> tenantMetrics = metrics.getOrDefault(tenantId, Map.of()); // GH-90000
                return new TenantMetrics( // GH-90000
                    tenantMetrics.getOrDefault("api.calls", 0.0), // GH-90000
                    tenantMetrics.getOrDefault("active.users", 0.0), // GH-90000
                    tenantMetrics.getOrDefault("projects", 0.0) // GH-90000
                );
            });
        }

        @Override
        public Promise<Void> createAuditEvent(String tenantId, String eventType, String resourceId) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                AuditEvent event = new AuditEvent(UUID.randomUUID().toString(), tenantId, eventType, resourceId); // GH-90000
                auditLogs.computeIfAbsent(tenantId, k -> new java.util.ArrayList<>()).add(event); // GH-90000
                return null;
            });
        }

        @Override
        public Promise<AuditEvent[]> getAuditLogs(String tenantId) { // GH-90000
            return Promise.of(auditLogs.getOrDefault(tenantId, java.util.List.of()) // GH-90000
                .toArray(new AuditEvent[0])); // GH-90000
        }

        @Override
        public Promise<AccessControlResult> attemptCrossTenantAccess(CrossTenantAccessRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                // Log security event
                securityEvents.add(new SecurityEvent( // GH-90000
                    "CROSS_TENANT_ACCESS_ATTEMPT",
                    request.sourceTenant(), // GH-90000
                    request.targetTenant(), // GH-90000
                    request.resourceId() // GH-90000
                ));

                // Always deny cross-tenant access
                return new AccessControlResult(false, "Cross-tenant access denied"); // GH-90000
            });
        }

        @Override
        public Promise<AccessLog[]> getAccessLogs(String tenantId) { // GH-90000
            return Promise.of(accessLogs.stream() // GH-90000
                .filter(log -> log.tenantId().equals(tenantId)) // GH-90000
                .toArray(AccessLog[]::new)); // GH-90000
        }

        @Override
        public Promise<SecurityEvent[]> getSecurityEvents() { // GH-90000
            return Promise.of(securityEvents.toArray(new SecurityEvent[0])); // GH-90000
        }
    }
}
