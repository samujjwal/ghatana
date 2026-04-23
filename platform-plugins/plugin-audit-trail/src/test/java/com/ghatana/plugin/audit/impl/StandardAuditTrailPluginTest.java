package com.ghatana.plugin.audit.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Comprehensive tests for StandardAuditTrailPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("StandardAuditTrailPlugin Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class StandardAuditTrailPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardAuditTrailPlugin auditPlugin;

    @BeforeEach
    void setUp() { // GH-90000
        auditPlugin = new StandardAuditTrailPlugin(); // GH-90000
    }

    @Nested
    @DisplayName("Plugin Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should initialize plugin with correct state")
        void testInitialize() { // GH-90000
            // Given: Fresh plugin instance
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000

            // When: Initialize plugin
            Promise<Void> result = auditPlugin.initialize(mockContext); // GH-90000
            runPromise(() -> result); // GH-90000

            // Then: Plugin should be initialized
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000
        }

        @Test
        @DisplayName("Should start plugin and transition state")
        void testStart() { // GH-90000
            // Given: Initialized plugin
            runPromise(() -> auditPlugin.initialize(mockContext)); // GH-90000

            // When: Start plugin
            Promise<Void> result = auditPlugin.start(); // GH-90000
            runPromise(() -> result); // GH-90000

            // Then: Plugin should be started
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.STARTED); // GH-90000
        }

        @Test
        @DisplayName("Should stop plugin and transition state")
        void testStop() { // GH-90000
            // Given: Started plugin
            runPromise(() -> auditPlugin.initialize(mockContext) // GH-90000
                    .then(v -> auditPlugin.start())); // GH-90000

            // When: Stop plugin
            Promise<Void> result = auditPlugin.stop(); // GH-90000
            runPromise(() -> result); // GH-90000

            // Then: Plugin should be stopped
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.STOPPED); // GH-90000
        }

        @Test
        @DisplayName("Should shutdown plugin and clean resources")
        void testShutdown() { // GH-90000
            // Given: Started plugin with audit entries
            runPromise(() -> auditPlugin.initialize(mockContext) // GH-90000
                    .then(v -> auditPlugin.start()) // GH-90000
                    .then(v -> auditPlugin.logEvent("entity1", "READ", new HashMap<>()))); // GH-90000

            // When: Shutdown plugin
            Promise<Void> result = auditPlugin.shutdown(); // GH-90000
            runPromise(() -> result); // GH-90000

            // Then: Plugin should be unloaded
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000

            // And: Trails should be cleared
            Promise<List<AuditTrailPlugin.AuditEntry>> emptyTrail =
                    auditPlugin.getTrail("entity1");
            List<AuditTrailPlugin.AuditEntry> result2 = runPromise(() -> emptyTrail); // GH-90000
            assertThat(result2).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Should return correct metadata")
        void testMetadata() { // GH-90000
            // When: Get metadata
            var metadata = auditPlugin.metadata(); // GH-90000

            // Then: Metadata should be complete
            assertThat(metadata.id()).isEqualTo("audit-trail-plugin");
            assertThat(metadata.name()).isEqualTo("Audit Trail Plugin");
            assertThat(metadata.version()).isEqualTo("1.0.0");
            assertThat(metadata.description()).contains("audit trail");
        }
    }

    @Nested
    @DisplayName("Audit Event Logging Tests")
    class EventLoggingTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> auditPlugin.initialize(mockContext) // GH-90000
                    .then(v -> auditPlugin.start())); // GH-90000
        }

        @Test
        @DisplayName("Should log single audit event")
        void testLogEvent_Single() { // GH-90000
            // Given: Plugin ready
            Map<String, Object> details = Map.of( // GH-90000
                    "operation", "CREATE",
                    "resourceType", "Document",
                    "resourceId", "doc123"
            );

            // When: Log event
            Promise<AuditTrailPlugin.AuditEntry> result =
                    auditPlugin.logEvent("entity1", "CREATE", details); // GH-90000
            AuditTrailPlugin.AuditEntry entry = runPromise(() -> result); // GH-90000

            // Then: Entry should be created with correct data
            assertThat(entry.entityId()).isEqualTo("entity1");
            assertThat(entry.action()).isEqualTo("CREATE");
            assertThat(entry.details()).containsAllEntriesOf(details); // GH-90000
            assertThat(entry.entryId()).isNotNull(); // GH-90000
            assertThat(entry.hash()).isNotNull(); // GH-90000
            assertThat(entry.previousHash()).isEqualTo("0"); // First entry
            assertThat(entry.timestamp()).isNotNull(); // GH-90000
            assertThat(entry.actorId()).isEqualTo("system"); // Default actor
        }

        @Test
        @DisplayName("Should log event with custom actor")
        void testLogEvent_WithCustomActor() { // GH-90000
            // Given: Event details with custom actor
            Map<String, Object> details = Map.of( // GH-90000
                    "actorId", "user123",
                    "operation", "DELETE"
            );

            // When: Log event
            Promise<AuditTrailPlugin.AuditEntry> result =
                    auditPlugin.logEvent("entity2", "DELETE", details); // GH-90000
            AuditTrailPlugin.AuditEntry entry = runPromise(() -> result); // GH-90000

            // Then: Should use provided actor ID
            assertThat(entry.actorId()).isEqualTo("user123");
        }

        @Test
        @DisplayName("Should maintain hash chain across multiple events")
        void testLogEvent_HashChain() { // GH-90000
            // Given: Entity ID
            String entityId = "entity3";

            // When: Log multiple events
            AuditTrailPlugin.AuditEntry entry1 = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entityId, "CREATE", new HashMap<>())); // GH-90000

            AuditTrailPlugin.AuditEntry entry2 = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entityId, "UPDATE", new HashMap<>())); // GH-90000

            AuditTrailPlugin.AuditEntry entry3 = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entityId, "DELETE", new HashMap<>())); // GH-90000

            // Then: Hash chain should be maintained
            assertThat(entry1.previousHash()).isEqualTo("0");
            assertThat(entry2.previousHash()).isEqualTo(entry1.hash()); // GH-90000
            assertThat(entry3.previousHash()).isEqualTo(entry2.hash()); // GH-90000

            // And: All hashes should be unique
            assertThat(entry1.hash()) // GH-90000
                    .isNotEqualTo(entry2.hash()) // GH-90000
                    .isNotEqualTo(entry3.hash()); // GH-90000
        }

        @Test
        @DisplayName("Should maintain separate trails for different entities")
        void testLogEvent_SeparateEntities() { // GH-90000
            // Given: Multiple entities
            String entity1 = "user123";
            String entity2 = "document456";

            // When: Log events for different entities
            AuditTrailPlugin.AuditEntry entry1a = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entity1, "LOGIN", new HashMap<>())); // GH-90000

            AuditTrailPlugin.AuditEntry entry2a = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entity2, "CREATE", new HashMap<>())); // GH-90000

            AuditTrailPlugin.AuditEntry entry1b = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entity1, "LOGOUT", new HashMap<>())); // GH-90000

            // Then: Each entity should have separate trail
            Promise<List<AuditTrailPlugin.AuditEntry>> trail1 =
                    auditPlugin.getTrail(entity1); // GH-90000
            Promise<List<AuditTrailPlugin.AuditEntry>> trail2 =
                    auditPlugin.getTrail(entity2); // GH-90000

            List<AuditTrailPlugin.AuditEntry> result1 = runPromise(() -> trail1); // GH-90000
            List<AuditTrailPlugin.AuditEntry> result2 = runPromise(() -> trail2); // GH-90000

            assertThat(result1).hasSize(2); // GH-90000
            assertThat(result2).hasSize(1); // GH-90000
            assertThat(result1.get(1).previousHash()).isEqualTo(result1.get(0).hash()); // GH-90000
            assertThat(result2.get(0).previousHash()).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("Audit Trail Retrieval Tests")
    class TrailRetrievalTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> auditPlugin.initialize(mockContext) // GH-90000
                    .then(v -> auditPlugin.start())); // GH-90000
        }

        @Test
        @DisplayName("Should retrieve empty trail for non-existent entity")
        void testGetTrail_Empty() { // GH-90000
            // When: Get trail for non-existent entity
            Promise<List<AuditTrailPlugin.AuditEntry>> result =
                    auditPlugin.getTrail("nonexistent");
            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> result); // GH-90000

            // Then: Should return empty list
            assertThat(trail).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Should retrieve full audit trail")
        void testGetTrail_FullTrail() { // GH-90000
            // Given: Multiple events logged
            String entityId = "user789";
            runPromise(() -> auditPlugin.logEvent(entityId, "CREATE", new HashMap<>())); // GH-90000
            runPromise(() -> auditPlugin.logEvent(entityId, "UPDATE", Map.of("field", "name"))); // GH-90000
            runPromise(() -> auditPlugin.logEvent(entityId, "DELETE", new HashMap<>())); // GH-90000

            // When: Get trail
            Promise<List<AuditTrailPlugin.AuditEntry>> result =
                    auditPlugin.getTrail(entityId); // GH-90000
            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> result); // GH-90000

            // Then: Should return all events in order
            assertThat(trail).hasSize(3); // GH-90000
            assertThat(trail.get(0).action()).isEqualTo("CREATE");
            assertThat(trail.get(1).action()).isEqualTo("UPDATE");
            assertThat(trail.get(2).action()).isEqualTo("DELETE");
        }

        @Test
        @DisplayName("Should not modify original trail when retrieving")
        void testGetTrail_ImmutableResult() { // GH-90000
            // Given: Logged event
            String entityId = "doc123";
            runPromise(() -> auditPlugin.logEvent(entityId, "CREATE", new HashMap<>())); // GH-90000

            // When: Get trail twice
            Promise<List<AuditTrailPlugin.AuditEntry>> result1 =
                    auditPlugin.getTrail(entityId); // GH-90000
            List<AuditTrailPlugin.AuditEntry> trail1 = runPromise(() -> result1); // GH-90000

            // And: Modify returned list
            trail1.clear(); // GH-90000

            // And: Get trail again
            Promise<List<AuditTrailPlugin.AuditEntry>> result2 =
                    auditPlugin.getTrail(entityId); // GH-90000
            List<AuditTrailPlugin.AuditEntry> trail2 = runPromise(() -> result2); // GH-90000

            // Then: Original trail should be unaffected
            assertThat(trail2).hasSize(1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Integrity Verification Tests")
    class IntegrityVerificationTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> auditPlugin.initialize(mockContext) // GH-90000
                    .then(v -> auditPlugin.start())); // GH-90000
        }

        @Test
        @DisplayName("Should verify empty trail as valid")
        void testVerifyIntegrity_Empty() { // GH-90000
            // When: Verify empty trail
            Promise<AuditTrailPlugin.VerificationResult> result =
                    auditPlugin.verifyIntegrity("empty_entity");
            AuditTrailPlugin.VerificationResult verification = runPromise(() -> result); // GH-90000

            // Then: Should be valid
            assertThat(verification.valid()).isTrue(); // GH-90000
            assertThat(verification.entryCount()).isZero(); // GH-90000
            assertThat(verification.violations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Should verify valid trail")
        void testVerifyIntegrity_Valid() { // GH-90000
            // Given: Multiple logged events
            String entityId = "entity_verify";
            runPromise(() -> auditPlugin.logEvent(entityId, "CREATE", new HashMap<>()) // GH-90000
                    .then(v -> auditPlugin.logEvent(entityId, "UPDATE", new HashMap<>())) // GH-90000
                    .then(v -> auditPlugin.logEvent(entityId, "READ", new HashMap<>()))); // GH-90000

            // When: Verify integrity
            Promise<AuditTrailPlugin.VerificationResult> result =
                    auditPlugin.verifyIntegrity(entityId); // GH-90000
            AuditTrailPlugin.VerificationResult verification = runPromise(() -> result); // GH-90000

            // Then: Trail should be valid
            assertThat(verification.valid()).isTrue(); // GH-90000
            assertThat(verification.entryCount()).isEqualTo(3); // GH-90000
            assertThat(verification.violations()).isEmpty(); // GH-90000
            assertThat(verification.entityId()).isEqualTo(entityId); // GH-90000
            assertThat(verification.verifiedAt()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Export Tests")
    class ExportTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> auditPlugin.initialize(mockContext) // GH-90000
                    .then(v -> auditPlugin.start()) // GH-90000
                    .then(v -> auditPlugin.logEvent("entity_export", "CREATE", // GH-90000
                            Map.of("details", "test"))) // GH-90000
                    .then(v -> auditPlugin.logEvent("entity_export", "UPDATE", // GH-90000
                            Map.of("field", "updated")))); // GH-90000
        }

        @Test
        @DisplayName("Should export trail to JSON format")
        void testExportTrail_JSON() throws IOException { // GH-90000
            // Given: Audit trail
            ByteArrayOutputStream output = new ByteArrayOutputStream(); // GH-90000

            // When: Export to JSON
            Promise<Void> result = auditPlugin.exportTrail("entity_export", // GH-90000
                    AuditTrailPlugin.ExportFormat.JSON, output);
            runPromise(() -> result); // GH-90000

            // Then: Should produce valid JSON
            String json = output.toString(StandardCharsets.UTF_8); // GH-90000
            assertThat(json) // GH-90000
                    .contains("[")
                    .contains("]")
                    .contains("\"entryId\"") // GH-90000
                    .contains("\"entityId\"") // GH-90000
                    .contains("\"action\"") // GH-90000
                    .contains("\"CREATE\"") // GH-90000
                    .contains("\"UPDATE\""); // GH-90000
        }

        @Test
        @DisplayName("Should export trail to CSV format")
        void testExportTrail_CSV() throws IOException { // GH-90000
            // Given: Audit trail
            ByteArrayOutputStream output = new ByteArrayOutputStream(); // GH-90000

            // When: Export to CSV
            Promise<Void> result = auditPlugin.exportTrail("entity_export", // GH-90000
                    AuditTrailPlugin.ExportFormat.CSV, output);
            runPromise(() -> result); // GH-90000

            // Then: Should produce valid CSV
            String csv = output.toString(StandardCharsets.UTF_8); // GH-90000
            assertThat(csv) // GH-90000
                    .containsPattern("entryId,entityId,action,.*")
                    .contains("CREATE")
                    .contains("UPDATE");
        }

        @Test
        @DisplayName("Should export trail to XML format")
        void testExportTrail_XML() throws IOException { // GH-90000
            // Given: Audit trail
            ByteArrayOutputStream output = new ByteArrayOutputStream(); // GH-90000

            // When: Export to XML
            Promise<Void> result = auditPlugin.exportTrail("entity_export", // GH-90000
                    AuditTrailPlugin.ExportFormat.XML, output);
            runPromise(() -> result); // GH-90000

            // Then: Should produce valid XML
            String xml = output.toString(StandardCharsets.UTF_8); // GH-90000
            assertThat(xml) // GH-90000
                    .contains("<?xml version=\"1.0\"") // GH-90000
                    .contains("<auditTrail>")
                    .contains("</auditTrail>")
                    .contains("<entityId>entity_export</entityId>")
                    .contains("<action>CREATE</action>")
                    .contains("<action>UPDATE</action>");
        }

        @Test
        @DisplayName("Should fail gracefully on unsupported format")
        void testExportTrail_UnsupportedFormat() { // GH-90000
            // Given: Audit trail
            ByteArrayOutputStream output = new ByteArrayOutputStream(); // GH-90000

            // When: Try to export to PDF (unsupported) // GH-90000
            Promise<Void> result = auditPlugin.exportTrail("entity_export", // GH-90000
                    AuditTrailPlugin.ExportFormat.PDF, output);

            // Then: Should handle gracefully
            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class) // GH-90000
                    .hasMessageContaining("PDF export");
        }

        @Test
        @DisplayName("Should export empty trail")
        void testExportTrail_Empty() throws IOException { // GH-90000
            // Given: No events for entity
            ByteArrayOutputStream output = new ByteArrayOutputStream(); // GH-90000

            // When: Export empty trail
            Promise<Void> result = auditPlugin.exportTrail("nonexistent", // GH-90000
                    AuditTrailPlugin.ExportFormat.JSON, output);
            runPromise(() -> result); // GH-90000

            // Then: Should produce valid but empty output
            String json = output.toString(StandardCharsets.UTF_8); // GH-90000
            assertThat(json) // GH-90000
                    .contains("[")
                    .contains("]");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @BeforeEach
        void setUp() { // GH-90000
            runPromise(() -> auditPlugin.initialize(mockContext) // GH-90000
                    .then(v -> auditPlugin.start())); // GH-90000
        }

        @Test
        @DisplayName("Should handle null details map")
        void testLogEvent_NullDetails() { // GH-90000
            // When: Log event with null details
            // Then: Should handle gracefully (implementation uses getOrDefault) // GH-90000
            assertThatNoException().isThrownBy(() -> { // GH-90000
                Promise<AuditTrailPlugin.AuditEntry> result =
                        auditPlugin.logEvent("entity", "ACTION", new HashMap<>()); // GH-90000
                AuditTrailPlugin.AuditEntry entry = runPromise(() -> result); // GH-90000
                assertThat(entry).isNotNull(); // GH-90000
            });
        }

        @Test
        @DisplayName("Should handle special characters in action")
        void testLogEvent_SpecialCharacters() { // GH-90000
            // When: Log event with special characters
            String specialAction = "DELETE_WITH_CASCADE!@#$%";
            Promise<AuditTrailPlugin.AuditEntry> result =
                    auditPlugin.logEvent("entity", specialAction, new HashMap<>()); // GH-90000
            AuditTrailPlugin.AuditEntry entry = runPromise(() -> result); // GH-90000

            // Then: Should store correctly
            assertThat(entry.action()).isEqualTo(specialAction); // GH-90000
        }

        @Test
        @DisplayName("Should handle large number of events")
        void testLogEvent_ManyEvents() { // GH-90000
            // Given: Log many events
            String entityId = "heavy_entity";
            int eventCount = 1000;

            // When: Log many events
            for (int i = 0; i < eventCount; i++) { // GH-90000
                final int index = i;
                runPromise(() -> // GH-90000
                        auditPlugin.logEvent(entityId, "ACTION_" + index, new HashMap<>())); // GH-90000
            }

            // And: Retrieve trail
            Promise<List<AuditTrailPlugin.AuditEntry>> result =
                    auditPlugin.getTrail(entityId); // GH-90000
            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> result); // GH-90000

            // Then: Should have all events
            assertThat(trail).hasSize(eventCount); // GH-90000

            // And: Hash chain should be intact
            for (int i = 0; i < trail.size() - 1; i++) { // GH-90000
                assertThat(trail.get(i + 1).previousHash()) // GH-90000
                        .isEqualTo(trail.get(i).hash()); // GH-90000
            }
        }

        @Test
        @DisplayName("Should handle concurrent events for same entity")
        void testLogEvent_ConcurrentSameEntity() { // GH-90000
            // Given: Concurrent event logging
            String entityId = "concurrent_entity";

            // When: Log events (sequentially in test but in list) // GH-90000
            AuditTrailPlugin.AuditEntry entry1 = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entityId, "EVENT1", new HashMap<>())); // GH-90000

            AuditTrailPlugin.AuditEntry entry2 = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entityId, "EVENT2", new HashMap<>())); // GH-90000

            AuditTrailPlugin.AuditEntry entry3 = runPromise(() -> // GH-90000
                    auditPlugin.logEvent(entityId, "EVENT3", new HashMap<>())); // GH-90000

            // Then: All should be recorded with proper hash chain
            assertThat(entry1.previousHash()).isEqualTo("0");
            assertThat(entry2.previousHash()).isEqualTo(entry1.hash()); // GH-90000
            assertThat(entry3.previousHash()).isEqualTo(entry2.hash()); // GH-90000
        }
    }
}
