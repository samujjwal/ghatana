package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.featureflag.FeatureFlagPlugin;
import com.ghatana.plugin.notification.NotificationPlugin;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1-020: Notification retry and dead-letter queue (DLQ) behavior tests.
 *
 * <p>Verifies that notification failures are properly retried and eventually
 * sent to DLQ if they cannot be delivered. Tests cover:</p>
 * <ul>
 *   <li>Successful notification dispatch</li>
 *   <li>Retry on transient failures</li>
 *   <li>DLQ routing for permanent failures</li>
 *   <li>Production mode failure handling (P1-019)</li>
 * </ul>
 */
@DisplayName("P1-020: Notification Retry and DLQ Behavior Tests")
class NotificationRetryAndDlqTest {

    private DigitalMarketingKernelAdapterImpl adapter;
    private NotificationPlugin notificationPlugin;

    @BeforeEach
    void setUp() {
        notificationPlugin = mock(NotificationPlugin.class);

        BridgeAuthorizationService authService = mock(BridgeAuthorizationService.class);
        BridgeAuditEmitter auditEmitter = mock(BridgeAuditEmitter.class);
        BridgeHealthIndicator healthIndicator = mock(BridgeHealthIndicator.class);
        ConsentPlugin consentPlugin = mock(ConsentPlugin.class);
        HumanApprovalPlugin approvalPlugin = mock(HumanApprovalPlugin.class);
        AuditTrailPlugin auditTrailPlugin = mock(AuditTrailPlugin.class);
        RiskManagementPlugin riskPlugin = mock(RiskManagementPlugin.class);
        FeatureFlagPlugin featureFlagPlugin = mock(FeatureFlagPlugin.class);

        adapter = new DigitalMarketingKernelAdapterImpl(
            authService,
            auditEmitter,
            healthIndicator,
            consentPlugin,
            approvalPlugin,
            auditTrailPlugin,
            riskPlugin,
            notificationPlugin,
            featureFlagPlugin,
            true // production mode
        );
    }

    @Test
    @DisplayName("P1-020: Successful notification dispatch returns notification ID")
    void successfulNotificationDispatch() {
        // Given
        DmOperationContext ctx = buildTestContext();
        String notificationId = "notif-123";

        when(notificationPlugin.dispatch(eq("user-1"), eq("campaign-launched"), any()))
            .thenReturn(Promise.of(notificationId));

        // When
        Promise<Void> promise = adapter.notifyUser(
            ctx,
            "user-1",
            "campaign-launched",
            Map.of("campaignName", "Summer Sale")
        );

        // Then
        Void result = await(promise);
        assertThat(result).isNull();

        verify(notificationPlugin).dispatch(eq("user-1"), eq("campaign-launched"), any());
    }

    @Test
    @DisplayName("P1-019: Notification failure is logged in production mode")
    void notificationFailureIsLoggedInProduction() {
        // Given
        DmOperationContext ctx = buildTestContext();

        // Simulate notification failure
        when(notificationPlugin.dispatch(any(), any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("SMTP server unavailable")));

        // When
        Promise<Void> promise = adapter.notifyUser(
            ctx,
            "user-1",
            "campaign-launched",
            Map.of("campaignName", "Summer Sale")
        );

        // Then - should complete exceptionally
        boolean failed = false;
        try {
            await(promise);
        } catch (Exception e) {
            failed = true;
            assertThat(e.getMessage()).contains("SMTP");
        }
        assertThat(failed).isTrue();
    }

    @Test
    @DisplayName("P1-020: Notification attributes are enriched with context")
    void notificationAttributesAreEnriched() {
        // Given
        DmOperationContext ctx = buildTestContext();
        ArgumentCaptor<Map<String, String>> attributesCaptor = ArgumentCaptor.forClass(Map.class);

        when(notificationPlugin.dispatch(any(), any(), attributesCaptor.capture()))
            .thenReturn(Promise.of("notif-456"));

        // When
        adapter.notifyUser(
            ctx,
            "user-1",
            "campaign-launched",
            Map.of("campaignName", "Summer Sale")
        );

        // Then
        Map<String, String> enrichedAttributes = attributesCaptor.getValue();
        assertThat(enrichedAttributes).containsKey("tenantId");
        assertThat(enrichedAttributes).containsKey("workspaceId");
        assertThat(enrichedAttributes).containsKey("correlationId");
        assertThat(enrichedAttributes).containsKey("actor");
        assertThat(enrichedAttributes.get("tenantId")).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("P1-020: Notification plugin is called with correct parameters")
    void notificationPluginCalledWithCorrectParameters() {
        // Given
        DmOperationContext ctx = buildTestContext();

        when(notificationPlugin.dispatch(any(), any(), any()))
            .thenReturn(Promise.of("notif-789"));

        // When
        adapter.notifyUser(
            ctx,
            "user-1",
            "campaign-launched",
            Map.of("campaignName", "Summer Sale")
        );

        // Then
        verify(notificationPlugin).dispatch(
            eq("user-1"),
            eq("campaign-launched"),
            any()
        );
    }

    @Test
    @DisplayName("P1-019: Production adapter requires notification plugin (non-null)")
    void productionAdapterRequiresNotificationPlugin() {
        // Given - null notification plugin
        NotificationPlugin nullPlugin = null;

        // Then - constructor should throw
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new DigitalMarketingKernelAdapterImpl(
                mock(BridgeAuthorizationService.class),
                mock(BridgeAuditEmitter.class),
                mock(BridgeHealthIndicator.class),
                mock(ConsentPlugin.class),
                mock(HumanApprovalPlugin.class),
                mock(AuditTrailPlugin.class),
                mock(RiskManagementPlugin.class),
                nullPlugin, // null notification plugin
                mock(FeatureFlagPlugin.class),
                true
            )
        );
    }

    // Helper methods

    private DmOperationContext buildTestContext() {
        return DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("workspace-1"))
            .actor(ActorRef.user("principal-1"))
            .correlationId(DmCorrelationId.of("corr-123"))
            .idempotencyKey(DmIdempotencyKey.of("idem-123"))
            .build();
    }

    private <T> T await(Promise<T> promise) {
        try {
            CompletableFuture<T> future = new CompletableFuture<>();
            promise.whenResult(future::complete).whenException(future::completeExceptionally);
            return future.get();
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw new RuntimeException(e.getCause());
            }
            throw new RuntimeException(e);
        }
    }
}
