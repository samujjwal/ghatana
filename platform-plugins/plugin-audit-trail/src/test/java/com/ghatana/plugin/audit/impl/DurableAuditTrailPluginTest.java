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
import java.sql.Connection;
import java.sql.PreparedStatement;
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
@DisplayName("DurableAuditTrailPlugin contract tests")
@ExtendWith(MockitoExtension.class) 
class DurableAuditTrailPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private DurableAuditTrailPlugin plugin;
    private JdbcDataSource ds;

    @BeforeEach
    void setUp() { 
        ds = new JdbcDataSource(); 
        // Unique DB per test to ensure isolation between tests
        ds.setURL("jdbc:h2:mem:audit_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"); 
        plugin = new DurableAuditTrailPlugin(ds); 
        plugin.ensureSchema(); 
        runPromise(() -> plugin.initialize(mockContext)); 
        runPromise(() -> plugin.start()); 
    }

    @AfterEach
    void tearDown() { 
        runPromise(() -> plugin.stop()); 
        runPromise(() -> plugin.shutdown()); 
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Plugin should transition through UNLOADED → INITIALIZED → STARTED → STOPPED → UNLOADED")
    void testLifecycleTransitions() { 
        // Already started in setUp — verify terminal state after stop+shutdown
        runPromise(() -> plugin.stop()); 
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED); 
        runPromise(() -> plugin.shutdown()); 
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED); 
    }

        @Test
        @DisplayName("Metadata must declare durable semantics and unsupported export formats")
        void testMetadataDeclaresDurableSemantics() {
        var metadata = plugin.metadata();

        assertThat(metadata.capabilities()).contains("audit:log", "audit:verify", "audit:export");
        assertThat(metadata.properties())
            .containsEntry("variant", "durable-jdbc")
            .containsEntry("durability", "durable");
        assertThat(metadata.properties().get("supportedExportFormats"))
            .isEqualTo(List.of("JSON", "CSV", "XML"));
        assertThat(metadata.properties().get("unsupportedExportFormats"))
            .isEqualTo(List.of("PDF"));
        }

    // -------------------------------------------------------------------------
    // KP-010: Cross-tenant isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("KP-010 — entity A events must not appear in entity B trail")
    void testCrossTenantIsolation() { 
        Map<String, Object> detailsA = Map.of("actorId", "user-a", "action-context", "tenant-a"); 
        Map<String, Object> detailsB = Map.of("actorId", "user-b", "action-context", "tenant-b"); 

        runPromise(() -> plugin.logEvent("entity-A", "CREATE", detailsA)); 
        runPromise(() -> plugin.logEvent("entity-A", "UPDATE", detailsA)); 
        runPromise(() -> plugin.logEvent("entity-B", "CREATE", detailsB)); 

        List<AuditTrailPlugin.AuditEntry> trailA = runPromise(() -> plugin.getTrail("entity-A"));
        List<AuditTrailPlugin.AuditEntry> trailB = runPromise(() -> plugin.getTrail("entity-B"));

        // entity-A trail should have exactly 2 entries
        assertThat(trailA).hasSize(2); 
        assertThat(trailA).allMatch(e -> "entity-A".equals(e.entityId())); 

        // entity-B trail should have exactly 1 entry
        assertThat(trailB).hasSize(1); 
        assertThat(trailB).allMatch(e -> "entity-B".equals(e.entityId())); 

        // No cross-contamination
        assertThat(trailA).noneMatch(e -> "entity-B".equals(e.entityId())); 
        assertThat(trailB).noneMatch(e -> "entity-A".equals(e.entityId())); 
    }

    @Test
    @DisplayName("KP-010 — getTrail for unknown entity must return empty list")
    void testUnknownEntityReturnsEmptyTrail() { 
        List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("nonexistent-entity"));
        assertThat(trail).isEmpty(); 
    }

    // -------------------------------------------------------------------------
    // KP-011: Hash chain integrity and mutation detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("KP-011 — intact chain must pass verifyIntegrity")
    void testVerifyIntegrityOnIntactChain() { 
        runPromise(() -> plugin.logEvent("entity-chain", "STEP1", Map.of("actorId", "u1"))); 
        runPromise(() -> plugin.logEvent("entity-chain", "STEP2", Map.of("actorId", "u1"))); 
        runPromise(() -> plugin.logEvent("entity-chain", "STEP3", Map.of("actorId", "u1"))); 

        AuditTrailPlugin.VerificationResult result =
                runPromise(() -> plugin.verifyIntegrity("entity-chain"));

        assertThat(result.valid()).isTrue(); 
        assertThat(result.entryCount()).isEqualTo(3); 
        assertThat(result.violations()).isEmpty(); 
    }

    @Test
    @DisplayName("KP-011 — first entry in chain must have null previousHash")
    void testFirstEntryHasNullPreviousHash() { 
        runPromise(() -> plugin.logEvent("entity-first", "INIT", Map.of("actorId", "system"))); 

        List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-first"));

        assertThat(trail).hasSize(1); 
        assertThat(trail.get(0).previousHash()).isNull(); 
        assertThat(trail.get(0).hash()).isNotBlank(); 
    }

    @Test
    @DisplayName("KP-011 — each entry must link previousHash to the preceding entry hash")
    void testHashChainLinkage() { 
        runPromise(() -> plugin.logEvent("entity-link", "A", Map.of("actorId", "u1"))); 
        runPromise(() -> plugin.logEvent("entity-link", "B", Map.of("actorId", "u1"))); 
        runPromise(() -> plugin.logEvent("entity-link", "C", Map.of("actorId", "u1"))); 

        List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-link"));

        assertThat(trail).hasSize(3); 
        assertThat(trail.get(1).previousHash()).isEqualTo(trail.get(0).hash()); 
        assertThat(trail.get(2).previousHash()).isEqualTo(trail.get(1).hash()); 
    }

    @Test
    @DisplayName("KP-011 — verifyIntegrity on empty trail must report valid=true with 0 entries")
    void testVerifyIntegrityEmptyTrail() { 
        AuditTrailPlugin.VerificationResult result =
                runPromise(() -> plugin.verifyIntegrity("entity-empty"));

        assertThat(result.valid()).isTrue(); 
        assertThat(result.entryCount()).isEqualTo(0); 
        assertThat(result.violations()).isEmpty(); 
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("exportTrail as JSON must produce valid JSON array string")
    void testExportJson() throws Exception { 
        runPromise(() -> plugin.logEvent("entity-export", "EV1", Map.of("actorId", "u1"))); 
        runPromise(() -> plugin.logEvent("entity-export", "EV2", Map.of("actorId", "u2"))); 

        ByteArrayOutputStream out = new ByteArrayOutputStream(); 
        runPromise(() -> plugin.exportTrail("entity-export", AuditTrailPlugin.ExportFormat.JSON, out)); 

        String json = out.toString("UTF-8");
        assertThat(json).startsWith("[").endsWith("]");
        assertThat(json).contains("\"entityId\":\"entity-export\""); 
        assertThat(json).contains("\"action\":\"EV1\""); 
        assertThat(json).contains("\"action\":\"EV2\""); 
    }

    @Test
    @DisplayName("exportTrail as CSV must include header and data rows")
    void testExportCsv() throws Exception { 
        runPromise(() -> plugin.logEvent("entity-csv", "EV_CSV", Map.of("actorId", "u1"))); 

        ByteArrayOutputStream out = new ByteArrayOutputStream(); 
        runPromise(() -> plugin.exportTrail("entity-csv", AuditTrailPlugin.ExportFormat.CSV, out)); 

        String csv = out.toString("UTF-8");
        assertThat(csv).contains("entryId,entityId,action,actorId,hash,previousHash,timestamp");
        assertThat(csv).contains("EV_CSV");
    }

    @Test
    @DisplayName("exportTrail as PDF must return UnsupportedOperationException")
    void testExportPdfThrows() { 
        ByteArrayOutputStream out = new ByteArrayOutputStream(); 
        assertThatThrownBy(() -> 
            runPromise(() -> plugin.exportTrail("entity-pdf", AuditTrailPlugin.ExportFormat.PDF, out))) 
            .isInstanceOf(UnsupportedOperationException.class); 
    }

    // -------------------------------------------------------------------------
    // KP-011: Hash-chain mutation detection (direct DB tamper)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("KP-011 — verifyIntegrity must detect a directly tampered hash in the chain")
    void testVerifyIntegrityDetectsTamperedHash() throws Exception {
        String entityId = "entity-tamper-" + System.nanoTime();
        runPromise(() -> plugin.logEvent(entityId, "STEP1", Map.of("actorId", "u1")));
        runPromise(() -> plugin.logEvent(entityId, "STEP2", Map.of("actorId", "u1")));
        runPromise(() -> plugin.logEvent(entityId, "STEP3", Map.of("actorId", "u1")));

        // Confirm chain is intact before mutation
        AuditTrailPlugin.VerificationResult before = runPromise(() -> plugin.verifyIntegrity(entityId));
        assertThat(before.valid()).isTrue();

        // Directly corrupt the hash of the first entry in the chain
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE plugin_audit_entries SET entry_hash = 'TAMPERED_HASH_VALUE' " +
                 "WHERE entity_id = ? ORDER BY entry_ts ASC LIMIT 1")) {
            ps.setString(1, entityId);
            int rows = ps.executeUpdate();
            assertThat(rows).as("At least one row must be updated by the tamper mutation").isGreaterThan(0);
        }

        // After mutation, verifyIntegrity must report failure
        AuditTrailPlugin.VerificationResult after = runPromise(() -> plugin.verifyIntegrity(entityId));
        assertThat(after.valid())
                .as("verifyIntegrity must detect tampering and report invalid chain")
                .isFalse();
        assertThat(after.violations())
                .as("violations list must be non-empty after tamper")
                .isNotEmpty();
    }

    @Test
    @DisplayName("KP-011 — verifyIntegrity must detect a directly tampered previousHash link")
    void testVerifyIntegrityDetectsTamperedPreviousHash() throws Exception {
        String entityId = "entity-prevtamper-" + System.nanoTime();
        runPromise(() -> plugin.logEvent(entityId, "A", Map.of("actorId", "u1")));
        runPromise(() -> plugin.logEvent(entityId, "B", Map.of("actorId", "u1")));
        runPromise(() -> plugin.logEvent(entityId, "C", Map.of("actorId", "u1")));

        // Corrupt the previousHash of the third entry (breaks the back-link from C → B)
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE plugin_audit_entries SET previous_hash = 'BROKEN_LINK' " +
                 "WHERE entity_id = ? ORDER BY timestamp DESC LIMIT 1")) {
            ps.setString(1, entityId);
            ps.executeUpdate();
        }

        AuditTrailPlugin.VerificationResult result = runPromise(() -> plugin.verifyIntegrity(entityId));
        assertThat(result.valid())
                .as("broken previousHash link must cause integrity check to fail")
                .isFalse();
    }
}
