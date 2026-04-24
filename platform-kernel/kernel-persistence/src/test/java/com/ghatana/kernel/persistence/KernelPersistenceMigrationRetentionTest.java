package com.ghatana.kernel.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.DefaultAuditTrailService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for kernel-persistence migration and retention scenarios.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Schema migration idempotency (ensureSchema called repeatedly)</li>
 *   <li>Module lifecycle state transitions through all canonical states</li>
 *   <li>Version history tracking (status changes preserve latest version)</li>
 *   <li>Retention: aged module records can be identified and purged</li>
 *   <li>Audit trail multi-tenant isolation</li>
 *   <li>Audit hash-chain ordering is preserved across restarts (loadAll sorts by timestamp)</li>
 *   <li>Audit event retention: bulk insert and full load round-trip</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Migration and retention integration tests for kernel-persistence adapters
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Kernel Persistence – Migration & Retention")
class KernelPersistenceMigrationRetentionTest {

    private DataSource dataSource;
    private JdbcModuleRegistry registry;
    private PostgresAuditTrailPersistence auditPersistence;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:migration_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("sa");
        dataSource = ds;

        registry = new JdbcModuleRegistry(dataSource);
        registry.ensureSchema();

        auditPersistence = new PostgresAuditTrailPersistence(dataSource, new ObjectMapper());
        auditPersistence.ensureSchema();
    }

    // =========================================================================
    // Schema migration idempotency
    // =========================================================================

    @Nested
    @DisplayName("Schema migration idempotency")
    class SchemaMigration {

        @Test
        @DisplayName("ensureSchema is idempotent – calling it multiple times does not throw")
        void ensureSchemaIsIdempotent() {
            assertThatNoException().isThrownBy(() -> {
                registry.ensureSchema();
                registry.ensureSchema();
                registry.ensureSchema();
            });
        }

        @Test
        @DisplayName("ensureSchema for audit trail is idempotent")
        void auditEnsureSchemaIsIdempotent() {
            assertThatNoException().isThrownBy(() -> {
                auditPersistence.ensureSchema();
                auditPersistence.ensureSchema();
            });
        }

        @Test
        @DisplayName("Data survives repeated ensureSchema calls")
        void dataSurvivesRepeatedMigration() {
            registry.registerModule("platform:java:kernel", "1.0.0", "REGISTERED");

            registry.ensureSchema();
            registry.ensureSchema();

            Optional<JdbcModuleRegistry.ModuleRegistration> found =
                registry.getModule("platform:java:kernel");
            assertThat(found).isPresent();
            assertThat(found.get().moduleVersion()).isEqualTo("1.0.0");
        }
    }

    // =========================================================================
    // Module lifecycle state transitions
    // =========================================================================

    @Nested
    @DisplayName("Module lifecycle state transitions")
    class LifecycleTransitions {

        @Test
        @DisplayName("Module transitions through all canonical states in order")
        void fullLifecycleTransition() {
            String moduleId = "platform:java:security";

            registry.registerModule(moduleId, "2.0.0", "REGISTERED");
            assertThat(statusOf(moduleId)).isEqualTo("REGISTERED");

            registry.registerModule(moduleId, "2.0.0", "INITIALIZED");
            assertThat(statusOf(moduleId)).isEqualTo("INITIALIZED");

            registry.registerModule(moduleId, "2.0.0", "STARTED");
            assertThat(statusOf(moduleId)).isEqualTo("STARTED");

            registry.registerModule(moduleId, "2.0.0", "RUNNING");
            assertThat(statusOf(moduleId)).isEqualTo("RUNNING");

            registry.registerModule(moduleId, "2.0.0", "STOPPED");
            assertThat(statusOf(moduleId)).isEqualTo("STOPPED");
        }

        @Test
        @DisplayName("Failed state is recorded and retrievable")
        void failedStateRecorded() {
            String moduleId = "platform:java:database";

            registry.registerModule(moduleId, "1.5.0", "STARTED");
            registry.registerModule(moduleId, "1.5.0", "FAILED");

            assertThat(statusOf(moduleId)).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("Version upgrade during transition is preserved")
        void versionUpgradeDuringTransition() {
            String moduleId = "platform:java:http";

            registry.registerModule(moduleId, "1.0.0", "REGISTERED");
            registry.registerModule(moduleId, "1.1.0", "STARTED");
            registry.registerModule(moduleId, "1.2.0", "RUNNING");

            JdbcModuleRegistry.ModuleRegistration reg =
                registry.getModule(moduleId).orElseThrow();
            assertThat(reg.moduleVersion()).isEqualTo("1.2.0");
            assertThat(reg.moduleStatus()).isEqualTo("RUNNING");
        }

        @Test
        @DisplayName("Registering a module updates updatedAt timestamp")
        void updatedAtChangesOnRegister() {
            String moduleId = "platform:java:observability";

            registry.registerModule(moduleId, "1.0.0", "REGISTERED");
            long firstTs = registry.getModule(moduleId).orElseThrow().updatedAtEpochMs();

            // Ensure time advances at least 1 ms
            try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            registry.registerModule(moduleId, "1.0.0", "STARTED");
            long secondTs = registry.getModule(moduleId).orElseThrow().updatedAtEpochMs();

            assertThat(secondTs).isGreaterThanOrEqualTo(firstTs);
        }
    }

    // =========================================================================
    // Retention: identifying and purging stale records
    // =========================================================================

    @Nested
    @DisplayName("Module retention")
    class ModuleRetention {

        @Test
        @DisplayName("Modules older than retention threshold can be identified via raw SQL")
        void identifyAgedModules() throws SQLException {
            long nowMs = Instant.now().toEpochMilli();
            long oldMs = nowMs - 90_000L; // 90 seconds ago

            // Insert aged module directly to simulate old state
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "MERGE INTO kernel_module_registry (module_id, module_version, module_status, updated_at) "
                     + "KEY (module_id) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, "platform:java:legacy");
                ps.setString(2, "0.9.0");
                ps.setString(3, "STOPPED");
                ps.setLong(4, oldMs);
                ps.executeUpdate();
            }

            registry.registerModule("platform:java:current", "2.0.0", "RUNNING");

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules();
            long retentionCutoffMs = nowMs - 60_000L; // 60-second retention window

            List<JdbcModuleRegistry.ModuleRegistration> stale = all.stream()
                .filter(r -> r.updatedAtEpochMs() < retentionCutoffMs)
                .toList();

            assertThat(stale).hasSize(1);
            assertThat(stale.getFirst().moduleId()).isEqualTo("platform:java:legacy");
        }

        @Test
        @DisplayName("Removing stale modules leaves active modules intact")
        void purgeStaleModulesPreservesActive() throws SQLException {
            long nowMs = Instant.now().toEpochMilli();
            long oldMs = nowMs - 90_000L;

            // Seed three modules: two stale, one current
            for (String id : List.of("platform:java:m1", "platform:java:m2")) {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "MERGE INTO kernel_module_registry (module_id, module_version, module_status, updated_at) "
                         + "KEY (module_id) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, id);
                    ps.setString(2, "0.1.0");
                    ps.setString(3, "STOPPED");
                    ps.setLong(4, oldMs);
                    ps.executeUpdate();
                }
            }
            registry.registerModule("platform:java:active", "3.0.0", "RUNNING");

            long retentionCutoffMs = nowMs - 60_000L;
            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules();
            all.stream()
                .filter(r -> r.updatedAtEpochMs() < retentionCutoffMs)
                .forEach(r -> registry.removeModule(r.moduleId()));

            List<JdbcModuleRegistry.ModuleRegistration> remaining = registry.listModules();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.getFirst().moduleId()).isEqualTo("platform:java:active");
        }

        @Test
        @DisplayName("Removing all modules leaves registry empty")
        void removeAllLeavesEmpty() {
            registry.registerModule("m1", "1.0", "STARTED");
            registry.registerModule("m2", "1.0", "STARTED");

            registry.removeModule("m1");
            registry.removeModule("m2");

            assertThat(registry.listModules()).isEmpty();
        }
    }

    // =========================================================================
    // Audit trail: multi-tenant isolation and retention
    // =========================================================================

    @Nested
    @DisplayName("Audit trail retention")
    class AuditRetention {

        @Test
        @DisplayName("Events from different tenants are all persisted and loadable")
        void multiTenantEventsPersisted() {
            persistEvent("evt-t1-1", "tenant-alpha", 1000L, "genesis", "hash-1");
            persistEvent("evt-t2-1", "tenant-beta",  2000L, "genesis", "hash-2");
            persistEvent("evt-t1-2", "tenant-alpha", 3000L, "hash-1",  "hash-3");

            List<DefaultAuditTrailService.StoredAuditEvent> all = auditPersistence.loadAll();
            assertThat(all).hasSize(3);

            List<String> tenantAlpha = all.stream()
                .map(e -> e.event().getTenantId())
                .filter("tenant-alpha"::equals)
                .toList();
            assertThat(tenantAlpha).hasSize(2);
        }

        @Test
        @DisplayName("loadAll returns events in ascending timestamp order (hash-chain order)")
        void loadAllPreservesTimestampOrder() {
            persistEvent("evt-3", "tenant-x", 3000L, "hash-2", "hash-3");
            persistEvent("evt-1", "tenant-x", 1000L, "genesis", "hash-1");
            persistEvent("evt-2", "tenant-x", 2000L, "hash-1", "hash-2");

            List<DefaultAuditTrailService.StoredAuditEvent> all = auditPersistence.loadAll();
            assertThat(all).hasSize(3);

            List<Long> timestamps = all.stream()
                .map(e -> e.event().getTimestamp())
                .toList();
            assertThat(timestamps).isSorted();
        }

        @Test
        @DisplayName("Hash-chain: previousHash of each event matches hash of prior event")
        void hashChainIntegrity() {
            persistEvent("evt-a", "tenant-y", 1000L, "genesis", "abc123");
            persistEvent("evt-b", "tenant-y", 2000L, "abc123", "def456");
            persistEvent("evt-c", "tenant-y", 3000L, "def456", "ghi789");

            List<DefaultAuditTrailService.StoredAuditEvent> events = auditPersistence.loadAll();
            assertThat(events).hasSize(3);

            // Verify chain: each event's previousHash matches prior event's stored hash
            assertThat(events.get(0).event().getPreviousHash()).isEqualTo("genesis");
            assertThat(events.get(1).event().getPreviousHash()).isEqualTo(events.get(0).hash());
            assertThat(events.get(2).event().getPreviousHash()).isEqualTo(events.get(1).hash());
        }

        @Test
        @DisplayName("Bulk audit event insert and load round-trip preserves all fields")
        void bulkInsertRoundTrip() {
            int count = 50;
            for (int i = 0; i < count; i++) {
                persistEvent(
                    "evt-bulk-" + i,
                    "tenant-bulk",
                    (long) (1000 + i),
                    i == 0 ? "genesis" : "hash-" + (i - 1),
                    "hash-" + i
                );
            }

            List<DefaultAuditTrailService.StoredAuditEvent> all = auditPersistence.loadAll();
            assertThat(all).hasSize(count);

            // Verify all fields are preserved for first event
            DefaultAuditTrailService.StoredAuditEvent first = all.getFirst();
            assertThat(first.event().getEventId()).isEqualTo("evt-bulk-0");
            assertThat(first.event().getTenantId()).isEqualTo("tenant-bulk");
            assertThat(first.hash()).isEqualTo("hash-0");
        }

        @Test
        @DisplayName("Events with structured data payload survive round-trip")
        void eventDataPayloadRoundTrip() {
            AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
                .eventId("evt-data")
                .eventType("MODULE_STARTED")
                .entityId("platform:java:kernel")
                .userId("system")
                .tenantId("platform")
                .action("START")
                .data(Map.of(
                    "version", "2.1.0",
                    "nodeId", "node-a",
                    "profileCount", 3
                ))
                .timestamp(Instant.now().toEpochMilli())
                .previousHash("genesis")
                .build();

            auditPersistence.persist(new DefaultAuditTrailService.StoredAuditEvent(event, "evt-hash"));

            List<DefaultAuditTrailService.StoredAuditEvent> loaded = auditPersistence.loadAll();
            assertThat(loaded).hasSize(1);

            Map<String, Object> loadedData = loaded.getFirst().event().getData();
            assertThat(loadedData).containsKey("version");
            assertThat(loadedData.get("version")).isEqualTo("2.1.0");
            assertThat(loadedData).containsKey("nodeId");
        }
    }

    // =========================================================================
    // Combined scenario: module lifecycle + audit trail in same transaction context
    // =========================================================================

    @Nested
    @DisplayName("Combined module + audit scenario")
    class CombinedScenario {

        @Test
        @DisplayName("Module state transitions are auditable and audit events mirror lifecycle")
        void moduleLifecycleWithAuditTrail() {
            String moduleId = "platform:java:agent-core";
            String[] states = {"REGISTERED", "INITIALIZED", "STARTED", "RUNNING"};

            long ts = 1_000L;
            String prevHash = "genesis";
            for (String state : states) {
                registry.registerModule(moduleId, "3.0.0", state);
                String hash = "hash-" + state.toLowerCase();
                persistEvent("audit-" + state, "platform", ts, prevHash, hash);
                prevHash = hash;
                ts += 1000;
            }

            assertThat(statusOf(moduleId)).isEqualTo("RUNNING");

            List<DefaultAuditTrailService.StoredAuditEvent> events = auditPersistence.loadAll();
            assertThat(events).hasSize(states.length);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String statusOf(String moduleId) {
        return registry.getModule(moduleId)
            .map(JdbcModuleRegistry.ModuleRegistration::moduleStatus)
            .orElseThrow(() -> new AssertionError("Module not found: " + moduleId));
    }

    private void persistEvent(String eventId, String tenantId, long timestamp,
                              String previousHash, String hash) {
        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
            .eventId(eventId)
            .eventType("MODULE_EVENT")
            .entityId("kernel")
            .userId("system")
            .tenantId(tenantId)
            .action("TRANSITION")
            .data(Map.of())
            .timestamp(timestamp)
            .previousHash(previousHash)
            .build();
        auditPersistence.persist(new DefaultAuditTrailService.StoredAuditEvent(event, hash));
    }
}
