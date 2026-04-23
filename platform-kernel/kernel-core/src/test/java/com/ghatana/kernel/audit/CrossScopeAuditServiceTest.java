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
@DisplayName("CrossScopeAuditService Tests")
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
    @DisplayName("Retention Policy Resolution")
    class RetentionPolicyResolutionTests {

        @Test
        @DisplayName("Healthcare classification resolves to 25 years per Nepal Directive 2081")
        void healthcareRetention() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("phr"))
                    .targetScope(ScopeDescriptor.product("finance"))
                    .action("billing.request")
                    .userId("user-1")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "healthcare", SensitivityLevel.RESTRICTED, "nepal-2081"))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            assertEquals(25, auditStore.records.get(0).getRetentionYears()); // GH-90000
            assertEquals("archive", auditStore.records.get(0).getStorageTier()); // GH-90000
        }

        @Test
        @DisplayName("Finance classification resolves to 10 years per SEBON regulation")
        void financeRetention() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("finance"))
                    .targetScope(ScopeDescriptor.product("phr"))
                    .action("payment.complete")
                    .userId("user-2")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "finance", SensitivityLevel.CONFIDENTIAL, "sebon"))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            assertEquals(10, auditStore.records.get(0).getRetentionYears()); // GH-90000
            assertEquals("compliance", auditStore.records.get(0).getStorageTier()); // GH-90000
        }

        @Test
        @DisplayName("Default classification resolves to 7 years")
        void defaultRetention() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("flashit"))
                    .targetScope(ScopeDescriptor.product("aura"))
                    .action("recommendation.request")
                    .userId("user-3")
                    .tenantId("tenant-2")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "general", SensitivityLevel.INTERNAL))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertEquals(1, auditStore.records.size()); // GH-90000
            assertEquals(7, auditStore.records.get(0).getRetentionYears()); // GH-90000
            assertEquals("standard", auditStore.records.get(0).getStorageTier()); // GH-90000
        }

        @Test
        @DisplayName("Multiple compliance tags resolve to longest retention")
        void multipleComplianceTags() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("phr"))
                    .targetScope(ScopeDescriptor.product("finance"))
                    .action("cross-domain.operation")
                    .userId("user-4")
                    .tenantId("tenant-1")
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
    @DisplayName("Scope-Awareness")
    class ScopeAwarenessTests {

        @Test
        @DisplayName("Audit records carry source and target scope descriptors")
        void recordsCarryScopeDescriptors() { // GH-90000
            ScopeDescriptor source = new ScopeDescriptor( // GH-90000
                    ScopeType.DOMAIN_PACK, "healthcare-pack", Map.of("region", "nepal")); // GH-90000
            ScopeDescriptor target = ScopeDescriptor.product("finance");

            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(source) // GH-90000
                    .targetScope(target) // GH-90000
                    .action("data.access")
                    .userId("user-1")
                    .tenantId("tenant-1")
                    .capabilityId("data.storage")
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
        @DisplayName("Works with non-product scope types (tenant, workflow, agent)")
        void worksWithNonProductScopes() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.tenant("tenant-42"))
                    .targetScope(ScopeDescriptor.workflow("payment-flow-123"))
                    .action("workflow.start")
                    .userId("user-1")
                    .tenantId("tenant-42")
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
    @DisplayName("Record Integrity")
    class RecordIntegrityTests {

        @Test
        @DisplayName("Confidential/restricted events get cryptographic signatures")
        void restrictedEventsAreSigned() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("finance"))
                    .targetScope(ScopeDescriptor.product("finance"))
                    .action("trade.execute")
                    .userId("trader-1")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "finance", SensitivityLevel.RESTRICTED, "sebon"))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord record = auditStore.records.get(0); // GH-90000
            assertNotNull(record.getSignature(), "Restricted events must be signed"); // GH-90000
        }

        @Test
        @DisplayName("Low-sensitivity events skip signature")
        void lowSensitivityEventsSkipSignature() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("flashit"))
                    .targetScope(ScopeDescriptor.product("flashit"))
                    .action("content.view")
                    .userId("user-1")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of( // GH-90000
                            "general", SensitivityLevel.PUBLIC))
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            ScopeAuditRecord record = auditStore.records.get(0); // GH-90000
            assertNull(record.getSignature(), "Public events should not require signature"); // GH-90000
        }

        @Test
        @DisplayName("Audit ID is generated and non-null")
        void auditIdIsGenerated() { // GH-90000
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder() // GH-90000
                    .sourceScope(ScopeDescriptor.product("finance"))
                    .targetScope(ScopeDescriptor.product("phr"))
                    .action("data.share")
                    .classification(ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL)) // GH-90000
                    .build(); // GH-90000

            service.auditCrossScopeAction(event).getResult(); // GH-90000

            assertNotNull(auditStore.records.get(0).getAuditId()); // GH-90000
            assertTrue(auditStore.records.get(0).getAuditId().startsWith("AUDIT-"));
        }
    }

    // ==================== Policy Resolver Unit Tests ====================

    @Nested
    @DisplayName("DefaultRetentionPolicyResolver")
    class RetentionResolverTests {

        @Test
        @DisplayName("Returns default when no compliance tags match")
        void defaultsTo7Years() { // GH-90000
            RetentionPolicyResolver resolver = DefaultRetentionPolicyResolver.withStandardMappings(); // GH-90000
            int years = resolver.resolveRetentionYears( // GH-90000
                    ScopeDescriptor.product("any"),
                    ScopeDescriptor.product("any"),
                    ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL)); // GH-90000
            assertEquals(7, years); // GH-90000
        }

        @Test
        @DisplayName("Custom compliance tag mappings are honored")
        void customMappingsWork() { // GH-90000
            RetentionPolicyResolver resolver = new DefaultRetentionPolicyResolver( // GH-90000
                    Map.of("custom-regulation", 15)); // GH-90000
            int years = resolver.resolveRetentionYears( // GH-90000
                    ScopeDescriptor.product("any"),
                    ScopeDescriptor.product("any"),
                    ClassificationDescriptor.of("custom", SensitivityLevel.CONFIDENTIAL, "custom-regulation")); // GH-90000
            assertEquals(15, years); // GH-90000
        }
    }

    // ==================== Null/Edge Cases ====================

    @Test
    @DisplayName("Null event throws NullPointerException")
    void nullEventThrows() { // GH-90000
        assertThrows(NullPointerException.class, () -> service.auditCrossScopeAction(null)); // GH-90000
    }

    @Test
    @DisplayName("Event without classification throws NullPointerException")
    void eventWithoutClassificationThrows() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                CrossScopeAuditEvent.builder() // GH-90000
                        .sourceScope(ScopeDescriptor.product("a"))
                        .targetScope(ScopeDescriptor.product("b"))
                        .action("test")
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
