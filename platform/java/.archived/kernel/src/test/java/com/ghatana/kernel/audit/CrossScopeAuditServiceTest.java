/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.audit;

import com.ghatana.kernel.audit.CrossScopeAuditService.AuditEventStore;
import com.ghatana.kernel.audit.CrossScopeAuditService.CrossScopeAuditEvent;
import com.ghatana.kernel.audit.CrossScopeAuditService.ScopeAuditRecord;
import com.ghatana.kernel.policy.*;
import com.ghatana.kernel.policy.AuditPolicyResolver.AuditPolicy;
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
    void setUp() {
        auditStore = new InMemoryAuditStore();
        AuditPolicyResolver resolver = DefaultAuditPolicyResolver.withStandardMappings();
        service = new CrossScopeAuditService(resolver, auditStore);
    }

    // ==================== Retention Resolution ====================

    @Nested
    @DisplayName("Retention Policy Resolution")
    class RetentionPolicyResolutionTests {

        @Test
        @DisplayName("Healthcare classification resolves to 25 years per Nepal Directive 2081")
        void healthcareRetention() {
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(ScopeDescriptor.product("phr"))
                    .targetScope(ScopeDescriptor.product("finance"))
                    .action("billing.request")
                    .userId("user-1")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of(
                            "healthcare", SensitivityLevel.RESTRICTED, "nepal-2081"))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            assertEquals(1, auditStore.records.size());
            assertEquals(25, auditStore.records.get(0).getRetentionYears());
            assertEquals("archive", auditStore.records.get(0).getStorageTier());
        }

        @Test
        @DisplayName("Finance classification resolves to 10 years per SEBON regulation")
        void financeRetention() {
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(ScopeDescriptor.product("finance"))
                    .targetScope(ScopeDescriptor.product("phr"))
                    .action("payment.complete")
                    .userId("user-2")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of(
                            "finance", SensitivityLevel.CONFIDENTIAL, "sebon"))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            assertEquals(1, auditStore.records.size());
            assertEquals(10, auditStore.records.get(0).getRetentionYears());
            assertEquals("compliance", auditStore.records.get(0).getStorageTier());
        }

        @Test
        @DisplayName("Default classification resolves to 7 years")
        void defaultRetention() {
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(ScopeDescriptor.product("flashit"))
                    .targetScope(ScopeDescriptor.product("aura"))
                    .action("recommendation.request")
                    .userId("user-3")
                    .tenantId("tenant-2")
                    .classification(ClassificationDescriptor.of(
                            "general", SensitivityLevel.INTERNAL))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            assertEquals(1, auditStore.records.size());
            assertEquals(7, auditStore.records.get(0).getRetentionYears());
            assertEquals("standard", auditStore.records.get(0).getStorageTier());
        }

        @Test
        @DisplayName("Multiple compliance tags resolve to longest retention")
        void multipleComplianceTags() {
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(ScopeDescriptor.product("phr"))
                    .targetScope(ScopeDescriptor.product("finance"))
                    .action("cross-domain.operation")
                    .userId("user-4")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of(
                            "cross-domain", SensitivityLevel.RESTRICTED, "sebon", "nepal-2081"))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            assertEquals(1, auditStore.records.size());
            // nepal-2081 (25 years) wins over sebon (10 years)
            assertEquals(25, auditStore.records.get(0).getRetentionYears());
        }
    }

    // ==================== Scope-Awareness ====================

    @Nested
    @DisplayName("Scope-Awareness")
    class ScopeAwarenessTests {

        @Test
        @DisplayName("Audit records carry source and target scope descriptors")
        void recordsCarryScopeDescriptors() {
            ScopeDescriptor source = new ScopeDescriptor(
                    ScopeType.DOMAIN_PACK, "healthcare-pack", Map.of("region", "nepal"));
            ScopeDescriptor target = ScopeDescriptor.product("finance");

            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(source)
                    .targetScope(target)
                    .action("data.access")
                    .userId("user-1")
                    .tenantId("tenant-1")
                    .capabilityId("data.storage")
                    .classification(ClassificationDescriptor.of("healthcare", SensitivityLevel.RESTRICTED))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            ScopeAuditRecord record = auditStore.records.get(0);
            assertEquals(ScopeType.DOMAIN_PACK, record.getSourceScope().getScopeType());
            assertEquals("healthcare-pack", record.getSourceScope().getScopeId());
            assertEquals(ScopeType.PRODUCT, record.getTargetScope().getScopeType());
            assertEquals("finance", record.getTargetScope().getScopeId());
            assertEquals("data.storage", record.getCapabilityId());
        }

        @Test
        @DisplayName("Works with non-product scope types (tenant, workflow, agent)")
        void worksWithNonProductScopes() {
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(ScopeDescriptor.tenant("tenant-42"))
                    .targetScope(ScopeDescriptor.workflow("payment-flow-123"))
                    .action("workflow.start")
                    .userId("user-1")
                    .tenantId("tenant-42")
                    .classification(ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            ScopeAuditRecord record = auditStore.records.get(0);
            assertEquals(ScopeType.TENANT, record.getSourceScope().getScopeType());
            assertEquals(ScopeType.WORKFLOW, record.getTargetScope().getScopeType());
        }
    }

    // ==================== Record Integrity ====================

    @Nested
    @DisplayName("Record Integrity")
    class RecordIntegrityTests {

        @Test
        @DisplayName("Confidential/restricted events get cryptographic signatures")
        void restrictedEventsAreSigned() {
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(ScopeDescriptor.product("finance"))
                    .targetScope(ScopeDescriptor.product("finance"))
                    .action("trade.execute")
                    .userId("trader-1")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of(
                            "finance", SensitivityLevel.RESTRICTED, "sebon"))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            ScopeAuditRecord record = auditStore.records.get(0);
            assertNotNull(record.getSignature(), "Restricted events must be signed");
        }

        @Test
        @DisplayName("Low-sensitivity events skip signature")
        void lowSensitivityEventsSkipSignature() {
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(ScopeDescriptor.product("flashit"))
                    .targetScope(ScopeDescriptor.product("flashit"))
                    .action("content.view")
                    .userId("user-1")
                    .tenantId("tenant-1")
                    .classification(ClassificationDescriptor.of(
                            "general", SensitivityLevel.PUBLIC))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            ScopeAuditRecord record = auditStore.records.get(0);
            assertNull(record.getSignature(), "Public events should not require signature");
        }

        @Test
        @DisplayName("Audit ID is generated and non-null")
        void auditIdIsGenerated() {
            CrossScopeAuditEvent event = CrossScopeAuditEvent.builder()
                    .sourceScope(ScopeDescriptor.product("finance"))
                    .targetScope(ScopeDescriptor.product("phr"))
                    .action("data.share")
                    .classification(ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL))
                    .build();

            service.auditCrossScopeAction(event).getResult();

            assertNotNull(auditStore.records.get(0).getAuditId());
            assertTrue(auditStore.records.get(0).getAuditId().startsWith("AUDIT-"));
        }
    }

    // ==================== Policy Resolver Unit Tests ====================

    @Nested
    @DisplayName("DefaultRetentionPolicyResolver")
    class RetentionResolverTests {

        @Test
        @DisplayName("Returns default when no compliance tags match")
        void defaultsTo7Years() {
            RetentionPolicyResolver resolver = DefaultRetentionPolicyResolver.withStandardMappings();
            int years = resolver.resolveRetentionYears(
                    ScopeDescriptor.product("any"),
                    ScopeDescriptor.product("any"),
                    ClassificationDescriptor.of("general", SensitivityLevel.INTERNAL));
            assertEquals(7, years);
        }

        @Test
        @DisplayName("Custom compliance tag mappings are honored")
        void customMappingsWork() {
            RetentionPolicyResolver resolver = new DefaultRetentionPolicyResolver(
                    Map.of("custom-regulation", 15));
            int years = resolver.resolveRetentionYears(
                    ScopeDescriptor.product("any"),
                    ScopeDescriptor.product("any"),
                    ClassificationDescriptor.of("custom", SensitivityLevel.CONFIDENTIAL, "custom-regulation"));
            assertEquals(15, years);
        }
    }

    // ==================== Null/Edge Cases ====================

    @Test
    @DisplayName("Null event throws NullPointerException")
    void nullEventThrows() {
        assertThrows(NullPointerException.class, () -> service.auditCrossScopeAction(null));
    }

    @Test
    @DisplayName("Event without classification throws NullPointerException")
    void eventWithoutClassificationThrows() {
        assertThrows(NullPointerException.class, () ->
                CrossScopeAuditEvent.builder()
                        .sourceScope(ScopeDescriptor.product("a"))
                        .targetScope(ScopeDescriptor.product("b"))
                        .action("test")
                        .classification(null)
                        .build());
    }

    // ==================== In-Memory Test Store ====================

    private static class InMemoryAuditStore implements AuditEventStore {
        final List<ScopeAuditRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public Promise<Void> store(ScopeAuditRecord record) {
            records.add(record);
            return Promise.complete();
        }

        @Override
        public Promise<Set<ScopeAuditRecord>> query(Instant startDate, Instant endDate,
                                                      ScopeDescriptor sourceScope,
                                                      ScopeDescriptor targetScope) {
            Set<ScopeAuditRecord> result = new HashSet<>();
            for (ScopeAuditRecord r : records) {
                if (r.getTimestamp() != null &&
                        !r.getTimestamp().isBefore(startDate) &&
                        !r.getTimestamp().isAfter(endDate)) {
                    if (sourceScope == null || sourceScope.equals(r.getSourceScope())) {
                        if (targetScope == null || targetScope.equals(r.getTargetScope())) {
                            result.add(r);
                        }
                    }
                }
            }
            return Promise.of(result);
        }
    }
}
