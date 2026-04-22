/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.kernel.audit;

import com.ghatana.kernel.audit.CrossScopeAuditService.AuditEventStore;
import com.ghatana.kernel.audit.CrossScopeAuditService.CrossScopeAuditEvent;
import com.ghatana.kernel.audit.CrossScopeAuditService.ScopeAuditRecord;
import com.ghatana.kernel.policy.*;
import com.ghatana.kernel.policy.ClassificationDescriptor.SensitivityLevel;
import com.ghatana.kernel.scope.ScopeDescriptor;
import com.ghatana.kernel.scope.ScopeType;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CrossScopeAuditService} and supporting policy classes.
 *
 * <p>Validates that the scope-aware, policy-driven audit service correctly resolves
 * retention from classification metadata rather than product ids, and that records
 * are stored with the resolved policy attributes.</p>
 *
 * @doc.type test
 * @doc.purpose Validate scope-aware audit service and policy resolution
 * @doc.layer test
 * @doc.pattern UnitTest
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
@DisplayName("CrossScopeAuditService Tests [GH-90000]")
class CrossScopeAuditServiceTest {

    private InMemoryAuditStore auditStore;
    private CrossScopeAuditService service;

    @BeforeEach
    void setUp() { // GH-90000
        auditStore = new InMemoryAuditStore(); // GH-90000
        AuditPolicyResolver resolver = DefaultAuditPolicyResolver.withStandardMappings(); // GH-90000
        service = new CrossScopeAuditService(resolver, auditStore); // GH-90000
    }

    // ==================== Retention Resolution ====================

    @Nested
    @DisplayName("Retention Policy Resolution [GH-90000]")
    class RetentionPolicyResolutionTests {

        @Test
        @DisplayName("Healthcare classification resolves to 25 years per Nepal Directive 2081 [GH-90000]")
        void healthcareRetention() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("phr [GH-90000]"))
                    .targetScope(ScopeDescriptor.product("finance [GH-90000]"))
                    .action("billing.request [GH-90000]")
                    .userId("user-1 [GH-90000]")
                    .tenantId("tenant-1 [GH-90000]")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "healthcare", SensitivityLevel.RESTRICTED, "nepal-2081"))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            assertEquals(25, auditStore.records.get(0).getRetentionYears()); // GH-90000
            assertEquals("archive", auditStore.records.get(0).getStorageTier()); // GH-90000
        }

        @Test
        @DisplayName("Finance classification resolves to 10 years per SEBON regulation [GH-90000]")
        void financeRetention() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("finance [GH-90000]"))
                    .targetScope(ScopeDescriptor.product("phr [GH-90000]"))
                    .action("payment.complete [GH-90000]")
                    .userId("user-2 [GH-90000]")
                    .tenantId("tenant-1 [GH-90000]")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "finance", SensitivityLevel.CONFIDENTIAL, "sebon"))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            assertEquals(10, auditStore.records.get(0).getRetentionYears()); // GH-90000
            assertEquals("compliance", auditStore.records.get(0).getStorageTier()); // GH-90000
        }

        @Test
        @DisplayName("Default classification resolves to 7 years [GH-90000]")
        void defaultRetention() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("flashit [GH-90000]"))
                    .targetScope(ScopeDescriptor.product("aura [GH-90000]"))
                    .action("recommendation.request [GH-90000]")
                    .userId("user-3 [GH-90000]")
                    .tenantId("tenant-2 [GH-90000]")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "general", SensitivityLevel.INTERNAL))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            assertEquals(7, auditStore.records.get(0).getRetentionYears()); // GH-90000
            assertEquals("standard", auditStore.records.get(0).getStorageTier()); // GH-90000
        }

        @Test
        @DisplayName("Multiple compliance tags resolve to longest retention [GH-90000]")
        void multipleComplianceTags() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("phr [GH-90000]"))
                    .targetScope(ScopeDescriptor.product("finance [GH-90000]"))
                    .action("cross-domain.operation [GH-90000]")
                    .userId("user-4 [GH-90000]")
                    .tenantId("tenant-1 [GH-90000]")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "cross-domain", SensitivityLevel.RESTRICTED, "sebon", "nepal-2081"))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            // nepal-2081 (25 years) wins over sebon (10 years) // GH-90000
            assertEquals(25, auditStore.records.get(0).getRetentionYears()); // GH-90000
        }
    }

    // ==================== Scope-Awareness ====================

    @Nested
    @DisplayName("Scope-Awareness [GH-90000]")
    class ScopeAwarenessTests {

        @Test
        @DisplayName("Audit records carry source and target scope descriptors [GH-90000]")
        void recordsCarryScopeDescriptors() { // GH-90000
            ScopeDescriptor source = new ScopeDescriptor( // GH-90000
                    ScopeType.DOMAIN_PACK, "healthcare-pack", Map.of("region", "nepal")); // GH-90000
            ScopeDescriptor target = ScopeDescriptor.product("finance [GH-90000]");

            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(source) // GH-90000
                    .targetScope(target) // GH-90000
                    .action("data.access [GH-90000]")
                    .userId("user-1 [GH-90000]")
                    .tenantId("tenant-1 [GH-90000]")
                    .capabilityId("data.storage [GH-90000]")
                    .classification(ClassificationDescriptor.of("healthcare", SensitivityLevel.RESTRICTED)) // GH-90000
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord record = auditStore.records.get(0); // GH-90000
            assertEquals(ScopeType.DOMAIN_PACK, record.getSourceScope().getScopeType()); // GH-90000
            assertEquals("healthcare-pack", record.getSourceScope().getScopeId()); // GH-90000
            assertEquals(ScopeType.PRODUCT, record.getTargetScope().getScopeType()); // GH-90000
            assertEquals("finance", record.getTargetScope().getScopeId()); // GH-90000
            assertEquals("data.storage", record.getCapabilityId()); // GH-90000
        }

        @Test
        @DisplayName("Works with non-product scope types (tenant, workflow, agent) [GH-90000]")
        void worksWithNonProductScopes() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.tenant("tenant-42 [GH-90000]"))
                    .targetScope(ScopeDescriptor.workflow("payment-flow-123 [GH-90000]"))
                    .action("workflow.start [GH-90000]")
                    .userId("user-1 [GH-90000]")
                    .tenantId("tenant-42 [GH-90000]")
                    .classification(ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL)) // GH-90000
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord record = auditStore.records.get(0); // GH-90000
            assertEquals(ScopeType.TENANT, record.getSourceScope().getScopeType()); // GH-90000
            assertEquals(ScopeType.WORKFLOW, record.getTargetScope().getScopeType()); // GH-90000
        }
    }

    // ==================== Record Integrity ====================

    @Nested
    @DisplayName("Record Integrity [GH-90000]")
    class RecordIntegrityTests {

        @Test
        @DisplayName("Confidential/restricted events get cryptographic signatures [GH-90000]")
        void restrictedEventsAreSigned() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("finance [GH-90000]"))
                    .targetScope(ScopeDescriptor.product("finance [GH-90000]"))
                    .action("trade.execute [GH-90000]")
                    .userId("trader-1 [GH-90000]")
                    .tenantId("tenant-1 [GH-90000]")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "finance", SensitivityLevel.RESTRICTED, "sebon"))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord record = auditStore.records.get(0); // GH-90000
            assertNotNull(record.getSignature(), "Restricted events must be signed"); // GH-90000
        }

        @Test
        @DisplayName("Low-sensitivity events skip signature [GH-90000]")
        void lowSensitivityEventsSkipSignature() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("flashit [GH-90000]"))
                    .targetScope(ScopeDescriptor.product("flashit [GH-90000]"))
                    .action("content.view [GH-90000]")
                    .userId("user-1 [GH-90000]")
                    .tenantId("tenant-1 [GH-90000]")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "general", SensitivityLevel.PUBLIC))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord record = auditStore.records.get(0); // GH-90000
            assertNull(record.getSignature(), "Public events should not require signature"); // GH-90000
        }

        @Test
        @DisplayName("Audit ID is generated and non-null [GH-90000]")
        void auditIdIsGenerated() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("finance [GH-90000]"))
                    .targetScope(ScopeDescriptor.product("phr [GH-90000]"))
                    .action("data.share [GH-90000]")
                    .classification(ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL)) // GH-90000
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertNotNull(auditStore.records.get(0).getAuditId()); // GH-90000
            assertTrue(auditStore.records.get(0).getAuditId().startsWith("AUDIT- [GH-90000]"));
        }
    }

    // ==================== Policy Resolver Unit Tests ====================

    @Nested
    @DisplayName("DefaultRetentionPolicyResolver [GH-90000]")
    class RetentionResolverTests {

        @Test
        @DisplayName("Returns default when no compliance tags match [GH-90000]")
        void defaultsTo7Years() { // GH-90000
            RetentionPolicyResolver resolver = DefaultRetentionPolicyResolver.withStandardMappings(); // GH-90000
            int years = resolver.resolveRetentionYears( // GH-90000
                    ScopeDescriptor.product("any [GH-90000]"),
                    ScopeDescriptor.product("any [GH-90000]"),
                    ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL)); // GH-90000
            assertEquals(7, years); // GH-90000
        }

        @Test
        @DisplayName("Custom compliance tag mappings are honored [GH-90000]")
        void customMappingsWork() { // GH-90000
            RetentionPolicyResolver resolver = new DefaultRetentionPolicyResolver( // GH-90000
                    Map.of("custom-regulation", 15)); // GH-90000
            int years = resolver.resolveRetentionYears( // GH-90000
                    ScopeDescriptor.product("any [GH-90000]"),
                    ScopeDescriptor.product("any [GH-90000]"),
                    ClassificationDescriptor.of("custom", SensitivityLevel.CONFIDENTIAL, "custom-regulation")); // GH-90000
            assertEquals(15, years); // GH-90000
        }
    }

    // ==================== Null/Edge Cases ====================

    @Test
    @DisplayName("Null event throws NullPointerException [GH-90000]")
    void nullEventThrows() { // GH-90000
        assertThrows(NullPointerException.class, () -> service.auditCrossScopeAction(null)); // GH-90000
    }

    @Test
    @DisplayName("Event without classification throws NullPointerException [GH-90000]")
    void eventWithoutClassificationThrows() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                CrossScopeAuditEvent.builder() // GH-90000
                        .sourceScope(ScopeDescriptor.product("a [GH-90000]"))
                        .targetScope(ScopeDescriptor.product("b [GH-90000]"))
                        .action("test [GH-90000]")
                        .classification(null) // GH-90000
                        .build()); // GH-90000
    }

    // ==================== In-Memory Test Store ====================

    private static class InMemoryAuditStore implements AuditEventStore {
        final List<ScopeAuditRecord> records = new CopyOnWriteArrayList<>(); // GH-90000

        @Override
        public Promise<Void> store(ScopeAuditRecord record) { // GH-90000
            records.add(record); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Set<ScopeAuditRecord>> query(Instant startDate, Instant endDate, // GH-90000
                                                      ScopeDescriptor sourceScope,
                                                      ScopeDescriptor targetScope) {
            Set<ScopeAuditRecord> result = new HashSet<>(); // GH-90000
            for (ScopeAuditRecord r : records) { // GH-90000
                if (r.getTimestamp() != null && // GH-90000
                        !r.getTimestamp().isBefore(startDate) && // GH-90000
                        !r.getTimestamp().isAfter(endDate)) { // GH-90000
                    if (sourceScope == null || sourceScope.equals(r.getSourceScope())) { // GH-90000
                        if (targetScope == null || targetScope.equals(r.getTargetScope())) { // GH-90000
                            result.add(r); // GH-90000
                        }
                    }
                }
            }
            return Promise.of(result); // GH-90000
        }
    }
}
