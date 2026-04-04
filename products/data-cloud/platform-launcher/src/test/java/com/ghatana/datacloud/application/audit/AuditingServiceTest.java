package com.ghatana.datacloud.application.audit;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.audit.AuditAction;
import com.ghatana.datacloud.entity.audit.AuditLog;
import com.ghatana.datacloud.entity.audit.AuditLogPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AuditingService}.
 *
 * @doc.type test
 * @doc.purpose Validate audit log recording, retrieval, export, and cleanup
 * @doc.layer application
 */
@DisplayName("AuditingService Tests")
@ExtendWith(MockitoExtension.class)
class AuditingServiceTest extends EventloopTestBase {

    @Mock
    private AuditLogPort auditStore;

    @Mock
    private MetricsCollector metricsCollector;

    private AuditingService auditingService;

    @BeforeEach
    void setUp() {
        auditingService = new AuditingService(auditStore, metricsCollector);
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException when auditStore is null")
        void shouldThrowForNullStore() {
            assertThatThrownBy(() -> new AuditingService(null, metricsCollector))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when metricsCollector is null")
        void shouldThrowForNullMetrics() {
            assertThatThrownBy(() -> new AuditingService(auditStore, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // LOG ACTION
    // =========================================================================

    @Nested
    @DisplayName("Log Action")
    class LogAction {

        @Test
        @DisplayName("should save audit log entry when logAction is called")
        void shouldSaveAuditLog() {
            when(auditStore.save(any())).thenReturn(Promise.complete());

            runPromise(() -> auditingService.logAction(
                    "tenant-1", "user-1", AuditAction.CREATE_ENTITY, "entity", "entity-123"));

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditStore).save(captor.capture());
            AuditLog saved = captor.getValue();
            assertThat(saved.getTenantId()).isEqualTo("tenant-1");
            assertThat(saved.getUserId()).isEqualTo("user-1");
            assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE_ENTITY);
            assertThat(saved.getResourceType()).isEqualTo("entity");
            assertThat(saved.getResourceId()).isEqualTo("entity-123");
        }

        @Test
        @DisplayName("should record metrics after logging action")
        void shouldRecordMetricsOnSuccess() {
            when(auditStore.save(any())).thenReturn(Promise.complete());

            runPromise(() -> auditingService.logAction(
                    "tenant-1", "user-1", AuditAction.DELETE_ENTITY, "entity", "eid-1"));

            verify(metricsCollector, atLeastOnce()).incrementCounter(anyString(), any(String[].class));
        }
    }

    // =========================================================================
    // QUERY OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Query Operations")
    class QueryOperations {

        private final String tenantId = "tenant-query";
        private final AuditLog sampleLog = AuditLog.builder()
                .tenantId(tenantId)
                .userId("user-1")
                .action(AuditAction.CREATE_ENTITY)
                .resourceType("entity")
                .resourceId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .build();

        @Test
        @DisplayName("should return user activity for a tenant and user")
        void shouldReturnUserActivity() {
            when(auditStore.findByTenantAndUser(tenantId, "user-1"))
                    .thenReturn(Promise.of(List.of(sampleLog)));

            List<AuditLog> activity = runPromise(() -> auditingService.getUserActivity(tenantId, "user-1"));
            assertThat(activity).hasSize(1);
        }

        @Test
        @DisplayName("should return resource audit trail")
        void shouldReturnResourceAuditTrail() {
            when(auditStore.findByResource(tenantId, "entity", "entity-1"))
                    .thenReturn(Promise.of(List.of(sampleLog)));

            List<AuditLog> trail = runPromise(() -> auditingService.getResourceAuditTrail(tenantId, "entity", "entity-1"));
            assertThat(trail).hasSize(1);
        }

        @Test
        @DisplayName("should return action logs by action type")
        void shouldReturnActionLogs() {
            when(auditStore.findByAction(tenantId, AuditAction.CREATE_ENTITY))
                    .thenReturn(Promise.of(List.of(sampleLog)));

            List<AuditLog> logs = runPromise(() -> auditingService.getActionLogs(tenantId, AuditAction.CREATE_ENTITY));
            assertThat(logs).hasSize(1);
        }

        @Test
        @DisplayName("should return audit logs by date range")
        void shouldReturnAuditLogsByDateRange() {
            Instant start = Instant.now().minusSeconds(3600);
            Instant end = Instant.now();
            when(auditStore.findByDateRange(tenantId, start, end))
                    .thenReturn(Promise.of(List.of(sampleLog)));

            List<AuditLog> logs = runPromise(() -> auditingService.getAuditTrailByDateRange(tenantId, start, end));
            assertThat(logs).hasSize(1);
        }

        @Test
        @DisplayName("should return audit log count by tenant")
        void shouldReturnCount() {
            when(auditStore.countByTenant(tenantId)).thenReturn(Promise.of(42L));

            long count = runPromise(() -> auditingService.getAuditLogCount(tenantId));
            assertThat(count).isEqualTo(42L);
        }
    }

    // =========================================================================
    // EXPORT AND CLEANUP
    // =========================================================================

    @Nested
    @DisplayName("Export and Cleanup")
    class ExportAndCleanup {

        @Test
        @DisplayName("should export audit logs and return non-null result")
        void shouldExportAuditLogs() {
            String tenantId = "tenant-export";
            Instant start = Instant.now().minusSeconds(3600);
            Instant end = Instant.now();
            when(auditStore.exportAsJson(tenantId, start, end))
                    .thenReturn(Promise.of("{\"logs\":[]}"));

            String result = runPromise(() -> auditingService.exportAuditLogs(tenantId, start, end));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should delete old logs and return count")
        void shouldCleanupOldLogs() {
            when(auditStore.deleteOlderThan("tenant-clean", 30)).thenReturn(Promise.of(5L));

            long deleted = runPromise(() -> auditingService.cleanupOldLogs("tenant-clean", 30));
            assertThat(deleted).isEqualTo(5L);
        }
    }
}
