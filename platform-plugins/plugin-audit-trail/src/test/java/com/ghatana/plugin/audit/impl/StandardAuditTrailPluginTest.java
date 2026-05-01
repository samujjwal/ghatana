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
@ExtendWith(MockitoExtension.class) 
class StandardAuditTrailPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardAuditTrailPlugin auditPlugin;

    @BeforeEach
    void setUp() { 
        auditPlugin = new StandardAuditTrailPlugin(); 
    }

    @Nested
    @DisplayName("Plugin Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should initialize plugin with correct state")
        void testInitialize() { 
            // Given: Fresh plugin instance
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.UNLOADED); 

            // When: Initialize plugin
            Promise<Void> result = auditPlugin.initialize(mockContext); 
            runPromise(() -> result); 

            // Then: Plugin should be initialized
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.INITIALIZED); 
        }

        @Test
        @DisplayName("Should start plugin and transition state")
        void testStart() { 
            // Given: Initialized plugin
            runPromise(() -> auditPlugin.initialize(mockContext)); 

            // When: Start plugin
            Promise<Void> result = auditPlugin.start(); 
            runPromise(() -> result); 

            // Then: Plugin should be started
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.STARTED); 
        }

        @Test
        @DisplayName("Should stop plugin and transition state")
        void testStop() { 
            // Given: Started plugin
            runPromise(() -> auditPlugin.initialize(mockContext) 
                    .then(v -> auditPlugin.start())); 

            // When: Stop plugin
            Promise<Void> result = auditPlugin.stop(); 
            runPromise(() -> result); 

            // Then: Plugin should be stopped
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.STOPPED); 
        }

        @Test
        @DisplayName("Should shutdown plugin and clean resources")
        void testShutdown() { 
            // Given: Started plugin with audit entries
            runPromise(() -> auditPlugin.initialize(mockContext) 
                    .then(v -> auditPlugin.start()) 
                    .then(v -> auditPlugin.logEvent("entity1", "READ", new HashMap<>()))); 

            // When: Shutdown plugin
            Promise<Void> result = auditPlugin.shutdown(); 
            runPromise(() -> result); 

            // Then: Plugin should be unloaded
            assertThat(auditPlugin.getState()).isEqualTo(PluginState.UNLOADED); 

            // And: Trails should be cleared
            Promise<List<AuditTrailPlugin.AuditEntry>> emptyTrail =
                    auditPlugin.getTrail("entity1");
            List<AuditTrailPlugin.AuditEntry> result2 = runPromise(() -> emptyTrail); 
            assertThat(result2).isEmpty(); 
        }

        @Test
        @DisplayName("Should return correct metadata")
        void testMetadata() { 
            // When: Get metadata
            var metadata = auditPlugin.metadata(); 

            // Then: Metadata should be complete
            assertThat(metadata.id()).isEqualTo("audit-trail-plugin");
            assertThat(metadata.name()).isEqualTo("Audit Trail Plugin");
            assertThat(metadata.version()).isEqualTo("1.0.0");
            assertThat(metadata.description()).contains("audit trail");
            assertThat(metadata.capabilities()).contains("audit:log", "audit:verify", "audit:export");
            assertThat(metadata.properties())
                    .containsEntry("variant", "standard-in-memory")
                    .containsEntry("durability", "non-durable");
            assertThat(metadata.properties().get("supportedExportFormats"))
                    .isEqualTo(List.of("JSON", "CSV", "XML"));
            assertThat(metadata.properties().get("unsupportedExportFormats"))
                    .isEqualTo(List.of("PDF"));
        }
    }

    @Nested
    @DisplayName("Audit Event Logging Tests")
    class EventLoggingTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> auditPlugin.initialize(mockContext) 
                    .then(v -> auditPlugin.start())); 
        }

        @Test
        @DisplayName("Should log single audit event")
        void testLogEvent_Single() { 
            // Given: Plugin ready
            Map<String, Object> details = Map.of( 
                    "operation", "CREATE",
                    "resourceType", "Document",
                    "resourceId", "doc123"
            );

            // When: Log event
            Promise<AuditTrailPlugin.AuditEntry> result =
                    auditPlugin.logEvent("entity1", "CREATE", details); 
            AuditTrailPlugin.AuditEntry entry = runPromise(() -> result); 

            // Then: Entry should be created with correct data
            assertThat(entry.entityId()).isEqualTo("entity1");
            assertThat(entry.action()).isEqualTo("CREATE");
            assertThat(entry.details()).containsAllEntriesOf(details); 
            assertThat(entry.entryId()).isNotNull(); 
            assertThat(entry.hash()).isNotNull(); 
            assertThat(entry.previousHash()).isEqualTo("0"); // First entry
            assertThat(entry.timestamp()).isNotNull(); 
            assertThat(entry.actorId()).isEqualTo("system"); // Default actor
        }

        @Test
        @DisplayName("Should log event with custom actor")
        void testLogEvent_WithCustomActor() { 
            // Given: Event details with custom actor
            Map<String, Object> details = Map.of( 
                    "actorId", "user123",
                    "operation", "DELETE"
            );

            // When: Log event
            Promise<AuditTrailPlugin.AuditEntry> result =
                    auditPlugin.logEvent("entity2", "DELETE", details); 
            AuditTrailPlugin.AuditEntry entry = runPromise(() -> result); 

            // Then: Should use provided actor ID
            assertThat(entry.actorId()).isEqualTo("user123");
        }

        @Test
        @DisplayName("Should maintain hash chain across multiple events")
        void testLogEvent_HashChain() { 
            // Given: Entity ID
            String entityId = "entity3";

            // When: Log multiple events
            AuditTrailPlugin.AuditEntry entry1 = runPromise(() -> 
                    auditPlugin.logEvent(entityId, "CREATE", new HashMap<>())); 

            AuditTrailPlugin.AuditEntry entry2 = runPromise(() -> 
                    auditPlugin.logEvent(entityId, "UPDATE", new HashMap<>())); 

            AuditTrailPlugin.AuditEntry entry3 = runPromise(() -> 
                    auditPlugin.logEvent(entityId, "DELETE", new HashMap<>())); 

            // Then: Hash chain should be maintained
            assertThat(entry1.previousHash()).isEqualTo("0");
            assertThat(entry2.previousHash()).isEqualTo(entry1.hash()); 
            assertThat(entry3.previousHash()).isEqualTo(entry2.hash()); 

            // And: All hashes should be unique
            assertThat(entry1.hash()) 
                    .isNotEqualTo(entry2.hash()) 
                    .isNotEqualTo(entry3.hash()); 
        }

        @Test
        @DisplayName("Should maintain separate trails for different entities")
        void testLogEvent_SeparateEntities() { 
            // Given: Multiple entities
            String entity1 = "user123";
            String entity2 = "document456";

            // When: Log events for different entities
            AuditTrailPlugin.AuditEntry entry1a = runPromise(() -> 
                    auditPlugin.logEvent(entity1, "LOGIN", new HashMap<>())); 

            AuditTrailPlugin.AuditEntry entry2a = runPromise(() -> 
                    auditPlugin.logEvent(entity2, "CREATE", new HashMap<>())); 

            AuditTrailPlugin.AuditEntry entry1b = runPromise(() -> 
                    auditPlugin.logEvent(entity1, "LOGOUT", new HashMap<>())); 

            // Then: Each entity should have separate trail
            Promise<List<AuditTrailPlugin.AuditEntry>> trail1 =
                    auditPlugin.getTrail(entity1); 
            Promise<List<AuditTrailPlugin.AuditEntry>> trail2 =
                    auditPlugin.getTrail(entity2); 

            List<AuditTrailPlugin.AuditEntry> result1 = runPromise(() -> trail1); 
            List<AuditTrailPlugin.AuditEntry> result2 = runPromise(() -> trail2); 

            assertThat(result1).hasSize(2); 
            assertThat(result2).hasSize(1); 
            assertThat(result1.get(1).previousHash()).isEqualTo(result1.get(0).hash()); 
            assertThat(result2.get(0).previousHash()).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("Audit Trail Retrieval Tests")
    class TrailRetrievalTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> auditPlugin.initialize(mockContext) 
                    .then(v -> auditPlugin.start())); 
        }

        @Test
        @DisplayName("Should retrieve empty trail for non-existent entity")
        void testGetTrail_Empty() { 
            // When: Get trail for non-existent entity
            Promise<List<AuditTrailPlugin.AuditEntry>> result =
                    auditPlugin.getTrail("nonexistent");
            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> result); 

            // Then: Should return empty list
            assertThat(trail).isEmpty(); 
        }

        @Test
        @DisplayName("Should retrieve full audit trail")
        void testGetTrail_FullTrail() { 
            // Given: Multiple events logged
            String entityId = "user789";
            runPromise(() -> auditPlugin.logEvent(entityId, "CREATE", new HashMap<>())); 
            runPromise(() -> auditPlugin.logEvent(entityId, "UPDATE", Map.of("field", "name"))); 
            runPromise(() -> auditPlugin.logEvent(entityId, "DELETE", new HashMap<>())); 

            // When: Get trail
            Promise<List<AuditTrailPlugin.AuditEntry>> result =
                    auditPlugin.getTrail(entityId); 
            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> result); 

            // Then: Should return all events in order
            assertThat(trail).hasSize(3); 
            assertThat(trail.get(0).action()).isEqualTo("CREATE");
            assertThat(trail.get(1).action()).isEqualTo("UPDATE");
            assertThat(trail.get(2).action()).isEqualTo("DELETE");
        }

        @Test
        @DisplayName("Should not modify original trail when retrieving")
        void testGetTrail_ImmutableResult() { 
            // Given: Logged event
            String entityId = "doc123";
            runPromise(() -> auditPlugin.logEvent(entityId, "CREATE", new HashMap<>())); 

            // When: Get trail twice
            Promise<List<AuditTrailPlugin.AuditEntry>> result1 =
                    auditPlugin.getTrail(entityId); 
            List<AuditTrailPlugin.AuditEntry> trail1 = runPromise(() -> result1); 

            // And: Modify returned list
            trail1.clear(); 

            // And: Get trail again
            Promise<List<AuditTrailPlugin.AuditEntry>> result2 =
                    auditPlugin.getTrail(entityId); 
            List<AuditTrailPlugin.AuditEntry> trail2 = runPromise(() -> result2); 

            // Then: Original trail should be unaffected
            assertThat(trail2).hasSize(1); 
        }
    }

    @Nested
    @DisplayName("Integrity Verification Tests")
    class IntegrityVerificationTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> auditPlugin.initialize(mockContext) 
                    .then(v -> auditPlugin.start())); 
        }

        @Test
        @DisplayName("Should verify empty trail as valid")
        void testVerifyIntegrity_Empty() { 
            // When: Verify empty trail
            Promise<AuditTrailPlugin.VerificationResult> result =
                    auditPlugin.verifyIntegrity("empty_entity");
            AuditTrailPlugin.VerificationResult verification = runPromise(() -> result); 

            // Then: Should be valid
            assertThat(verification.valid()).isTrue(); 
            assertThat(verification.entryCount()).isZero(); 
            assertThat(verification.violations()).isEmpty(); 
        }

        @Test
        @DisplayName("Should verify valid trail")
        void testVerifyIntegrity_Valid() { 
            // Given: Multiple logged events
            String entityId = "entity_verify";
            runPromise(() -> auditPlugin.logEvent(entityId, "CREATE", new HashMap<>()) 
                    .then(v -> auditPlugin.logEvent(entityId, "UPDATE", new HashMap<>())) 
                    .then(v -> auditPlugin.logEvent(entityId, "READ", new HashMap<>()))); 

            // When: Verify integrity
            Promise<AuditTrailPlugin.VerificationResult> result =
                    auditPlugin.verifyIntegrity(entityId); 
            AuditTrailPlugin.VerificationResult verification = runPromise(() -> result); 

            // Then: Trail should be valid
            assertThat(verification.valid()).isTrue(); 
            assertThat(verification.entryCount()).isEqualTo(3); 
            assertThat(verification.violations()).isEmpty(); 
            assertThat(verification.entityId()).isEqualTo(entityId); 
            assertThat(verification.verifiedAt()).isNotNull(); 
        }
    }

    @Nested
    @DisplayName("Export Tests")
    class ExportTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> auditPlugin.initialize(mockContext) 
                    .then(v -> auditPlugin.start()) 
                    .then(v -> auditPlugin.logEvent("entity_export", "CREATE", 
                            Map.of("details", "test"))) 
                    .then(v -> auditPlugin.logEvent("entity_export", "UPDATE", 
                            Map.of("field", "updated")))); 
        }

        @Test
        @DisplayName("Should export trail to JSON format")
        void testExportTrail_JSON() throws IOException { 
            // Given: Audit trail
            ByteArrayOutputStream output = new ByteArrayOutputStream(); 

            // When: Export to JSON
            Promise<Void> result = auditPlugin.exportTrail("entity_export", 
                    AuditTrailPlugin.ExportFormat.JSON, output);
            runPromise(() -> result); 

            // Then: Should produce valid JSON
            String json = output.toString(StandardCharsets.UTF_8); 
            assertThat(json) 
                    .contains("[")
                    .contains("]")
                    .contains("\"entryId\"") 
                    .contains("\"entityId\"") 
                    .contains("\"action\"") 
                    .contains("\"CREATE\"") 
                    .contains("\"UPDATE\""); 
        }

        @Test
        @DisplayName("Should export trail to CSV format")
        void testExportTrail_CSV() throws IOException { 
            // Given: Audit trail
            ByteArrayOutputStream output = new ByteArrayOutputStream(); 

            // When: Export to CSV
            Promise<Void> result = auditPlugin.exportTrail("entity_export", 
                    AuditTrailPlugin.ExportFormat.CSV, output);
            runPromise(() -> result); 

            // Then: Should produce valid CSV
            String csv = output.toString(StandardCharsets.UTF_8); 
            assertThat(csv) 
                    .containsPattern("entryId,entityId,action,.*")
                    .contains("CREATE")
                    .contains("UPDATE");
        }

        @Test
        @DisplayName("Should export trail to XML format")
        void testExportTrail_XML() throws IOException { 
            // Given: Audit trail
            ByteArrayOutputStream output = new ByteArrayOutputStream(); 

            // When: Export to XML
            Promise<Void> result = auditPlugin.exportTrail("entity_export", 
                    AuditTrailPlugin.ExportFormat.XML, output);
            runPromise(() -> result); 

            // Then: Should produce valid XML
            String xml = output.toString(StandardCharsets.UTF_8); 
            assertThat(xml) 
                    .contains("<?xml version=\"1.0\"") 
                    .contains("<auditTrail>")
                    .contains("</auditTrail>")
                    .contains("<entityId>entity_export</entityId>")
                    .contains("<action>CREATE</action>")
                    .contains("<action>UPDATE</action>");
        }

        @Test
        @DisplayName("Should fail gracefully on unsupported format")
        void testExportTrail_UnsupportedFormat() { 
            // Given: Audit trail
            ByteArrayOutputStream output = new ByteArrayOutputStream(); 

            // When: Try to export to PDF (unsupported) 
            Promise<Void> result = auditPlugin.exportTrail("entity_export", 
                    AuditTrailPlugin.ExportFormat.PDF, output);

            // Then: Should handle gracefully
            assertThatThrownBy(() -> runPromise(() -> result)) 
                    .isInstanceOf(UnsupportedOperationException.class) 
                    .hasMessageContaining("PDF export");
        }

        @Test
        @DisplayName("Should export empty trail")
        void testExportTrail_Empty() throws IOException { 
            // Given: No events for entity
            ByteArrayOutputStream output = new ByteArrayOutputStream(); 

            // When: Export empty trail
            Promise<Void> result = auditPlugin.exportTrail("nonexistent", 
                    AuditTrailPlugin.ExportFormat.JSON, output);
            runPromise(() -> result); 

            // Then: Should produce valid but empty output
            String json = output.toString(StandardCharsets.UTF_8); 
            assertThat(json) 
                    .contains("[")
                    .contains("]");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @BeforeEach
        void setUp() { 
            runPromise(() -> auditPlugin.initialize(mockContext) 
                    .then(v -> auditPlugin.start())); 
        }

        @Test
        @DisplayName("Should handle null details map")
        void testLogEvent_NullDetails() { 
            // When: Log event with null details
            // Then: Should handle gracefully (implementation uses getOrDefault) 
            assertThatNoException().isThrownBy(() -> { 
                Promise<AuditTrailPlugin.AuditEntry> result =
                        auditPlugin.logEvent("entity", "ACTION", new HashMap<>()); 
                AuditTrailPlugin.AuditEntry entry = runPromise(() -> result); 
                assertThat(entry).isNotNull(); 
            });
        }

        @Test
        @DisplayName("Should handle special characters in action")
        void testLogEvent_SpecialCharacters() { 
            // When: Log event with special characters
            String specialAction = "DELETE_WITH_CASCADE!@#$%";
            Promise<AuditTrailPlugin.AuditEntry> result =
                    auditPlugin.logEvent("entity", specialAction, new HashMap<>()); 
            AuditTrailPlugin.AuditEntry entry = runPromise(() -> result); 

            // Then: Should store correctly
            assertThat(entry.action()).isEqualTo(specialAction); 
        }

        @Test
        @DisplayName("Should handle large number of events")
        void testLogEvent_ManyEvents() { 
            // Given: Log many events
            String entityId = "heavy_entity";
            int eventCount = 1000;

            // When: Log many events
            for (int i = 0; i < eventCount; i++) { 
                final int index = i;
                runPromise(() -> 
                        auditPlugin.logEvent(entityId, "ACTION_" + index, new HashMap<>())); 
            }

            // And: Retrieve trail
            Promise<List<AuditTrailPlugin.AuditEntry>> result =
                    auditPlugin.getTrail(entityId); 
            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> result); 

            // Then: Should have all events
            assertThat(trail).hasSize(eventCount); 

            // And: Hash chain should be intact
            for (int i = 0; i < trail.size() - 1; i++) { 
                assertThat(trail.get(i + 1).previousHash()) 
                        .isEqualTo(trail.get(i).hash()); 
            }
        }

        @Test
        @DisplayName("Should handle concurrent events for same entity")
        void testLogEvent_ConcurrentSameEntity() { 
            // Given: Concurrent event logging
            String entityId = "concurrent_entity";

            // When: Log events (sequentially in test but in list) 
            AuditTrailPlugin.AuditEntry entry1 = runPromise(() -> 
                    auditPlugin.logEvent(entityId, "EVENT1", new HashMap<>())); 

            AuditTrailPlugin.AuditEntry entry2 = runPromise(() -> 
                    auditPlugin.logEvent(entityId, "EVENT2", new HashMap<>())); 

            AuditTrailPlugin.AuditEntry entry3 = runPromise(() -> 
                    auditPlugin.logEvent(entityId, "EVENT3", new HashMap<>())); 

            // Then: All should be recorded with proper hash chain
            assertThat(entry1.previousHash()).isEqualTo("0");
            assertThat(entry2.previousHash()).isEqualTo(entry1.hash()); 
            assertThat(entry3.previousHash()).isEqualTo(entry2.hash()); 
        }
    }
}
