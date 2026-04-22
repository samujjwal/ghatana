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
@DisplayName("AuditingService Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class AuditingServiceTest extends EventloopTestBase {

    @Mock
    private AuditLogPort auditStore;

    @Mock
    private MetricsCollector metricsCollector;

    private AuditingService auditingService;

    @BeforeEach
    void setUp() { // GH-90000
        auditingService = new AuditingService(auditStore, metricsCollector); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException when auditStore is null [GH-90000]")
        void shouldThrowForNullStore() { // GH-90000
            assertThatThrownBy(() -> new AuditingService(null, metricsCollector)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException when metricsCollector is null [GH-90000]")
        void shouldThrowForNullMetrics() { // GH-90000
            assertThatThrownBy(() -> new AuditingService(auditStore, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // LOG ACTION
    // =========================================================================

    @Nested
    @DisplayName("Log Action [GH-90000]")
    class LogAction {

        @Test
        @DisplayName("should save audit log entry when logAction is called [GH-90000]")
        void shouldSaveAuditLog() { // GH-90000
            when(auditStore.save(any())).thenReturn(Promise.complete()); // GH-90000

            runPromise(() -> auditingService.logAction( // GH-90000
                    "tenant-1", "user-1", AuditAction.CREATE_ENTITY, "entity", "entity-123"));

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class); // GH-90000
            verify(auditStore).save(captor.capture()); // GH-90000
            AuditLog saved = captor.getValue(); // GH-90000
            assertThat(saved.getTenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(saved.getUserId()).isEqualTo("user-1 [GH-90000]");
            assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE_ENTITY); // GH-90000
            assertThat(saved.getResourceType()).isEqualTo("entity [GH-90000]");
            assertThat(saved.getResourceId()).isEqualTo("entity-123 [GH-90000]");
        }

        @Test
        @DisplayName("should record metrics after logging action [GH-90000]")
        void shouldRecordMetricsOnSuccess() { // GH-90000
            when(auditStore.save(any())).thenReturn(Promise.complete()); // GH-90000

            runPromise(() -> auditingService.logAction( // GH-90000
                    "tenant-1", "user-1", AuditAction.DELETE_ENTITY, "entity", "eid-1"));

            verify(metricsCollector, atLeastOnce()).incrementCounter(anyString(), any(String[].class)); // GH-90000
        }
    }

    // =========================================================================
    // QUERY OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Query Operations [GH-90000]")
    class QueryOperations {

        private final String tenantId = "tenant-query";
        private final AuditLog sampleLog = AuditLog.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .userId("user-1 [GH-90000]")
                .action(AuditAction.CREATE_ENTITY) // GH-90000
                .resourceType("entity [GH-90000]")
                .resourceId(UUID.randomUUID().toString()) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build(); // GH-90000

        @Test
        @DisplayName("should return user activity for a tenant and user [GH-90000]")
        void shouldReturnUserActivity() { // GH-90000
            when(auditStore.findByTenantAndUser(tenantId, "user-1")) // GH-90000
                    .thenReturn(Promise.of(List.of(sampleLog))); // GH-90000

            List<AuditLog> activity = runPromise(() -> auditingService.getUserActivity(tenantId, "user-1")); // GH-90000
            assertThat(activity).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should return resource audit trail [GH-90000]")
        void shouldReturnResourceAuditTrail() { // GH-90000
            when(auditStore.findByResource(tenantId, "entity", "entity-1")) // GH-90000
                    .thenReturn(Promise.of(List.of(sampleLog))); // GH-90000

            List<AuditLog> trail = runPromise(() -> auditingService.getResourceAuditTrail(tenantId, "entity", "entity-1")); // GH-90000
            assertThat(trail).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should return action logs by action type [GH-90000]")
        void shouldReturnActionLogs() { // GH-90000
            when(auditStore.findByAction(tenantId, AuditAction.CREATE_ENTITY)) // GH-90000
                    .thenReturn(Promise.of(List.of(sampleLog))); // GH-90000

            List<AuditLog> logs = runPromise(() -> auditingService.getActionLogs(tenantId, AuditAction.CREATE_ENTITY)); // GH-90000
            assertThat(logs).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should return audit logs by date range [GH-90000]")
        void shouldReturnAuditLogsByDateRange() { // GH-90000
            Instant start = Instant.now().minusSeconds(3600); // GH-90000
            Instant end = Instant.now(); // GH-90000
            when(auditStore.findByDateRange(tenantId, start, end)) // GH-90000
                    .thenReturn(Promise.of(List.of(sampleLog))); // GH-90000

            List<AuditLog> logs = runPromise(() -> auditingService.getAuditTrailByDateRange(tenantId, start, end)); // GH-90000
            assertThat(logs).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should return audit log count by tenant [GH-90000]")
        void shouldReturnCount() { // GH-90000
            when(auditStore.countByTenant(tenantId)).thenReturn(Promise.of(42L)); // GH-90000

            long count = runPromise(() -> auditingService.getAuditLogCount(tenantId)); // GH-90000
            assertThat(count).isEqualTo(42L); // GH-90000
        }
    }

    // =========================================================================
    // EXPORT AND CLEANUP
    // =========================================================================

    @Nested
    @DisplayName("Export and Cleanup [GH-90000]")
    class ExportAndCleanup {

        @Test
        @DisplayName("should export audit logs and return non-null result [GH-90000]")
        void shouldExportAuditLogs() { // GH-90000
            String tenantId = "tenant-export";
            Instant start = Instant.now().minusSeconds(3600); // GH-90000
            Instant end = Instant.now(); // GH-90000
            when(auditStore.exportAsJson(tenantId, start, end)) // GH-90000
                    .thenReturn(Promise.of("{\"logs\":[]}")); // GH-90000

            String result = runPromise(() -> auditingService.exportAuditLogs(tenantId, start, end)); // GH-90000
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should delete old logs and return count [GH-90000]")
        void shouldCleanupOldLogs() { // GH-90000
            when(auditStore.deleteOlderThan("tenant-clean", 30)).thenReturn(Promise.of(5L)); // GH-90000

            long deleted = runPromise(() -> auditingService.cleanupOldLogs("tenant-clean", 30)); // GH-90000
            assertThat(deleted).isEqualTo(5L); // GH-90000
        }
    }
}
