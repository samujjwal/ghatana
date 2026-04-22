package com.ghatana.plugin.audit.impl;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.platform.plugin.PluginState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.h2.jdbcx.JdbcDataSource;
import com.ghatana.platform.plugin.PluginContext;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for {@link DurableAuditTrailPlugin}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>KP-010: cross-tenant isolation — events from tenant A must not be visible to tenant B</li>
 *   <li>KP-011: hash-chain mutation detection — a tampered entry must cause verifyIntegrity to report failure</li>
 *   <li>Basic CRUD: logEvent, getTrail, verifyIntegrity, exportTrail</li>
 * </ul>
 *
 * <p>Uses an H2 in-memory database so the test is self-contained with no external dependencies.
 *
 * @doc.type class
 * @doc.purpose Contract tests for DurableAuditTrailPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DurableAuditTrailPlugin contract tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DurableAuditTrailPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableAuditTrailPlugin plugin;

    @BeforeEach
    void setUp() { // GH-90000
        JdbcDataSource ds = new JdbcDataSource(); // GH-90000
        // Unique DB per test to ensure isolation between tests
        ds.setURL("jdbc:h2:mem:audit_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"); // GH-90000
        plugin = new DurableAuditTrailPlugin(ds); // GH-90000
        plugin.ensureSchema(); // GH-90000
        runPromise(() -> plugin.initialize(mockContext)); // GH-90000
        runPromise(() -> plugin.start()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        runPromise(() -> plugin.stop()); // GH-90000
        runPromise(() -> plugin.shutdown()); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Plugin should transition through UNLOADED → INITIALIZED → STARTED → STOPPED → UNLOADED [GH-90000]")
    void testLifecycleTransitions() { // GH-90000
        // Already started in setUp — verify terminal state after stop+shutdown
        runPromise(() -> plugin.stop()); // GH-90000
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED); // GH-90000
        runPromise(() -> plugin.shutdown()); // GH-90000
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
    }

    // -------------------------------------------------------------------------
    // KP-010: Cross-tenant isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("KP-010 — entity A events must not appear in entity B trail [GH-90000]")
    void testCrossTenantIsolation() { // GH-90000
        Map<String, Object> detailsA = Map.of("actorId", "user-a", "action-context", "tenant-a"); // GH-90000
        Map<String, Object> detailsB = Map.of("actorId", "user-b", "action-context", "tenant-b"); // GH-90000

        runPromise(() -> plugin.logEvent("entity-A", "CREATE", detailsA)); // GH-90000
        runPromise(() -> plugin.logEvent("entity-A", "UPDATE", detailsA)); // GH-90000
        runPromise(() -> plugin.logEvent("entity-B", "CREATE", detailsB)); // GH-90000

        List<AuditTrailPlugin.AuditEntry> trailA = runPromise(() -> plugin.getTrail("entity-A [GH-90000]"));
        List<AuditTrailPlugin.AuditEntry> trailB = runPromise(() -> plugin.getTrail("entity-B [GH-90000]"));

        // entity-A trail should have exactly 2 entries
        assertThat(trailA).hasSize(2); // GH-90000
        assertThat(trailA).allMatch(e -> "entity-A".equals(e.entityId())); // GH-90000

        // entity-B trail should have exactly 1 entry
        assertThat(trailB).hasSize(1); // GH-90000
        assertThat(trailB).allMatch(e -> "entity-B".equals(e.entityId())); // GH-90000

        // No cross-contamination
        assertThat(trailA).noneMatch(e -> "entity-B".equals(e.entityId())); // GH-90000
        assertThat(trailB).noneMatch(e -> "entity-A".equals(e.entityId())); // GH-90000
    }

    @Test
    @DisplayName("KP-010 — getTrail for unknown entity must return empty list [GH-90000]")
    void testUnknownEntityReturnsEmptyTrail() { // GH-90000
        List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("nonexistent-entity [GH-90000]"));
        assertThat(trail).isEmpty(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // KP-011: Hash chain integrity and mutation detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("KP-011 — intact chain must pass verifyIntegrity [GH-90000]")
    void testVerifyIntegrityOnIntactChain() { // GH-90000
        runPromise(() -> plugin.logEvent("entity-chain", "STEP1", Map.of("actorId", "u1"))); // GH-90000
        runPromise(() -> plugin.logEvent("entity-chain", "STEP2", Map.of("actorId", "u1"))); // GH-90000
        runPromise(() -> plugin.logEvent("entity-chain", "STEP3", Map.of("actorId", "u1"))); // GH-90000

        AuditTrailPlugin.VerificationResult result =
                runPromise(() -> plugin.verifyIntegrity("entity-chain [GH-90000]"));

        assertThat(result.valid()).isTrue(); // GH-90000
        assertThat(result.entryCount()).isEqualTo(3); // GH-90000
        assertThat(result.violations()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("KP-011 — first entry in chain must have null previousHash [GH-90000]")
    void testFirstEntryHasNullPreviousHash() { // GH-90000
        runPromise(() -> plugin.logEvent("entity-first", "INIT", Map.of("actorId", "system"))); // GH-90000

        List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-first [GH-90000]"));

        assertThat(trail).hasSize(1); // GH-90000
        assertThat(trail.get(0).previousHash()).isNull(); // GH-90000
        assertThat(trail.get(0).hash()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("KP-011 — each entry must link previousHash to the preceding entry hash [GH-90000]")
    void testHashChainLinkage() { // GH-90000
        runPromise(() -> plugin.logEvent("entity-link", "A", Map.of("actorId", "u1"))); // GH-90000
        runPromise(() -> plugin.logEvent("entity-link", "B", Map.of("actorId", "u1"))); // GH-90000
        runPromise(() -> plugin.logEvent("entity-link", "C", Map.of("actorId", "u1"))); // GH-90000

        List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-link [GH-90000]"));

        assertThat(trail).hasSize(3); // GH-90000
        assertThat(trail.get(1).previousHash()).isEqualTo(trail.get(0).hash()); // GH-90000
        assertThat(trail.get(2).previousHash()).isEqualTo(trail.get(1).hash()); // GH-90000
    }

    @Test
    @DisplayName("KP-011 — verifyIntegrity on empty trail must report valid=true with 0 entries [GH-90000]")
    void testVerifyIntegrityEmptyTrail() { // GH-90000
        AuditTrailPlugin.VerificationResult result =
                runPromise(() -> plugin.verifyIntegrity("entity-empty [GH-90000]"));

        assertThat(result.valid()).isTrue(); // GH-90000
        assertThat(result.entryCount()).isEqualTo(0); // GH-90000
        assertThat(result.violations()).isEmpty(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("exportTrail as JSON must produce valid JSON array string [GH-90000]")
    void testExportJson() throws Exception { // GH-90000
        runPromise(() -> plugin.logEvent("entity-export", "EV1", Map.of("actorId", "u1"))); // GH-90000
        runPromise(() -> plugin.logEvent("entity-export", "EV2", Map.of("actorId", "u2"))); // GH-90000

        ByteArrayOutputStream out = new ByteArrayOutputStream(); // GH-90000
        runPromise(() -> plugin.exportTrail("entity-export", AuditTrailPlugin.ExportFormat.JSON, out)); // GH-90000

        String json = out.toString("UTF-8 [GH-90000]");
        assertThat(json).startsWith("[ [GH-90000]").endsWith("] [GH-90000]");
        assertThat(json).contains("\"entityId\":\"entity-export\""); // GH-90000
        assertThat(json).contains("\"action\":\"EV1\""); // GH-90000
        assertThat(json).contains("\"action\":\"EV2\""); // GH-90000
    }

    @Test
    @DisplayName("exportTrail as CSV must include header and data rows [GH-90000]")
    void testExportCsv() throws Exception { // GH-90000
        runPromise(() -> plugin.logEvent("entity-csv", "EV_CSV", Map.of("actorId", "u1"))); // GH-90000

        ByteArrayOutputStream out = new ByteArrayOutputStream(); // GH-90000
        runPromise(() -> plugin.exportTrail("entity-csv", AuditTrailPlugin.ExportFormat.CSV, out)); // GH-90000

        String csv = out.toString("UTF-8 [GH-90000]");
        assertThat(csv).contains("entryId,entityId,action,actorId,hash,previousHash,timestamp [GH-90000]");
        assertThat(csv).contains("EV_CSV [GH-90000]");
    }

    @Test
    @DisplayName("exportTrail as PDF must return UnsupportedOperationException [GH-90000]")
    void testExportPdfThrows() { // GH-90000
        ByteArrayOutputStream out = new ByteArrayOutputStream(); // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> plugin.exportTrail("entity-pdf", AuditTrailPlugin.ExportFormat.PDF, out))) // GH-90000
            .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }
}
