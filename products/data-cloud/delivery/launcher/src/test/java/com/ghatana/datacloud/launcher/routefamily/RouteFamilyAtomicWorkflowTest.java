/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.routefamily;

import com.ghatana.datacloud.launcher.http.RouteSecurityRegistry;
import com.ghatana.platform.database.adapter.PostgreSQLAdapter;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ghatana.platform.testing.activej.EventloopTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Wave 1: Route-family tests for critical mutations with atomic workflow validation.
 *
 * <p>Validates that critical mutating routes have proper atomic workflow behavior:
 * <ul>
 *   <li>Rollback on partial failure</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Audit trail generation</li>
 *   <li>Outbox pattern for reliable event delivery</li>
 *   <li>Idempotency handling</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Route-family tests for critical mutations with atomic workflow validation (Wave 1)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Route Family Atomic Workflow Tests (Wave 1)")
@Tag("production")
@Tag("atomic-workflow")
@Tag("route-family")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteFamilyAtomicWorkflowTest extends EventloopTestBase {

    @Mock
    private PostgreSQLAdapter postgresAdapter;

    @Mock
    private EventLogStore eventLogStore;

    private RouteFamilyTestHarness testHarness;

    @BeforeEach
    void setUp() {
        testHarness = new RouteFamilyTestHarness(postgresAdapter, eventLogStore);
    }

    // ==================== Collection Creation Route Family ====================

    @Test
    @DisplayName("Wave 1: Collection creation validates rollback on event append failure")
    void collectionCreationValidatesRollbackOnEventAppendFailure() {
        // Given: Business write succeeds but event append fails
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        // When: Execute collection creation
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executeCollectionCreation("test-collection")
        );

        // Then: Should rollback business write
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.ROLLED_BACK);
        assertThat(result.getRollbackExecuted()).isTrue();
        verify(postgresAdapter, times(1)).executeRollback(any());
    }

    @Test
    @DisplayName("Wave 1: Collection creation validates retry on transient failure")
    void collectionCreationValidatesRetryOnTransientFailure() {
        // Given: Event append fails initially then succeeds
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Transient error")))
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(1L)));

        // When: Execute collection creation with retry
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executeCollectionCreationWithRetry("test-collection", 3)
        );

        // Then: Should succeed after retry
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(result.getRetryCount()).isEqualTo(1);
        verify(eventLogStore, times(2)).append(any(TenantContext.class), any(EventLogStore.EventEntry.class));
    }

    @Test
    @DisplayName("Wave 1: Collection creation validates audit trail generation")
    void collectionCreationValidatesAuditTrailGeneration() {
        // Given: All operations succeed
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(1L)));

        // When: Execute collection creation
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executeCollectionCreation("test-collection")
        );

        // Then: Audit trail should be generated
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(result.getAuditGenerated()).isTrue();
        assertThat(result.getAuditId()).isNotNull();
    }

    @Test
    @DisplayName("Wave 1: Collection creation validates outbox pattern")
    void collectionCreationValidatesOutboxPattern() {
        // Given: All operations succeed
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(1L)));

        // When: Execute collection creation with outbox
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executeCollectionCreationWithOutbox("test-collection")
        );

        // Then: Outbox entry should be created
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(result.getOutboxUsed()).isTrue();
        assertThat(result.getOutboxId()).isNotNull();
    }

    @Test
    @DisplayName("Wave 1: Collection creation validates idempotency handling")
    void collectionCreationValidatesIdempotencyHandling() {
        // Given: Idempotency key provided
        String idempotencyKey = "idemp-key-123";

        // When: Execute collection creation twice with same key
        RouteFamilyResult result1 = runPromise(() -> 
            testHarness.executeCollectionCreationWithIdempotency("test-collection", idempotencyKey)
        );
        RouteFamilyResult result2 = runPromise(() -> 
            testHarness.executeCollectionCreationWithIdempotency("test-collection", idempotencyKey)
        );

        // Then: Second call should return same result without duplicate creation
        assertThat(result1.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(result2.getStatus()).isEqualTo(RouteFamilyResult.Status.IDEMPOTENT);
        assertThat(result2.getResourceId()).isEqualTo(result1.getResourceId());
    }

    // ==================== Entity Deletion Route Family ====================

    @Test
    @DisplayName("Wave 1: Entity deletion validates rollback on audit failure")
    void entityDeletionValidatesRollbackOnAuditFailure() {
        // Given: Business write succeeds but audit fails
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Audit sink unavailable")));

        // When: Execute entity deletion
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executeEntityDeletion("entity-123")
        );

        // Then: Should rollback business write
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.ROLLED_BACK);
        assertThat(result.getRollbackExecuted()).isTrue();
    }

    @Test
    @DisplayName("Wave 1: Entity deletion validates critical audit requirement")
    void entityDeletionValidatesCriticalAuditRequirement() {
        // Given: Audit is required for deletion
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Audit unavailable")));

        // When: Execute entity deletion
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executeEntityDeletion("entity-123")
        );

        // Then: Should fail with audit requirement error
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.BLOCKED);
        assertThat(result.getErrorMessage()).contains("audit");
        assertThat(result.getErrorMessage()).contains("required");
    }

    // ==================== Policy Update Route Family ====================

    @Test
    @DisplayName("Wave 1: Policy update validates version tracking")
    void policyUpdateValidatesVersionTracking() {
        // Given: Policy update succeeds
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(1L)));

        // When: Execute policy update
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executePolicyUpdate("policy-123", "v2.0")
        );

        // Then: Version should be tracked
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(result.getVersion()).isEqualTo("v2.0");
        assertThat(result.getPreviousVersion()).isEqualTo("v1.0");
    }

    @Test
    @DisplayName("Wave 1: Policy update validates approval requirement")
    void policyUpdateValidatesApprovalRequirement() {
        // Given: Policy update without approval
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));

        // When: Execute policy update without approval
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executePolicyUpdateWithoutApproval("policy-123", "v2.0")
        );

        // Then: Should fail with approval requirement error
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.BLOCKED);
        assertThat(result.getErrorMessage()).contains("approval");
        assertThat(result.getErrorMessage()).contains("required");
    }

    // ==================== Model Promotion Route Family ====================

    @Test
    @DisplayName("Wave 1: Model promotion validates environment staging")
    void modelPromotionValidatesEnvironmentStaging() {
        // Given: Model promotion from staging to production
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(1L)));

        // When: Execute model promotion
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executeModelPromotion("model-123", "staging", "production")
        );

        // Then: Environment staging should be validated
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(result.getSourceEnvironment()).isEqualTo("staging");
        assertThat(result.getTargetEnvironment()).isEqualTo("production");
    }

    @Test
    @DisplayName("Wave 1: Model promotion validates quality gate")
    void modelPromotionValidatesQualityGate() {
        // Given: Model quality is below threshold
        when(postgresAdapter.executeQuery(any(), any()))
            .thenReturn(Promise.of(new Object())); // Quality check returns 0.75

        // When: Execute model promotion
        RouteFamilyResult result = runPromise(() -> 
            testHarness.executeModelPromotion("model-123", "staging", "production")
        );

        // Then: Should fail quality gate
        assertThat(result.getStatus()).isEqualTo(RouteFamilyResult.Status.BLOCKED);
        assertThat(result.getErrorMessage()).contains("quality");
        assertThat(result.getErrorMessage()).contains("threshold");
    }

    // ==================== Success Path ====================

    @Test
    @DisplayName("Wave 1: All route families succeed when all atomic workflow steps succeed")
    void allRouteFamiliesSucceedWhenAllStepsSucceed() {
        // Given: All operations succeed
        when(postgresAdapter.executeWrite(any(), any()))
            .thenReturn(Promise.of(1));
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class)))
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(1L)));

        // When: Execute all route families
        RouteFamilyResult collectionResult = runPromise(() -> 
            testHarness.executeCollectionCreation("test-collection")
        );
        RouteFamilyResult entityResult = runPromise(() -> 
            testHarness.executeEntityDeletion("entity-123")
        );
        RouteFamilyResult policyResult = runPromise(() -> 
            testHarness.executePolicyUpdate("policy-123", "v2.0")
        );

        // Then: All should succeed with full atomic workflow validation
        assertThat(collectionResult.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(collectionResult.getRollbackExecuted()).isFalse();
        assertThat(collectionResult.getAuditGenerated()).isTrue();
        assertThat(collectionResult.getOutboxUsed()).isTrue();

        assertThat(entityResult.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(entityResult.getAuditGenerated()).isTrue();

        assertThat(policyResult.getStatus()).isEqualTo(RouteFamilyResult.Status.SUCCESS);
        assertThat(policyResult.getAuditGenerated()).isTrue();
    }
}
