/*
 * Copyright (c) 2026 Ghatana 
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
    void setUp() { 
        auditLogger = new AuditLogger(false, 1000); // Sync mode for testing 
    }

    @Test
    @DisplayName("Should query audit events by type")
    void testQueryByEventType() throws Exception { 
        // Log various event types
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); 
        runPromise(() -> auditLogger.logLoginFailure("user2", "tenant1", "192.168.1.2", "Invalid password")); 
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant1")); 
        runPromise(() -> auditLogger.logTokenIssued("user1", "tenant1", "ACCESS", 3600)); 

        // Get recent events
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Filter by type manually (in real implementation, would have query method) 
        long loginSuccessCount = java.util.Arrays.stream(events) 
            .filter(e -> e.eventType() == AuditLogger.AuditEventType.AUTH_LOGIN_SUCCESS) 
            .count(); 
        long loginFailureCount = java.util.Arrays.stream(events) 
            .filter(e -> e.eventType() == AuditLogger.AuditEventType.AUTH_LOGIN_FAILURE) 
            .count(); 

        assertThat(loginSuccessCount).isEqualTo(1); 
        assertThat(loginFailureCount).isEqualTo(1); 
    }

    @Test
    @DisplayName("Should query audit events by user")
    void testQueryByUser() throws Exception { 
        // Log events for different users
        runPromise(() -> auditLogger.logLoginSuccess("alice", "tenant1", "192.168.1.1", "Mozilla")); 
        runPromise(() -> auditLogger.logLoginSuccess("bob", "tenant1", "192.168.1.2", "Chrome")); 
        runPromise(() -> auditLogger.logLogout("alice", "tenant1", "session-123")); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Filter by user
        long aliceEvents = java.util.Arrays.stream(events) 
            .filter(e -> "alice".equals(e.userId())) 
            .count(); 
        long bobEvents = java.util.Arrays.stream(events) 
            .filter(e -> "bob".equals(e.userId())) 
            .count(); 

        assertThat(aliceEvents).isEqualTo(2); 
        assertThat(bobEvents).isEqualTo(1); 
    }

    @Test
    @DisplayName("Should query audit events by tenant")
    void testQueryByTenant() throws Exception { 
        // Log events for different tenants
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant-a", "192.168.1.1", "Mozilla")); 
        runPromise(() -> auditLogger.logLoginSuccess("user2", "tenant-b", "192.168.1.2", "Chrome")); 
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant-a")); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Filter by tenant
        long tenantAEvents = java.util.Arrays.stream(events) 
            .filter(e -> "tenant-a".equals(e.tenantId())) 
            .count(); 
        long tenantBEvents = java.util.Arrays.stream(events) 
            .filter(e -> "tenant-b".equals(e.tenantId())) 
            .count(); 

        assertThat(tenantAEvents).isEqualTo(2); 
        assertThat(tenantBEvents).isEqualTo(1); 
    }

    @Test
    @DisplayName("Should query audit events by severity")
    void testQueryBySeverity() throws Exception { 
        // Log events with different severities
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); // INFO 
        runPromise(() -> auditLogger.logLoginFailure("user2", "tenant1", "192.168.1.2", "Invalid password")); // WARNING 
        runPromise(() -> auditLogger.logAccountLocked("user3", "tenant1", "Too many attempts")); // CRITICAL 
        runPromise(() -> auditLogger.logRateLimited("user4", "tenant1", "192.168.1.4", "/api/login")); // WARNING 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Filter by severity
        long infoCount = java.util.Arrays.stream(events) 
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.INFO) 
            .count(); 
        long warningCount = java.util.Arrays.stream(events) 
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.WARNING) 
            .count(); 
        long criticalCount = java.util.Arrays.stream(events) 
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.CRITICAL) 
            .count(); 

        assertThat(infoCount).isGreaterThanOrEqualTo(1); 
        assertThat(warningCount).isGreaterThanOrEqualTo(2); 
        assertThat(criticalCount).isEqualTo(1); 
    }

    @Test
    @DisplayName("Should query audit events by time range")
    void testQueryByTimeRange() throws Exception { 
        Instant startTime = Instant.now(); 

        // Log some events
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); 
        Thread.sleep(10); 
        runPromise(() -> auditLogger.logLogout("user1", "tenant1", "session-1")); 

        Instant endTime = Instant.now(); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Verify events are within time range
        for (AuditLogger.AuditEvent event : events) { 
            assertThat(event.timestamp()).isAfterOrEqualTo(startTime); 
            assertThat(event.timestamp()).isBeforeOrEqualTo(endTime); 
        }
    }

    @Test
    @DisplayName("Should query audit events by IP address")
    void testQueryByIpAddress() throws Exception { 
        // Log events from different IPs
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.100", "Mozilla")); 
        runPromise(() -> auditLogger.logLoginFailure("user2", "tenant1", "10.0.0.50", "Chrome")); 
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant1")); // No IP in this log type 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Filter by IP
        long eventsFrom192168 = java.util.Arrays.stream(events) 
            .filter(e -> "192.168.1.100".equals(e.ipAddress())) 
            .count(); 

        assertThat(eventsFrom192168).isGreaterThanOrEqualTo(1); 
    }

    @Test
    @DisplayName("Should support complex query with multiple filters")
    void testComplexQuery() throws Exception { 
        // Log diverse events
        runPromise(() -> auditLogger.logLoginSuccess("alice", "tenant-a", "192.168.1.1", "Mozilla")); 
        runPromise(() -> auditLogger.logLoginFailure("alice", "tenant-a", "192.168.1.2", "Wrong password")); 
        runPromise(() -> auditLogger.logLoginSuccess("alice", "tenant-b", "192.168.1.1", "Mozilla")); 
        runPromise(() -> auditLogger.logMfaEnrolled("alice", "tenant-a")); 
        runPromise(() -> auditLogger.logAuthorizationDecision("alice", "tenant-a", "/api/admin", "DELETE", false)); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Complex query: alice + tenant-a + WARNING severity
        long complexMatchCount = java.util.Arrays.stream(events) 
            .filter(e -> "alice".equals(e.userId())) 
            .filter(e -> "tenant-a".equals(e.tenantId())) 
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.WARNING) 
            .count(); 

        assertThat(complexMatchCount).isGreaterThanOrEqualTo(1); 
    }

    @Test
    @DisplayName("Should return events in chronological order")
    void testChronologicalOrder() throws Exception { 
        // Log events with slight delays
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); 
        Thread.sleep(5); 
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant1")); 
        Thread.sleep(5); 
        runPromise(() -> auditLogger.logLogout("user1", "tenant1", "session-1")); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Verify chronological order
        for (int i = 1; i < events.length; i++) { 
            assertThat(events[i].timestamp()).isAfterOrEqualTo(events[i-1].timestamp()); 
        }
    }

    @Test
    @DisplayName("Should limit query results")
    void testQueryLimit() throws Exception { 
        // Log many events
        for (int i = 0; i < 20; i++) { 
            final int userId = i;
            runPromise(() -> auditLogger.logLoginSuccess("user" + userId, "tenant1", "192.168.1." + userId, "Mozilla")); 
        }

        // Query with limit
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(5); 

        assertThat(events).hasSizeLessThanOrEqualTo(5); 
    }

    @Test
    @DisplayName("Should handle empty query results")
    void testEmptyQueryResults() throws Exception { 
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Before logging any events, should be empty or minimal
        assertThat(events).isNotNull(); 
    }

    @Test
    @DisplayName("Should include metadata in query results")
    void testMetadataInQueryResults() throws Exception { 
        runPromise(() -> auditLogger.logAuthorizationDecision("user1", "tenant1", "/api/projects", "CREATE", true)); 
        runPromise(() -> auditLogger.logRateLimited("user2", "tenant1", "192.168.1.1", "/api/login")); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Verify metadata is present
        for (AuditLogger.AuditEvent event : events) { 
            assertThat(event.metadata()).isNotNull(); 
            assertThat(event.eventId()).isNotNull(); 
            assertThat(event.timestamp()).isNotNull(); 
        }
    }

    @Test
    @DisplayName("Should query security-related events")
    void testSecurityEventQuery() throws Exception { 
        // Log security events
        runPromise(() -> auditLogger.logAccountLocked("user1", "tenant1", "Too many attempts")); 
        runPromise(() -> auditLogger.logRateLimited("user2", "tenant1", "192.168.1.1", "/api/login")); 
        runPromise(() -> auditLogger.logAuthorizationDecision("user3", "tenant1", "/api/admin", "DELETE", false)); 
        runPromise(() -> auditLogger.logMfaFailed("user4", "tenant1", 3)); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Count security events (WARNING and CRITICAL severity) 
        long securityEvents = java.util.Arrays.stream(events) 
            .filter(e -> e.severity() == AuditLogger.AuditSeverity.WARNING || 
                        e.severity() == AuditLogger.AuditSeverity.CRITICAL) 
            .count(); 

        assertThat(securityEvents).isGreaterThanOrEqualTo(3); 
    }

    @Test
    @DisplayName("Should support pagination concept")
    void testPaginationConcept() throws Exception { 
        // Log 25 events
        for (int i = 0; i < 25; i++) { 
            final int idx = i;
            runPromise(() -> auditLogger.logLoginSuccess("user" + (idx % 5), "tenant1", "192.168.1." + (idx % 10), "Mozilla")); 
        }

        // Simulate pagination: first 10
        AuditLogger.AuditEvent[] page1 = auditLogger.getRecentEvents(10); 
        assertThat(page1).hasSizeLessThanOrEqualTo(10); 

        // In real implementation, would have offset parameter
        // For now, verify buffer works correctly
    }

    @Test
    @DisplayName("Should preserve event correlation across queries")
    void testEventCorrelation() throws Exception { 
        String correlationId = "corr-" + java.util.UUID.randomUUID(); 

        // Log related events
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); 
        runPromise(() -> auditLogger.logMfaEnrolled("user1", "tenant1")); 
        runPromise(() -> auditLogger.logMfaVerified("user1", "tenant1")); 
        runPromise(() -> auditLogger.logTokenIssued("user1", "tenant1", "ACCESS", 3600)); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // All events should be for same user
        for (AuditLogger.AuditEvent event : events) { 
            if (event.userId() != null) { 
                assertThat(event.userId()).isEqualTo("user1");
            }
        }
    }

    @Test
    @DisplayName("Should handle high volume of events")
    void testHighVolumeEvents() throws Exception { 
        // Log 100 events rapidly
        for (int i = 0; i < 100; i++) { 
            final int idx = i;
            runPromise(() -> auditLogger.logLoginSuccess("user" + (idx % 10), "tenant" + (idx % 5), "192.168.1." + (idx % 50), "Mozilla")); 
        }

        // Query should still work
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(50); 
        assertThat(events).hasSizeGreaterThan(0); 
    }

    @Test
    @DisplayName("Should support event type aggregation")
    void testEventTypeAggregation() throws Exception { 
        // Log different types of events
        runPromise(() -> auditLogger.logLoginSuccess("user1", "tenant1", "192.168.1.1", "Mozilla")); 
        runPromise(() -> auditLogger.logLoginSuccess("user2", "tenant1", "192.168.1.2", "Chrome")); 
        runPromise(() -> auditLogger.logLoginFailure("user3", "tenant1", "192.168.1.3", "Invalid")); 
        runPromise(() -> auditLogger.logLogout("user1", "tenant1", "session-1")); 
        runPromise(() -> auditLogger.logLogout("user2", "tenant1", "session-2")); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); 

        // Aggregate by type
        Map<AuditLogger.AuditEventType, Long> typeCounts = new java.util.HashMap<>(); 
        for (AuditLogger.AuditEvent event : events) { 
            typeCounts.merge(event.eventType(), 1L, Long::sum); 
        }

        assertThat(typeCounts.get(AuditLogger.AuditEventType.AUTH_LOGIN_SUCCESS)).isEqualTo(2); 
        assertThat(typeCounts.get(AuditLogger.AuditEventType.AUTH_LOGIN_FAILURE)).isEqualTo(1); 
        assertThat(typeCounts.get(AuditLogger.AuditEventType.AUTH_LOGOUT)).isEqualTo(2); 
    }

    @Test
    @DisplayName("Should redact PII and secrets in audit message and metadata")
    void testAuditEventRedactionContract() throws Exception { 
        runPromise(() -> auditLogger.logLoginFailure(
                "user@example.com",
                "tenant1",
                "192.168.1.5",
                "token=abc123 email=user@example.com ssn=123-45-6789")); 

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(1); 
        assertThat(events).hasSize(1); 

        AuditLogger.AuditEvent event = events[0]; 
        assertThat(event.message()).doesNotContain("user@example.com");
        assertThat(event.message()).doesNotContain("123-45-6789");
        assertThat(event.message()).doesNotContain("abc123");
        assertThat(event.metadata().get("reason")).doesNotContain("user@example.com");
        assertThat(event.metadata().get("reason")).doesNotContain("123-45-6789");
        assertThat(event.metadata().get("reason")).doesNotContain("abc123");
    }
}
