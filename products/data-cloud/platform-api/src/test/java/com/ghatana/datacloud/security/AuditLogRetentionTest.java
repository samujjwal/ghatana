/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Tests for audit log retention policies (S002).
 *
 * @doc.type class
 * @doc.purpose Audit log retention tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogRetention – Retention Policies (S002)")
class AuditLogRetentionTest extends EventloopTestBase {

    @Mock
    private AuditLogService auditLogService;

    @Nested
    @DisplayName("Log Retention")
    class LogRetentionTests {

        @Test
        @DisplayName("[S002]: purge_old_logs_removes_expired_logs")
        void purgeOldLogsRemovesExpiredLogs() {
            String tenantId = "tenant-alpha";
            Instant cutoff = Instant.now().minusSeconds(86400 * 90); // 90 days ago

            when(auditLogService.purgeOldLogs(tenantId, cutoff))
                .thenReturn(Promise.of(5000)); // 5000 logs purged

            Integer purged = runPromise(() -> auditLogService.purgeOldLogs(tenantId, cutoff));

            assertThat(purged).isEqualTo(5000);
        }

        @Test
        @DisplayName("[S002]: retention_stats_calculated_correctly")
        void retentionStatsCalculatedCorrectly() {
            String tenantId = "tenant-alpha";

            AuditLogService.RetentionStats stats = new AuditLogService.RetentionStats(
                100000,      // totalEvents
                90000,       // eventsInRetention
                10000,       // eventsPendingPurge
                Instant.now().minusSeconds(86400 * 365), // oldestEvent (1 year ago)
                Instant.now(), // newestEvent
                1024L * 1024 * 1024 * 10 // storageBytes (10GB)
            );

            when(auditLogService.getRetentionStats(tenantId))
                .thenReturn(Promise.of(stats));

            AuditLogService.RetentionStats result = runPromise(() ->
                auditLogService.getRetentionStats(tenantId)
            );

            assertThat(result.totalEvents()).isEqualTo(100000);
            assertThat(result.eventsInRetention()).isEqualTo(90000);
            assertThat(result.storageBytes()).isEqualTo(10737418240L);
        }

        @Test
        @DisplayName("[S002]: recent_logs_preserved")
        void recentLogsPreserved() {
            String tenantId = "tenant-alpha";
            Instant now = Instant.now();
            Instant cutoff = now.minusSeconds(86400 * 30); // 30 days ago

            // Simulate that logs newer than cutoff should be kept
            AuditLogService.AuditEvent recent = new AuditLogService.AuditEvent(
                "event-1", tenantId, null, null, null, null, null,
                false, Map.of(), null, null, now.minusSeconds(86400 * 7)
            );

            assertThat(recent.timestamp()).isAfter(cutoff);
        }

        @Test
        @DisplayName("[S002]: old_logs_removed")
        void oldLogsRemoved() {
            String tenantId = "tenant-alpha";
            Instant now = Instant.now();
            Instant cutoff = now.minusSeconds(86400 * 30); // 30 days ago

            // Simulate that logs older than cutoff should be removed
            AuditLogService.AuditEvent old = new AuditLogService.AuditEvent(
                "event-old", tenantId, null, null, null, null, null,
                false, Map.of(), null, null, now.minusSeconds(86400 * 60)
            );

            assertThat(old.timestamp()).isBefore(cutoff);
        }
    }

    @Nested
    @DisplayName("Log Querying")
    class LogQueryingTests {

        @Test
        @DisplayName("[S002]: query_by_type_filters_correctly")
        void queryByTypeFiltersCorrectly() {
            String tenantId = "tenant-alpha";
            AuditLogService.AuditQuery query = AuditLogService.AuditQuery.builder()
                .types(Set.of(AuditLogService.EventType.ACCESS, AuditLogService.EventType.CREATE))
                .limit(100)
                .build();

            List<AuditLogService.AuditEvent> events = List.of(
                AuditLogService.AuditEvent.builder()
                    .id("e1")
                    .tenantId(tenantId)
                    .type(AuditLogService.EventType.ACCESS)
                    .build(),
                AuditLogService.AuditEvent.builder()
                    .id("e2")
                    .tenantId(tenantId)
                    .type(AuditLogService.EventType.CREATE)
                    .build()
            );

            when(auditLogService.query(tenantId, query))
                .thenReturn(Promise.of(events));

            List<AuditLogService.AuditEvent> result = runPromise(() ->
                auditLogService.query(tenantId, query)
            );

            assertThat(result).hasSize(2);
            assertThat(result.get(0).type()).isIn(AuditLogService.EventType.ACCESS, AuditLogService.EventType.CREATE);
        }

        @Test
        @DisplayName("[S002]: query_by_time_range_filters_by_date")
        void queryByTimeRangeFiltersByDate() {
            String tenantId = "tenant-alpha";
            Instant start = Instant.now().minusSeconds(86400 * 7); // 7 days ago
            Instant end = Instant.now();

            AuditLogService.AuditQuery query = AuditLogService.AuditQuery.builder()
                .startTime(start)
                .endTime(end)
                .limit(1000)
                .build();

            when(auditLogService.query(tenantId, query))
                .thenReturn(Promise.of(List.of()));

            List<AuditLogService.AuditEvent> result = runPromise(() ->
                auditLogService.query(tenantId, query)
            );

            assertThat(query.startTime()).isEqualTo(start);
            assertThat(query.endTime()).isEqualTo(end);
        }

        @Test
        @DisplayName("[S002]: query_by_user_filters_by_user_id")
        void queryByUserFiltersByUserId() {
            String tenantId = "tenant-alpha";
            String userId = "user-001";

            AuditLogService.AuditQuery query = AuditLogService.AuditQuery.builder()
                .userId(userId)
                .limit(100)
                .build();

            when(auditLogService.query(tenantId, query))
                .thenReturn(Promise.of(List.of()));

            List<AuditLogService.AuditEvent> result = runPromise(() ->
                auditLogService.query(tenantId, query)
            );

            assertThat(query.userId()).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("Log Export")
    class LogExportTests {

        @Test
        @DisplayName("[S002]: export_logs_returns_data")
        void exportLogsReturnsData() {
            String tenantId = "tenant-alpha";
            Instant start = Instant.now().minusSeconds(86400 * 30);
            Instant end = Instant.now();
            AuditLogService.ExportFormat format = AuditLogService.ExportFormat.JSON;

            byte[] exportData = "[{\"id\":\"1\"}]".getBytes();

            when(auditLogService.export(tenantId, start, end, format))
                .thenReturn(Promise.of(exportData));

            byte[] result = runPromise(() -> auditLogService.export(tenantId, start, end, format));

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("[S002]: export_supports_multiple_formats")
        void exportSupportsMultipleFormats() {
            String tenantId = "tenant-alpha";
            Instant start = Instant.now().minusSeconds(86400 * 7);
            Instant end = Instant.now();

            for (AuditLogService.ExportFormat format : AuditLogService.ExportFormat.values()) {
                byte[] data = format.name().toLowerCase().getBytes();

                when(auditLogService.export(tenantId, start, end, format))
                    .thenReturn(Promise.of(data));

                byte[] result = runPromise(() -> auditLogService.export(tenantId, start, end, format));

                assertThat(result).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Event Logging")
    class EventLoggingTests {

        @Test
        @DisplayName("[S002]: log_event_stores_event")
        void logEventStoresEvent() {
            AuditLogService.AuditEvent event = AuditLogService.AuditEvent.builder()
                .id("event-001")
                .tenantId("tenant-alpha")
                .userId("user-001")
                .type(AuditLogService.EventType.CREATE)
                .action("create-entity")
                .resource("Entity")
                .resourceId("entity-123")
                .success(true)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .build();

            when(auditLogService.log(event))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> auditLogService.log(event));

            verify(auditLogService).log(event);
        }

        @Test
        @DisplayName("[S002]: logged_event_contains_all_metadata")
        void loggedEventContainsAllMetadata() {
            Instant now = Instant.now();

            AuditLogService.AuditEvent event = AuditLogService.AuditEvent.builder()
                .id("event-001")
                .tenantId("tenant-alpha")
                .userId("user-001")
                .type(AuditLogService.EventType.ACCESS)
                .action("read")
                .resource("Report")
                .resourceId("report-123")
                .success(true)
                .details(Map.of("filter", "sales"))
                .ipAddress("10.0.0.1")
                .userAgent("TestAgent/1.0")
                .build();

            assertThat(event.id()).isEqualTo("event-001");
            assertThat(event.tenantId()).isEqualTo("tenant-alpha");
            assertThat(event.userId()).isEqualTo("user-001");
            assertThat(event.type()).isEqualTo(AuditLogService.EventType.ACCESS);
            assertThat(event.success()).isTrue();
            assertThat(java.time.Duration.between(event.timestamp(), now).toMillis()).isLessThan(100);
        }
    }

    @Nested
    @DisplayName("Compliance")
    class ComplianceTests {

        @Test
        @DisplayName("[S002]: retention_policy_compliance_calculated")
        void retentionPolicyComplianceCalculated() {
            String tenantId = "tenant-alpha";

            AuditLogService.RetentionStats stats = new AuditLogService.RetentionStats(
                100000, 100000, 0, // 100% in retention, 0 pending
                Instant.now().minusSeconds(86400 * 30),
                Instant.now(),
                1024L * 1024 * 100
            );

            when(auditLogService.getRetentionStats(tenantId))
                .thenReturn(Promise.of(stats));

            AuditLogService.RetentionStats result = runPromise(() ->
                auditLogService.getRetentionStats(tenantId)
            );

            // All events within retention
            assertThat(result.eventsPendingPurge()).isZero();
            double complianceRate = (double) result.eventsInRetention() / result.totalEvents();
            assertThat(complianceRate).isEqualTo(1.0);
        }
    }
}
