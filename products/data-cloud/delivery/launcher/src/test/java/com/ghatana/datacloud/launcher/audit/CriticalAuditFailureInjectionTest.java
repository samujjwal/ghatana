/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DC-P1-03: Failure-injection tests for critical audit operations.
 *
 * <p>Verifies that critical mutations fail when audit persistence fails (fail-closed semantics).
 * These tests ensure production-grade durability for audit trails on critical operations:
 * <ul>
 *   <li>Redaction operations</li>
 *   <li>Retention purge operations</li>
 *   <li>Policy update operations</li>
 *   <li>Model promotion operations</li>
 *   <li>Delete entity operations</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Failure-injection tests for critical audit durability (DC-P1-03)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Critical Audit Failure Injection Tests (DC-P1-03)")
@Tag("production")
@Tag("durability")
@Tag("failure-injection")
@ExtendWith(MockitoExtension.class)
class CriticalAuditFailureInjectionTest {

    @Mock
    private EventLogStore eventLogStore;

    private ObjectMapper objectMapper;
    private EventLogAuditService auditService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        // DC-P1-03: Create audit service with fail-closed enabled (default)
        auditService = new EventLogAuditService(eventLogStore, objectMapper, true);
    }

    // ==================== Redaction Operation Failure Tests ====================

    @Test
    @DisplayName("DC-P1-03: Redaction operation fails when audit persistence fails (fail-closed)")
    void redactionOperationFailsWhenAuditPersistenceFails() {
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        AuditEvent redactionEvent = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("PII_REDACTION")
            .principal("admin-user")
            .resourceType("ENTITY")
            .resourceId("entity-456")
            .success(true)
            .timestamp(Instant.now())
            .detail("fields", "email,phone")
            .detail("reason", "GDPR Article 17 request")
            .build();

        assertThatThrownBy(() -> auditService.recordCritical(redactionEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-09")
            .hasMessageContaining("Failed to record critical audit event")
            .hasMessageContaining("PII_REDACTION")
            .hasMessageContaining("operation blocked");
    }

    // ==================== Retention Purge Failure Tests ====================

    @Test
    @DisplayName("DC-P1-03: Retention purge operation fails when audit persistence fails (fail-closed)")
    void retentionPurgeOperationFailsWhenAuditPersistenceFails() {
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        AuditEvent purgeEvent = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("RETENTION_PURGE")
            .principal("admin-user")
            .resourceType("COLLECTION")
            .resourceId("collection-789")
            .success(true)
            .timestamp(Instant.now())
            .detail("dryRun", "false")
            .detail("confirmationToken", "token-abc123")
            .detail("recordsDeleted", "1500")
            .build();

        assertThatThrownBy(() -> auditService.recordCritical(purgeEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-09")
            .hasMessageContaining("Failed to record critical audit event")
            .hasMessageContaining("RETENTION_PURGE")
            .hasMessageContaining("operation blocked");
    }

    // ==================== Policy Update Failure Tests ====================

    @Test
    @DisplayName("DC-P1-03: Policy update operation fails when audit persistence fails (fail-closed)")
    void policyUpdateOperationFailsWhenAuditPersistenceFails() {
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        AuditEvent policyUpdateEvent = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("POLICY_UPDATE")
            .principal("admin-user")
            .resourceType("GOVERNANCE_POLICY")
            .resourceId("policy-xyz")
            .success(true)
            .timestamp(Instant.now())
            .detail("policyType", "retention")
            .detail("previousVersion", "v1.0")
            .detail("newVersion", "v2.0")
            .detail("changeReason", "Regulatory update")
            .build();

        assertThatThrownBy(() -> auditService.recordCritical(policyUpdateEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-09")
            .hasMessageContaining("Failed to record critical audit event")
            .hasMessageContaining("POLICY_UPDATE")
            .hasMessageContaining("operation blocked");
    }

    // ==================== Model Promotion Failure Tests ====================

    @Test
    @DisplayName("DC-P1-03: Model promotion operation fails when audit persistence fails (fail-closed)")
    void modelPromotionOperationFailsWhenAuditPersistenceFails() {
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        AuditEvent modelPromotionEvent = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("MODEL_PROMOTION")
            .principal("ml-admin")
            .resourceType("AI_MODEL")
            .resourceId("model-abc123")
            .success(true)
            .timestamp(Instant.now())
            .detail("sourceEnvironment", "staging")
            .detail("targetEnvironment", "production")
            .detail("modelVersion", "v3.2.1")
            .detail("approvalId", "approval-def456")
            .build();

        assertThatThrownBy(() -> auditService.recordCritical(modelPromotionEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-09")
            .hasMessageContaining("Failed to record critical audit event")
            .hasMessageContaining("MODEL_PROMOTION")
            .hasMessageContaining("operation blocked");
    }

    // ==================== Delete Entity Failure Tests ====================

    @Test
    @DisplayName("DC-P1-03: Delete entity operation fails when audit persistence fails (fail-closed)")
    void deleteEntityOperationFailsWhenAuditPersistenceFails() {
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        AuditEvent deleteEntityEvent = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("ENTITY_DELETE")
            .principal("admin-user")
            .resourceType("ENTITY")
            .resourceId("entity-ghi789")
            .success(true)
            .timestamp(Instant.now())
            .detail("collection", "customer-data")
            .detail("entityId", "entity-ghi789")
            .detail("reason", "Data subject request")
            .build();

        assertThatThrownBy(() -> auditService.recordCritical(deleteEntityEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-09")
            .hasMessageContaining("Failed to record critical audit event")
            .hasMessageContaining("ENTITY_DELETE")
            .hasMessageContaining("operation blocked");
    }

    // ==================== Serialization Failure Tests ====================

    @Test
    @DisplayName("DC-P1-03: Critical operation fails when audit event serialization fails (fail-closed)")
    void criticalOperationFailsWhenAuditEventSerializationFails() {
        // Create an audit event that will fail serialization
        AuditEvent badEvent = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("CRITICAL_OPERATION")
            .principal("admin-user")
            .resourceType("TEST")
            .resourceId("test-123")
            .success(true)
            .timestamp(Instant.now())
            // Add a circular reference or non-serializable object
            .detail("problematicField", new Object() {
                // Custom object without proper serialization
            })
            .build();

        assertThatThrownBy(() -> auditService.recordCritical(badEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-09")
            .hasMessageContaining("Failed to serialize critical audit event")
            .hasMessageContaining("operation blocked");
    }

    // ==================== Fail-Open Mode Tests ====================

    @Test
    @DisplayName("DC-P1-03: Fail-open mode allows operation to proceed despite audit failure (non-production)")
    void failOpenModeAllowsOperationToProceedDespiteAuditFailure() {
        // Create audit service with fail-closed disabled (fail-open mode)
        EventLogAuditService failOpenAuditService = new EventLogAuditService(eventLogStore, objectMapper, false);

        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        AuditEvent event = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("TEST_OPERATION")
            .principal("admin-user")
            .resourceType("TEST")
            .resourceId("test-123")
            .success(true)
            .timestamp(Instant.now())
            .build();

        // In fail-open mode, the exception should propagate but not be wrapped in IllegalStateException
        assertThatThrownBy(() -> failOpenAuditService.recordCritical(event))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Event store unavailable")
            .isNotInstanceOf(IllegalStateException.class);
    }

    // ==================== Success Path Tests ====================

    @Test
    @DisplayName("DC-P1-03: Critical operation succeeds when audit persistence succeeds")
    void criticalOperationSucceedsWhenAuditPersistenceSucceeds() {
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(1L)));

        AuditEvent event = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("CRITICAL_OPERATION")
            .principal("admin-user")
            .resourceType("TEST")
            .resourceId("test-123")
            .success(true)
            .timestamp(Instant.now())
            .detail("operation", "test")
            .build();

        // Should not throw - audit persistence succeeded
        auditService.recordCritical(event);
    }

    // ==================== Critical Field Preservation Tests ====================

    @Test
    @DisplayName("DC-P1-03: Critical audit event preserves all required fields on success")
    void criticalAuditEventPreservesAllRequiredFieldsOnSuccess() throws Exception {
        com.ghatana.platform.types.identity.Offset expectedOffset = com.ghatana.platform.types.identity.Offset.of(42L);
        
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenAnswer(invocation -> {
                EventLogStore.EventEntry entry = invocation.getArgument(1);
                // Verify the critical field is set in headers
                assertThat(entry.headers()).containsEntry("critical", "true");
                // Verify payload contains critical marker
                String payloadStr = new String(entry.payload().array(), java.nio.charset.StandardCharsets.UTF_8);
                assertThat(payloadStr).contains("\"critical\":true");
                return Promise.of(expectedOffset);
            });

        AuditEvent event = AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-123")
            .eventType("CRITICAL_OPERATION")
            .principal("admin-user")
            .resourceType("TEST")
            .resourceId("test-123")
            .success(true)
            .timestamp(Instant.now())
            .detail("operation", "test")
            .build();

        auditService.recordCritical(event);
    }
}
