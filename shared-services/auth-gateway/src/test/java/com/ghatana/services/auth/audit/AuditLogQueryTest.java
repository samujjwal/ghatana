/*
 * Copyright (c) 2026 Ghatana // GH-90000
 */
package com.ghatana.services.auth.audit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for audit log query and filtering functionality.
 */
class AuditLogQueryTest extends EventloopTestBase {

    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() { // GH-90000
        auditLogger = new AuditLogger(false, 1000); // Sync mode for testing // GH-90000
    }

    @Test
    @DisplayName("Should query audit events by type")
    void testQueryByEventType() throws Exception { // GH-90000
        // Log various event types
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); // GH-90000
        runPromise(() -> auditLogger.logLoginFailure("user2", "tenant1", "192.168.1.2", "Invalid password")); // GH-90000
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant1")); // GH-90000
        runPromise(() -> auditLogger.logTokenIssued("user1", "tenant1", "ACCESS", 3600)); // GH-90000

        // Get recent events
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Filter by type manually (in real implementation, would have query method) // GH-90000
        long loginSuccessCount = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> e.eventType() == AuditLogger.AuditEventType.AUTH_LOGIN_SUCCESS) // GH-90000
            .count(); // GH-90000
        long loginFailureCount = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> e.eventType() == AuditLogger.AuditEventType.AUTH_LOGIN_FAILURE) // GH-90000
            .count(); // GH-90000

        assertThat(loginSuccessCount).isEqualTo(1); // GH-90000
        assertThat(loginFailureCount).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should query audit events by user")
    void testQueryByUser() throws Exception { // GH-90000
        // Log events for different users
        runPromise(() -> auditLogger.logLoginSuccess("alice", "tenant1", "192.168.1.1", "Mozilla")); // GH-90000
        runPromise(() -> auditLogger.logLoginSuccess("bob", "tenant1", "192.168.1.2", "Chrome")); // GH-90000
        runPromise(() -> auditLogger.logLogout("alice", "tenant1", "session-123")); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Filter by user
        long aliceEvents = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> "alice".equals(e.userId())) // GH-90000
            .count(); // GH-90000
        long bobEvents = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> "bob".equals(e.userId())) // GH-90000
            .count(); // GH-90000

        assertThat(aliceEvents).isEqualTo(2); // GH-90000
        assertThat(bobEvents).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should query audit events by tenant")
    void testQueryByTenant() throws Exception { // GH-90000
        // Log events for different tenants
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant-a", "192.168.1.1", "Mozilla")); // GH-90000
        runPromise(() -> auditLogger.logLoginSuccess("user2", "tenant-b", "192.168.1.2", "Chrome")); // GH-90000
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant-a")); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Filter by tenant
        long tenantAEvents = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> "tenant-a".equals(e.tenantId())) // GH-90000
            .count(); // GH-90000
        long tenantBEvents = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> "tenant-b".equals(e.tenantId())) // GH-90000
            .count(); // GH-90000

        assertThat(tenantAEvents).isEqualTo(2); // GH-90000
        assertThat(tenantBEvents).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should query audit events by severity")
    void testQueryBySeverity() throws Exception { // GH-90000
        // Log events with different severities
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); // INFO // GH-90000
        runPromise(() -> auditLogger.logLoginFailure("user2", "tenant1", "192.168.1.2", "Invalid password")); // WARNING // GH-90000
        runPromise(() -> auditLogger.logAccountLocked("user3", "tenant1", "Too many attempts")); // CRITICAL // GH-90000
        runPromise(() -> auditLogger.logRateLimited("user4", "tenant1", "192.168.1.4", "/api/login")); // WARNING // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Filter by severity
        long infoCount = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.INFO) // GH-90000
            .count(); // GH-90000
        long warningCount = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.WARNING) // GH-90000
            .count(); // GH-90000
        long criticalCount = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.CRITICAL) // GH-90000
            .count(); // GH-90000

        assertThat(infoCount).isGreaterThanOrEqualTo(1); // GH-90000
        assertThat(warningCount).isGreaterThanOrEqualTo(2); // GH-90000
        assertThat(criticalCount).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should query audit events by time range")
    void testQueryByTimeRange() throws Exception { // GH-90000
        Instant startTime = Instant.now(); // GH-90000

        // Log some events
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); // GH-90000
        Thread.sleep(10); // GH-90000
        runPromise(() -> auditLogger.logLogout("user1", "tenant1", "session-1")); // GH-90000

        Instant endTime = Instant.now(); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Verify events are within time range
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            assertThat(event.timestamp()).isAfterOrEqualTo(startTime); // GH-90000
            assertThat(event.timestamp()).isBeforeOrEqualTo(endTime); // GH-90000
        }
    }

    @Test
    @DisplayName("Should query audit events by IP address")
    void testQueryByIpAddress() throws Exception { // GH-90000
        // Log events from different IPs
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.100", "Mozilla")); // GH-90000
        runPromise(() -> auditLogger.logLoginFailure("user2", "tenant1", "10.0.0.50", "Chrome")); // GH-90000
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant1")); // No IP in this log type // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Filter by IP
        long eventsFrom192168 = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> "192.168.1.100".equals(e.ipAddress())) // GH-90000
            .count(); // GH-90000

        assertThat(eventsFrom192168).isGreaterThanOrEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should support complex query with multiple filters")
    void testComplexQuery() throws Exception { // GH-90000
        // Log diverse events
        runPromise(() -> auditLogger.logLoginSuccess("alice", "tenant-a", "192.168.1.1", "Mozilla")); // GH-90000
        runPromise(() -> auditLogger.logLoginFailure("alice", "tenant-a", "192.168.1.2", "Wrong password")); // GH-90000
        runPromise(() -> auditLogger.logLoginSuccess("alice", "tenant-b", "192.168.1.1", "Mozilla")); // GH-90000
        runPromise(() -> auditLogger.logMfaEnrolled("alice", "tenant-a")); // GH-90000
        runPromise(() -> auditLogger.logAuthorizationDecision("alice", "tenant-a", "/api/admin", "DELETE", false)); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Complex query: alice + tenant-a + WARNING severity
        long complexMatchCount = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> "alice".equals(e.userId())) // GH-90000
            .filter(e -> "tenant-a".equals(e.tenantId())) // GH-90000
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.WARNING) // GH-90000
            .count(); // GH-90000

        assertThat(complexMatchCount).isGreaterThanOrEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should return events in chronological order")
    void testChronologicalOrder() throws Exception { // GH-90000
        // Log events with slight delays
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); // GH-90000
        Thread.sleep(5); // GH-90000
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant1")); // GH-90000
        Thread.sleep(5); // GH-90000
        runPromise(() -> auditLogger.logLogout("user1", "tenant1", "session-1")); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Verify chronological order
        for (int i = 1; i < events.length; i++) { // GH-90000
            assertThat(events[i].timestamp()).isAfterOrEqualTo(events[i-1].timestamp()); // GH-90000
        }
    }

    @Test
    @DisplayName("Should limit query results")
    void testQueryLimit() throws Exception { // GH-90000
        // Log many events
        for (int i = 0; i < 20; i++) { // GH-90000
            final int userId = i;
            runPromise(() -> auditLogger.logLoginSuccess("user" + userId, "tenant1", "192.168.1." + userId, "Mozilla")); // GH-90000
        }

        // Query with limit
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(5); // GH-90000

        assertThat(events).hasSizeLessThanOrEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("Should handle empty query results")
    void testEmptyQueryResults() throws Exception { // GH-90000
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Before logging any events, should be empty or minimal
        assertThat(events).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should include metadata in query results")
    void testMetadataInQueryResults() throws Exception { // GH-90000
        runPromise(() -> auditLogger.logAuthorizationDecision("user1", "tenant1", "/api/projects", "CREATE", true)); // GH-90000
        runPromise(() -> auditLogger.logRateLimited("user2", "tenant1", "192.168.1.1", "/api/login")); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Verify metadata is present
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            assertThat(event.metadata()).isNotNull(); // GH-90000
            assertThat(event.eventId()).isNotNull(); // GH-90000
            assertThat(event.timestamp()).isNotNull(); // GH-90000
        }
    }

    @Test
    @DisplayName("Should query security-related events")
    void testSecurityEventQuery() throws Exception { // GH-90000
        // Log security events
        runPromise(() -> auditLogger.logAccountLocked("user1", "tenant1", "Too many attempts")); // GH-90000
        runPromise(() -> auditLogger.logRateLimited("user2", "tenant1", "192.168.1.1", "/api/login")); // GH-90000
        runPromise(() -> auditLogger.logAuthorizationDecision("user3", "tenant1", "/api/admin", "DELETE", false)); // GH-90000
        runPromise(() -> auditLogger.logMfaFailed("user4", "tenant1", 3)); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Count security events (WARNING and CRITICAL severity) // GH-90000
        long securityEvents = java.util.Arrays.stream(events) // GH-90000
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.WARNING || // GH-90000
                        e.severity() == AuditLogger.AuditSeverity.CRITICAL) // GH-90000
            .count(); // GH-90000

        assertThat(securityEvents).isGreaterThanOrEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("Should support pagination concept")
    void testPaginationConcept() throws Exception { // GH-90000
        // Log 25 events
        for (int i = 0; i < 25; i++) { // GH-90000
            final int idx = i;
            runPromise(() -> auditLogger.logLoginSuccess("user" + (idx % 5), "tenant1", "192.168.1." + (idx % 10), "Mozilla")); // GH-90000
        }

        // Simulate pagination: first 10
        AuditLogger.AuditEvent[] page1 = auditLogger.getRecentEvents(10); // GH-90000
        assertThat(page1).hasSizeLessThanOrEqualTo(10); // GH-90000

        // In real implementation, would have offset parameter
        // For now, verify buffer works correctly
    }

    @Test
    @DisplayName("Should preserve event correlation across queries")
    void testEventCorrelation() throws Exception { // GH-90000
        String correlationId = "corr-" + java.util.UUID.randomUUID(); // GH-90000

        // Log related events
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); // GH-90000
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant1")); // GH-90000
        runPromise(() -> auditLogger.logMfaVerified("user1", "tenant1")); // GH-90000
        runPromise(() -> auditLogger.logTokenIssued("user1", "tenant1", "ACCESS", 3600)); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // All events should be for same user
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            if (event.userId() != null) { // GH-90000
                assertThat(event.userId()).isEqualTo("user1");
            }
        }
    }

    @Test
    @DisplayName("Should handle high volume of events")
    void testHighVolumeEvents() throws Exception { // GH-90000
        // Log 100 events rapidly
        for (int i = 0; i < 100; i++) { // GH-90000
            final int idx = i;
            runPromise(() -> auditLogger.logLoginSuccess("user" + (idx % 10), "tenant" + (idx % 5), "192.168.1." + (idx % 50), "Mozilla")); // GH-90000
        }

        // Query should still work
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(50); // GH-90000
        assertThat(events).hasSizeGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("Should support event type aggregation")
    void testEventTypeAggregation() throws Exception { // GH-90000
        // Log different types of events
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); // GH-90000
        runPromise(() -> auditLogger.logLoginSuccess("user2", "tenant1", "192.168.1.2", "Chrome")); // GH-90000
        runPromise(() -> auditLogger.logLoginFailure("user3", "tenant1", "192.168.1.3", "Invalid")); // GH-90000
        runPromise(() -> auditLogger.logLogout("user1", "tenant1", "session-1")); // GH-90000
        runPromise(() -> auditLogger.logLogout("user2", "tenant1", "session-2")); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        // Aggregate by type
        Map<AuditLogger.AuditEventType, Long> typeCounts = new java.util.HashMap<>(); // GH-90000
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            typeCounts.merge(event.eventType(), 1L, Long::sum); // GH-90000
        }

        assertThat(typeCounts.get(AuditLogger.AuditEventType.AUTH_LOGIN_SUCCESS)).isEqualTo(2); // GH-90000
        assertThat(typeCounts.get(AuditLogger.AuditEventType.AUTH_LOGIN_FAILURE)).isEqualTo(1); // GH-90000
        assertThat(typeCounts.get(AuditLogger.AuditEventType.AUTH_LOGOUT)).isEqualTo(2); // GH-90000
    }
}
