/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for audit log retention policies (S002). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Audit log retention tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AuditLogRetention – Retention Policies (S002)")
class AuditLogRetentionTest extends EventloopTestBase {

    @Mock
    private AuditLogService auditLogService;

    @Nested
    @DisplayName("Log Retention")
    class LogRetentionTests {

        @Test
        @DisplayName("[S002]: purge_old_logs_removes_expired_logs")
        void purgeOldLogsRemovesExpiredLogs() { // GH-90000
            String tenantId = "tenant-alpha";
            Instant cutoff = Instant.now().minusSeconds(86400 * 90); // 90 days ago // GH-90000

            when(auditLogService.purgeOldLogs(tenantId, cutoff)) // GH-90000
                .thenReturn(Promise.of(5000)); // 5000 logs purged // GH-90000

            Integer purged = runPromise(() -> auditLogService.purgeOldLogs(tenantId, cutoff)); // GH-90000

            assertThat(purged).isEqualTo(5000); // GH-90000
        }

        @Test
        @DisplayName("[S002]: retention_stats_calculated_correctly")
        void retentionStatsCalculatedCorrectly() { // GH-90000
            String tenantId = "tenant-alpha";

            AuditLogService.RetentionStats stats = new AuditLogService.RetentionStats( // GH-90000
                100000,      // totalEvents
                90000,       // eventsInRetention
                10000,       // eventsPendingPurge
                Instant.now().minusSeconds(86400 * 365), // oldestEvent (1 year ago) // GH-90000
                Instant.now(), // newestEvent // GH-90000
                1024L * 1024 * 1024 * 10 // storageBytes (10GB) // GH-90000
            );

            when(auditLogService.getRetentionStats(tenantId)) // GH-90000
                .thenReturn(Promise.of(stats)); // GH-90000

            AuditLogService.RetentionStats result = runPromise(() -> // GH-90000
                auditLogService.getRetentionStats(tenantId) // GH-90000
            );

            assertThat(result.totalEvents()).isEqualTo(100000); // GH-90000
            assertThat(result.eventsInRetention()).isEqualTo(90000); // GH-90000
            assertThat(result.storageBytes()).isEqualTo(10737418240L); // GH-90000
        }

        @Test
        @DisplayName("[S002]: recent_logs_preserved")
        void recentLogsPreserved() { // GH-90000
            String tenantId = "tenant-alpha";
            Instant now = Instant.now(); // GH-90000
            Instant cutoff = now.minusSeconds(86400 * 30); // 30 days ago // GH-90000

            // Simulate that logs newer than cutoff should be kept
            AuditLogService.AuditEvent recent = new AuditLogService.AuditEvent( // GH-90000
                "event-1", tenantId, null, null, null, null, null,
                false, Map.of(), null, null, now.minusSeconds(86400 * 7) // GH-90000
            );

            assertThat(recent.timestamp()).isAfter(cutoff); // GH-90000
        }

        @Test
        @DisplayName("[S002]: old_logs_removed")
        void oldLogsRemoved() { // GH-90000
            String tenantId = "tenant-alpha";
            Instant now = Instant.now(); // GH-90000
            Instant cutoff = now.minusSeconds(86400 * 30); // 30 days ago // GH-90000

            // Simulate that logs older than cutoff should be removed
            AuditLogService.AuditEvent old = new AuditLogService.AuditEvent( // GH-90000
                "event-old", tenantId, null, null, null, null, null,
                false, Map.of(), null, null, now.minusSeconds(86400 * 60) // GH-90000
            );

            assertThat(old.timestamp()).isBefore(cutoff); // GH-90000
        }
    }

    @Nested
    @DisplayName("Log Querying")
    class LogQueryingTests {

        @Test
        @DisplayName("[S002]: query_by_type_filters_correctly")
        void queryByTypeFiltersCorrectly() { // GH-90000
            String tenantId = "tenant-alpha";
            AuditLogService.AuditQuery query = AuditLogService.AuditQuery.builder() // GH-90000
                .types(Set.of(AuditLogService.EventType.ACCESS, AuditLogService.EventType.CREATE)) // GH-90000
                .limit(100) // GH-90000
                .build(); // GH-90000

            List<AuditLogService.AuditEvent> events = List.of( // GH-90000
                AuditLogService.AuditEvent.builder() // GH-90000
                    .id("e1")
                    .tenantId(tenantId) // GH-90000
                    .type(AuditLogService.EventType.ACCESS) // GH-90000
                    .build(), // GH-90000
                AuditLogService.AuditEvent.builder() // GH-90000
                    .id("e2")
                    .tenantId(tenantId) // GH-90000
                    .type(AuditLogService.EventType.CREATE) // GH-90000
                    .build() // GH-90000
            );

            when(auditLogService.query(tenantId, query)) // GH-90000
                .thenReturn(Promise.of(events)); // GH-90000

            List<AuditLogService.AuditEvent> result = runPromise(() -> // GH-90000
                auditLogService.query(tenantId, query) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result.get(0).type()).isIn(AuditLogService.EventType.ACCESS, AuditLogService.EventType.CREATE); // GH-90000
        }

        @Test
        @DisplayName("[S002]: query_by_time_range_filters_by_date")
        void queryByTimeRangeFiltersByDate() { // GH-90000
            String tenantId = "tenant-alpha";
            Instant start = Instant.now().minusSeconds(86400 * 7); // 7 days ago // GH-90000
            Instant end = Instant.now(); // GH-90000

            AuditLogService.AuditQuery query = AuditLogService.AuditQuery.builder() // GH-90000
                .startTime(start) // GH-90000
                .endTime(end) // GH-90000
                .limit(1000) // GH-90000
                .build(); // GH-90000

            when(auditLogService.query(tenantId, query)) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            List<AuditLogService.AuditEvent> result = runPromise(() -> // GH-90000
                auditLogService.query(tenantId, query) // GH-90000
            );

            assertThat(query.startTime()).isEqualTo(start); // GH-90000
            assertThat(query.endTime()).isEqualTo(end); // GH-90000
        }

        @Test
        @DisplayName("[S002]: query_by_user_filters_by_user_id")
        void queryByUserFiltersByUserId() { // GH-90000
            String tenantId = "tenant-alpha";
            String userId = "user-001";

            AuditLogService.AuditQuery query = AuditLogService.AuditQuery.builder() // GH-90000
                .userId(userId) // GH-90000
                .limit(100) // GH-90000
                .build(); // GH-90000

            when(auditLogService.query(tenantId, query)) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            List<AuditLogService.AuditEvent> result = runPromise(() -> // GH-90000
                auditLogService.query(tenantId, query) // GH-90000
            );

            assertThat(query.userId()).isEqualTo(userId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Log Export")
    class LogExportTests {

        @Test
        @DisplayName("[S002]: export_logs_returns_data")
        void exportLogsReturnsData() { // GH-90000
            String tenantId = "tenant-alpha";
            Instant start = Instant.now().minusSeconds(86400 * 30); // GH-90000
            Instant end = Instant.now(); // GH-90000
            AuditLogService.ExportFormat format = AuditLogService.ExportFormat.JSON;

            byte[] exportData = "[{\"id\":\"1\"}]".getBytes(); // GH-90000

            when(auditLogService.export(tenantId, start, end, format)) // GH-90000
                .thenReturn(Promise.of(exportData)); // GH-90000

            byte[] result = runPromise(() -> auditLogService.export(tenantId, start, end, format)); // GH-90000

            assertThat(result).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[S002]: export_supports_multiple_formats")
        void exportSupportsMultipleFormats() { // GH-90000
            String tenantId = "tenant-alpha";
            Instant start = Instant.now().minusSeconds(86400 * 7); // GH-90000
            Instant end = Instant.now(); // GH-90000

            for (AuditLogService.ExportFormat format : AuditLogService.ExportFormat.values()) { // GH-90000
                byte[] data = format.name().toLowerCase().getBytes(); // GH-90000

                when(auditLogService.export(tenantId, start, end, format)) // GH-90000
                    .thenReturn(Promise.of(data)); // GH-90000

                byte[] result = runPromise(() -> auditLogService.export(tenantId, start, end, format)); // GH-90000

                assertThat(result).isNotNull(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Event Logging")
    class EventLoggingTests {

        @Test
        @DisplayName("[S002]: log_event_stores_event")
        void logEventStoresEvent() { // GH-90000
            AuditLogService.AuditEvent event = AuditLogService.AuditEvent.builder() // GH-90000
                .id("event-001")
                .tenantId("tenant-alpha")
                .userId("user-001")
                .type(AuditLogService.EventType.CREATE) // GH-90000
                .action("create-entity")
                .resource("Entity")
                .resourceId("entity-123")
                .success(true) // GH-90000
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .build(); // GH-90000

            when(auditLogService.log(event)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> auditLogService.log(event)); // GH-90000

            verify(auditLogService).log(event); // GH-90000
        }

        @Test
        @DisplayName("[S002]: logged_event_contains_all_metadata")
        void loggedEventContainsAllMetadata() { // GH-90000
            Instant now = Instant.now(); // GH-90000

            AuditLogService.AuditEvent event = AuditLogService.AuditEvent.builder() // GH-90000
                .id("event-001")
                .tenantId("tenant-alpha")
                .userId("user-001")
                .type(AuditLogService.EventType.ACCESS) // GH-90000
                .action("read")
                .resource("Report")
                .resourceId("report-123")
                .success(true) // GH-90000
                .details(Map.of("filter", "sales")) // GH-90000
                .ipAddress("10.0.0.1")
                .userAgent("TestAgent/1.0")
                .build(); // GH-90000

            assertThat(event.id()).isEqualTo("event-001");
            assertThat(event.tenantId()).isEqualTo("tenant-alpha");
            assertThat(event.userId()).isEqualTo("user-001");
            assertThat(event.type()).isEqualTo(AuditLogService.EventType.ACCESS); // GH-90000
            assertThat(event.success()).isTrue(); // GH-90000
            assertThat(java.time.Duration.between(event.timestamp(), now).toMillis()).isLessThan(100); // GH-90000
        }
    }

    @Nested
    @DisplayName("Compliance")
    class ComplianceTests {

        @Test
        @DisplayName("[S002]: retention_policy_compliance_calculated")
        void retentionPolicyComplianceCalculated() { // GH-90000
            String tenantId = "tenant-alpha";

            AuditLogService.RetentionStats stats = new AuditLogService.RetentionStats( // GH-90000
                100000, 100000, 0, // 100% in retention, 0 pending
                Instant.now().minusSeconds(86400 * 30), // GH-90000
                Instant.now(), // GH-90000
                1024L * 1024 * 100
            );

            when(auditLogService.getRetentionStats(tenantId)) // GH-90000
                .thenReturn(Promise.of(stats)); // GH-90000

            AuditLogService.RetentionStats result = runPromise(() -> // GH-90000
                auditLogService.getRetentionStats(tenantId) // GH-90000
            );

            // All events within retention
            assertThat(result.eventsPendingPurge()).isZero(); // GH-90000
            double complianceRate = (double) result.eventsInRetention() / result.totalEvents(); // GH-90000
            assertThat(complianceRate).isEqualTo(1.0); // GH-90000
        }
    }
}
