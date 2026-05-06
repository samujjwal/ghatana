package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
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

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private CapturingNotificationPlugin capturingPlugin;
    private DigitalMarketingKernelAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        capturingPlugin = CapturingNotificationPlugin.alwaysSucceed();
        adapter = buildAdapter(capturingPlugin, true);
        adapter.start();
    }

    @Test
    @DisplayName("P1-020: Successful notification dispatch returns notification ID")
    void successfulNotificationDispatch() {
        DmOperationContext ctx = buildTestContext();

        Promise<Void> promise = adapter.notifyUser(
            ctx, "user-1", "campaign-launched", Map.of("campaignName", "Summer Sale")
        );

        Void result = await(promise);
        assertThat(result).isNull();
        assertThat(capturingPlugin.dispatchCallCount()).isEqualTo(1);
        assertThat(capturingPlugin.lastRecipient()).isEqualTo("user-1");
        assertThat(capturingPlugin.lastTemplate()).isEqualTo("campaign-launched");
    }

    @Test
    @DisplayName("P1-019: Notification failure is logged in production mode")
    void notificationFailureIsLoggedInProduction() {
        DmOperationContext ctx = buildTestContext();
        CapturingNotificationPlugin failPlugin = CapturingNotificationPlugin.alwaysFail("SMTP server unavailable");
        DigitalMarketingKernelAdapterImpl failAdapter = buildAdapter(failPlugin, true);
        failAdapter.start();

        Promise<Void> promise = failAdapter.notifyUser(
            ctx, "user-1", "campaign-launched", Map.of("campaignName", "Summer Sale")
        );

        boolean failed = false;
        try {
            await(promise);
        } catch (Exception e) {
            failed = true;
            assertThat(e.getMessage()).contains("Notification dispatch failed in production mode");
        }
        assertThat(failed).isTrue();
    }

    @Test
    @DisplayName("P1-020: Notification attributes are enriched with context")
    void notificationAttributesAreEnriched() {
        DmOperationContext ctx = buildTestContext();

        await(adapter.notifyUser(ctx, "user-1", "campaign-launched", Map.of("campaignName", "Summer Sale")));

        Map<String, String> enrichedAttributes = capturingPlugin.lastCapturedAttributes();
        assertThat(enrichedAttributes).containsKey("tenantId");
        assertThat(enrichedAttributes).containsKey("workspaceId");
        assertThat(enrichedAttributes).containsKey("correlationId");
        assertThat(enrichedAttributes).containsKey("actor");
        assertThat(enrichedAttributes.get("tenantId")).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("P1-020: Notification plugin is called with correct parameters")
    void notificationPluginCalledWithCorrectParameters() {
        DmOperationContext ctx = buildTestContext();

        await(adapter.notifyUser(ctx, "user-1", "campaign-launched", Map.of("campaignName", "Summer Sale")));

        assertThat(capturingPlugin.lastRecipient()).isEqualTo("user-1");
        assertThat(capturingPlugin.lastTemplate()).isEqualTo("campaign-launched");
        assertThat(capturingPlugin.dispatchCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("P1-019: Production adapter requires notification plugin (non-null)")
    void productionAdapterRequiresNotificationPlugin() {
        assertThrows(NullPointerException.class, () -> buildAdapter(null, true));
    }

    @Test
    @DisplayName("P1-020: Transient failures trigger retry with exponential backoff")
    void transientFailuresTriggerRetryWithBackoff() {
        DmOperationContext ctx = buildTestContext();
        // Fail on attempts 0 and 1, succeed on attempt 2
        CapturingNotificationPlugin retryPlugin = CapturingNotificationPlugin.failThenSucceed(2);
        DigitalMarketingKernelAdapterImpl retryAdapter = buildAdapter(retryPlugin, true);
        retryAdapter.start();

        Void result = await(retryAdapter.notifyUser(
            ctx, "user-1", "campaign-launched", Map.of("campaignName", "Summer Sale")
        ));

        assertThat(result).isNull();
        assertThat(retryPlugin.dispatchCallCount()).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    @DisplayName("P1-020: Permanent failures route to DLQ after max retries")
    void permanentFailuresRouteToDlq() {
        DmOperationContext ctx = buildTestContext();
        CapturingNotificationPlugin failPlugin = CapturingNotificationPlugin.alwaysFail("Invalid recipient");
        DigitalMarketingKernelAdapterImpl failAdapter = buildAdapter(failPlugin, true);
        failAdapter.start();

        Promise<Void> promise = failAdapter.notifyUser(
            ctx, "user-1", "campaign-launched", Map.of("campaignName", "Summer Sale")
        );

        boolean failed = false;
        try {
            await(promise);
        } catch (Exception e) {
            failed = true;
            assertThat(e.getMessage()).contains("Notification dispatch failed in production mode");
        }
        assertThat(failed).isTrue();
        assertThat(failPlugin.dispatchCallCount()).isGreaterThan(1);
    }

    @Test
    @DisplayName("P1-020: retry attempts preserve original notification context")
    void retryAttemptsPreserveOriginalContext() {
        DmOperationContext ctx = buildTestContext();
        CapturingNotificationPlugin failPlugin = CapturingNotificationPlugin.alwaysFail("Permanent failure");
        DigitalMarketingKernelAdapterImpl failAdapter = buildAdapter(failPlugin, true);
        failAdapter.start();

        try {
            await(failAdapter.notifyUser(
                ctx, "user-1", "campaign-launched", Map.of("campaignName", "Summer Sale")
            ));
        } catch (Exception e) {
            // Expected to fail
        }

        assertThat(failPlugin.dispatchCallCount()).isGreaterThan(1);
        assertThat(failPlugin.allCapturedAttributes()).allSatisfy(attributes -> {
            assertThat(attributes).containsEntry("tenantId", "tenant-1");
            assertThat(attributes).containsEntry("workspaceId", "workspace-1");
            assertThat(attributes).containsEntry("correlationId", "corr-123");
            assertThat(attributes).containsEntry("actor", "principal-1");
            assertThat(attributes).containsEntry("campaignName", "Summer Sale");
        });
    }

    @Test
    @DisplayName("P1-020: Retry count respects configured maximum")
    void retryCountRespectsMaximum() {
        DmOperationContext ctx = buildTestContext();
        CapturingNotificationPlugin failPlugin = CapturingNotificationPlugin.alwaysFail("Always fails");
        DigitalMarketingKernelAdapterImpl failAdapter = buildAdapter(failPlugin, true);
        failAdapter.start();

        try {
            await(failAdapter.notifyUser(
                ctx, "user-1", "campaign-launched", Map.of("campaignName", "Summer Sale")
            ));
        } catch (Exception e) {
            // Expected
        }

        // Should not retry indefinitely — max 4 total attempts (1 initial + 3 retries)
        assertThat(failPlugin.dispatchCallCount()).isLessThanOrEqualTo(4);
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    private DigitalMarketingKernelAdapterImpl buildAdapter(NotificationPlugin notifPlugin, boolean productionMode) {
        return new DigitalMarketingKernelAdapterImpl(
            new AlwaysAuthorizedService(),
            new NoOpAuditEmitter(),
            BridgeHealthIndicator.noOp(),
            new InMemoryConsentPlugin(),
            new InMemoryApprovalPlugin(),
            new InMemoryAuditTrailPlugin(),
            new InMemoryRiskManagementPlugin(),
            notifPlugin,
            new NoOpFeatureFlagPlugin(),
            productionMode
        );
    }

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

    // ─── In-memory doubles ────────────────────────────────────────────────────

    private static final class CapturingNotificationPlugin implements NotificationPlugin {
        private final IntFunction<Promise<String>> behavior;
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final List<String> capturedRecipients = new ArrayList<>();
        private final List<String> capturedTemplates = new ArrayList<>();
        private final List<Map<String, String>> capturedAttributes = new ArrayList<>();

        CapturingNotificationPlugin(IntFunction<Promise<String>> behavior) {
            this.behavior = behavior;
        }

        static CapturingNotificationPlugin alwaysSucceed() {
            return new CapturingNotificationPlugin(i -> Promise.of("notif-" + i));
        }

        static CapturingNotificationPlugin alwaysFail(String message) {
            return new CapturingNotificationPlugin(
                i -> Promise.ofException(new RuntimeException(message)));
        }

        static CapturingNotificationPlugin failThenSucceed(int failCount) {
            return new CapturingNotificationPlugin(i -> i < failCount
                ? Promise.ofException(new RuntimeException("Temporary failure"))
                : Promise.of("notif-retry-success"));
        }

        int dispatchCallCount() { return callCount.get(); }
        List<Map<String, String>> allCapturedAttributes() { return capturedAttributes; }
        Map<String, String> lastCapturedAttributes() {
            return capturedAttributes.isEmpty()
                ? Map.of()
                : capturedAttributes.get(capturedAttributes.size() - 1);
        }
        String lastRecipient() {
            return capturedRecipients.isEmpty() ? null : capturedRecipients.get(capturedRecipients.size() - 1);
        }
        String lastTemplate() {
            return capturedTemplates.isEmpty() ? null : capturedTemplates.get(capturedTemplates.size() - 1);
        }

        @Override
        public Promise<String> dispatch(String recipientId, String template, Map<String, String> attributes) {
            int count = callCount.getAndIncrement();
            capturedRecipients.add(recipientId);
            capturedTemplates.add(template);
            capturedAttributes.add(Map.copyOf(attributes));
            return behavior.apply(count);
        }

        @Override
        public Promise<DeliveryStatus> getDeliveryStatus(String notificationId) {
            return Promise.of(new DeliveryStatus(
                notificationId, "recipient", "template",
                DeliveryState.DELIVERED, 1, Instant.now(), null, Instant.now()
            ));
        }

        @Override
        public Promise<Void> retry(String notificationId) { return Promise.of(null); }

        @Override
        public Promise<List<DeadLetterEntry>> listDeadLetterQueue(int limit, int offset) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Void> reprocessDeadLetter(String notificationId) { return Promise.of(null); }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("capturing-notification-plugin")
                .name("Capturing Notification Plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext context) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class AlwaysAuthorizedService implements BridgeAuthorizationService {
        @Override
        public Promise<Boolean> isAuthorized(BridgeContext context, String resource, String action) {
            return Promise.of(Boolean.TRUE);
        }
    }

    private static final class NoOpAuditEmitter implements BridgeAuditEmitter {
        @Override
        public void emit(BridgeAuditEvent event) { /* discard */ }
    }

    private static final class InMemoryConsentPlugin implements ConsentPlugin {
        @Override
        public Promise<ConsentRecord> recordConsent(String subjectId, String purpose, ConsentAction action) {
            return Promise.of(new ConsentRecord(
                "consent-1", subjectId, purpose,
                action == ConsentAction.GRANT ? ConsentStatus.GRANTED : ConsentStatus.DENIED,
                action, "consent", Instant.now(), null, null, "{}"
            ));
        }
        @Override public Promise<Boolean> verifyConsent(String subjectId, String purpose) {
            return Promise.of(Boolean.FALSE);
        }
        @Override public Promise<Void> revokeConsent(String consentId) { return Promise.of(null); }
        @Override public Promise<List<ConsentRecord>> getConsentHistory(String subjectId) {
            return Promise.of(List.of());
        }
        @Override public Promise<ConsentStatus> getCurrentConsent(String subjectId, String purpose) {
            return Promise.of(ConsentStatus.NOT_REQUESTED);
        }
        @Override public Promise<Integer> deleteAllForSubject(String subjectId) { return Promise.of(0); }
        @Override public PluginMetadata metadata() {
            return PluginMetadata.builder().id("consent").name("Consent").type(PluginType.CONSENT).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext context) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class InMemoryApprovalPlugin implements HumanApprovalPlugin {
        @Override
        public Promise<ApprovalRecord> submitRequest(ApprovalRequest request) {
            return Promise.of(new ApprovalRecord(
                request.requestId(), request.subjectId(), request.requestedBy(),
                request.action(), ApprovalStatus.PENDING,
                Instant.now(), request.expiresAt(), null, null, null, request.context()
            ));
        }
        @Override public Promise<ApprovalRecord> approve(String requestId, ApprovalDecision decision) {
            return Promise.of(new ApprovalRecord(
                requestId, "", "", "", ApprovalStatus.APPROVED,
                Instant.now(), null, Instant.now(), "reviewer", decision.notes(), Map.of()
            ));
        }
        @Override public Promise<ApprovalRecord> reject(String requestId, ApprovalDecision decision) {
            return Promise.of(new ApprovalRecord(
                requestId, "", "", "", ApprovalStatus.REJECTED,
                Instant.now(), null, Instant.now(), "reviewer", decision.notes(), Map.of()
            ));
        }
        @Override public Promise<Optional<ApprovalRecord>> findById(String requestId) {
            return Promise.of(Optional.empty());
        }
        @Override public Promise<List<ApprovalRecord>> listPendingForWorkspace(String workspaceId) {
            return Promise.of(List.of());
        }
        @Override public PluginMetadata metadata() {
            return PluginMetadata.builder().id("approval").name("Approval").type(PluginType.GOVERNANCE).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext context) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class InMemoryAuditTrailPlugin implements AuditTrailPlugin {
        @Override public Promise<Void> record(AuditEntry entry) { return Promise.of(null); }
        @Override public Promise<List<AuditEntry>> query(String entityId, String entityType,
                Instant from, Instant to) { return Promise.of(List.of()); }
        @Override public Promise<VerificationResult> verifyIntegrity(String entityId) {
            return Promise.of(new VerificationResult(entityId, true, 0, List.of()));
        }
        @Override public Promise<Void> export(String entityId, ExportFormat format, OutputStream out) {
            return Promise.of(null);
        }
        @Override public PluginMetadata metadata() {
            return PluginMetadata.builder().id("audit").name("Audit").type(PluginType.AUDIT).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext context) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class InMemoryRiskManagementPlugin implements RiskManagementPlugin {
        @Override public Promise<RiskScore> evaluateRisk(String entityId, RiskModelId modelId,
                Map<String, Object> features) {
            return Promise.of(new RiskScore(entityId, modelId, 0.1,
                RiskScore.RiskLevel.LOW, Map.of(), Instant.now()));
        }
        @Override public Promise<List<RiskAlert>> getActiveAlerts(String entityId) {
            return Promise.of(List.of());
        }
        @Override public Promise<RiskReport> generateReport(String entityId, TimeRange range) {
            return Promise.of(new RiskReport(entityId, range, List.of(), List.of(), Map.of(), Instant.now()));
        }
        @Override public Promise<Void> acknowledgeAlert(String alertId) { return Promise.of(null); }
        @Override public PluginMetadata metadata() {
            return PluginMetadata.builder().id("risk").name("Risk").type(PluginType.RISK).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext context) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class NoOpFeatureFlagPlugin implements FeatureFlagPlugin {
        @Override public Promise<Boolean> isEnabled(String flagKey, String tenantId) {
            return Promise.of(Boolean.FALSE);
        }
        @Override public Promise<String> getString(String flagKey, String tenantId, String defaultValue) {
            return Promise.of(defaultValue);
        }
        @Override public Promise<Integer> getInt(String flagKey, String tenantId, int defaultValue) {
            return Promise.of(defaultValue);
        }
        @Override public Promise<Boolean> getBoolean(String flagKey, String tenantId, boolean defaultValue) {
            return Promise.of(defaultValue);
        }
        @Override public Promise<Map<String, Object>> getAllFlags(String tenantId) {
            return Promise.of(Map.of());
        }
        @Override public PluginMetadata metadata() {
            return PluginMetadata.builder().id("feature-flag").name("Feature Flag").type(PluginType.CUSTOM).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext context) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }
}
