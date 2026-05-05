package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
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
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.notification.NotificationPlugin;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DigitalMarketingKernelAdapterImpl")
class DigitalMarketingKernelAdapterImplTest extends EventloopTestBase {

    private RecordingBridgeAuthorizationService authService;
    private RecordingBridgeAuditEmitter auditEmitter;
    private RecordingBridgeHealthIndicator healthIndicator;
    private InMemoryConsentPlugin consentPlugin;
    private InMemoryApprovalPlugin approvalPlugin;
    private InMemoryAuditTrailPlugin auditTrailPlugin;
    private InMemoryRiskManagementPlugin riskManagementPlugin;
    private InMemoryNotificationPlugin notificationPlugin;
    private DigitalMarketingKernelAdapterImpl adapter;

    private static final DmTenantId TENANT = DmTenantId.of("acme-corp");
    private static final DmWorkspaceId WORKSPACE = DmWorkspaceId.of("ws-001");
    private static final ActorRef ACTOR = ActorRef.user("user-42");
    private static final DmCorrelationId CORR = DmCorrelationId.of("corr-abc");

    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        authService = new RecordingBridgeAuthorizationService();
        auditEmitter = new RecordingBridgeAuditEmitter();
        healthIndicator = new RecordingBridgeHealthIndicator();
        consentPlugin = new InMemoryConsentPlugin();
        approvalPlugin = new InMemoryApprovalPlugin();
        auditTrailPlugin = new InMemoryAuditTrailPlugin();
        riskManagementPlugin = new InMemoryRiskManagementPlugin();
        notificationPlugin = new InMemoryNotificationPlugin();

        adapter = new DigitalMarketingKernelAdapterImpl(
            authService,
            auditEmitter,
            healthIndicator,
            consentPlugin,
            approvalPlugin,
            auditTrailPlugin,
            riskManagementPlugin,
            notificationPlugin
        );

        ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .build();
    }

    @Test
    @DisplayName("lifecycle toggles started state")
    void shouldToggleStartedState() {
        assertThat(adapter.started()).isFalse();
        adapter.start();
        assertThat(adapter.started()).isTrue();
        adapter.stop();
        assertThat(adapter.started()).isFalse();
    }

    @Test
    @DisplayName("isAuthorized rejects calls before start")
    void shouldRejectAuthorizationBeforeStart() {
        assertThatIllegalStateException()
            .isThrownBy(() -> adapter.isAuthorized(ctx, "campaigns/c-1", "launch"))
            .withMessageContaining("not started");
    }

    @Test
    @DisplayName("isAuthorized delegates to bridge auth and propagates bridge context")
    void shouldDelegateAuthorization() {
        adapter.start();
        authService.setDecision("campaigns/c-1", "launch", true);

        boolean result = runPromise(() -> adapter.isAuthorized(ctx, "campaigns/c-1", "launch"));

        assertThat(result).isTrue();
        assertThat(authService.lastContext().getTenantId()).isEqualTo("acme-corp");
        assertThat(authService.lastContext().getPrincipalId()).isEqualTo("user-42");
        assertThat(authService.lastContext().getCorrelationId()).isEqualTo("corr-abc");
    }

    @Test
    @DisplayName("verifyConsent delegates to consent plugin")
    void shouldVerifyConsent() {
        adapter.start();
        consentPlugin.setConsent("contact-1", "marketing-email", true);

        boolean result = runPromise(() -> adapter.verifyConsent(ctx, "contact-1", "marketing-email"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("requestApproval returns request id from approval plugin")
    void shouldRequestApproval() {
        adapter.start();

        String requestId = runPromise(
            () -> adapter.requestApproval(ctx, "LaunchCampaign", "campaign-1", "Launch Q4 campaign")
        );

        assertThat(requestId).isNotBlank();
        assertThat(approvalPlugin.lastRequest().subjectId()).isEqualTo("campaign-1");
        assertThat(approvalPlugin.lastRequest().requestedBy()).isEqualTo("user-42");
    }

    @Test
    @DisplayName("recordAudit enriches attributes and returns audit entry id")
    void shouldRecordAuditWithContextAttributes() {
        adapter.start();

        String entryId = runPromise(
            () -> adapter.recordAudit(ctx, "campaign-1", "launch", Map.of("channel", "email"))
        );

        assertThat(entryId).isNotBlank();
        assertThat(auditTrailPlugin.lastDetails())
            .containsEntry("tenantId", "acme-corp")
            .containsEntry("workspaceId", "ws-001")
            .containsEntry("correlationId", "corr-abc")
            .containsEntry("actor", "user-42")
            .containsEntry("channel", "email");
    }

    @Test
    @DisplayName("evaluateRisk delegates to risk plugin")
    void shouldEvaluateRisk() {
        adapter.start();
        riskManagementPlugin.setScore(0.42d);

        double score = runPromise(() -> adapter.evaluateRisk(
            ctx,
            "campaign-1",
            "DM_CAMPAIGN_LAUNCH",
            Map.of("budget", 1000)
        ));

        assertThat(score).isEqualTo(0.42d);
    }

    @Test
    @DisplayName("notifyUser logs notification dispatch")
    void shouldNotifyUser() {
        adapter.start();

        runPromise(() -> adapter.notifyUser(
            ctx,
            "user-123",
            "dmos.campaign.launched",
            Map.of("campaignName", "Q4 Promo")
        ));

        // Should complete without error - the implementation logs the notification
    }

    @Test
    @DisplayName("notifyUser rejects calls before start")
    void shouldRejectNotifyBeforeStart() {
        assertThatIllegalStateException()
            .isThrownBy(() -> adapter.notifyUser(
                ctx,
                "user-123",
                "dmos.campaign.launched",
                Map.of("campaignName", "Q4 Promo")
            ))
            .withMessageContaining("not started");
    }

    @Test
    @DisplayName("constructor rejects null required dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                null, auditEmitter, healthIndicator, consentPlugin, approvalPlugin, auditTrailPlugin,
                riskManagementPlugin, notificationPlugin
            ));

        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                authService, auditEmitter, healthIndicator, null, approvalPlugin, auditTrailPlugin,
                riskManagementPlugin, notificationPlugin
            ))
            .withMessageContaining("consentPlugin");

        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                authService, auditEmitter, healthIndicator, consentPlugin, null, auditTrailPlugin,
                riskManagementPlugin, notificationPlugin
            ))
            .withMessageContaining("approvalPlugin");

        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                authService, auditEmitter, healthIndicator, consentPlugin, approvalPlugin, null,
                riskManagementPlugin, notificationPlugin
            ))
            .withMessageContaining("auditTrailPlugin");

        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                authService, auditEmitter, healthIndicator, consentPlugin, approvalPlugin, auditTrailPlugin,
                null, notificationPlugin
            ))
            .withMessageContaining("riskManagementPlugin");
    }

    private static final class RecordingBridgeAuthorizationService implements BridgeAuthorizationService {
        private final Map<String, Boolean> decisions = new java.util.HashMap<>();
        private BridgeContext lastContext;

        void setDecision(String resource, String action, boolean allowed) {
            decisions.put(resource + "|" + action, allowed);
        }

        BridgeContext lastContext() {
            return lastContext;
        }

        @Override
        public Promise<Boolean> isAuthorized(BridgeContext context, String resource, String action) {
            lastContext = context;
            boolean allowed = decisions.getOrDefault(resource + "|" + action, true);
            return Promise.of(allowed);
        }
    }

    private static final class RecordingBridgeAuditEmitter implements BridgeAuditEmitter {
        private final List<BridgeAuditEvent> events = new ArrayList<>();

        @Override
        public void emit(BridgeAuditEvent event) {
            events.add(event);
        }
    }

    private static final class RecordingBridgeHealthIndicator implements BridgeHealthIndicator {
        @Override
        public void reportHealthy(String bridgeId) {
            // no-op for adapter tests
        }

        @Override
        public void reportDegraded(String bridgeId, String reason) {
            // no-op for adapter tests
        }

        @Override
        public void reportUnhealthy(String bridgeId, String reason) {
            // no-op for adapter tests
        }
    }

    private static final class InMemoryConsentPlugin implements ConsentPlugin {
        private final Map<String, Boolean> consents = new java.util.HashMap<>();

        void setConsent(String subjectId, String purpose, boolean granted) {
            consents.put(subjectId + "|" + purpose, granted);
        }

        @Override
        public Promise<ConsentRecord> recordConsent(String subjectId, String purpose, ConsentAction action) {
            return Promise.of(new ConsentRecord(
                "consent-1",
                subjectId,
                purpose,
                action == ConsentAction.GRANT ? ConsentStatus.GRANTED : ConsentStatus.DENIED,
                action,
                "consent",
                Instant.now(),
                null,
                null,
                "{}"
            ));
        }

        @Override
        public Promise<Boolean> verifyConsent(String subjectId, String purpose) {
            return Promise.of(consents.getOrDefault(subjectId + "|" + purpose, false));
        }

        @Override
        public Promise<Void> revokeConsent(String consentId) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<ConsentRecord>> getConsentHistory(String subjectId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<ConsentStatus> getCurrentConsent(String subjectId, String purpose) {
            boolean granted = consents.getOrDefault(subjectId + "|" + purpose, false);
            return Promise.of(granted ? ConsentStatus.GRANTED : ConsentStatus.NOT_REQUESTED);
        }

        @Override
        public Promise<Integer> deleteAllForSubject(String subjectId) {
            return Promise.of(0);
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("dm-consent-test-plugin")
                .name("DM Consent Test Plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> start() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> stop() {
            return Promise.of(null);
        }
    }

    private static final class InMemoryApprovalPlugin implements HumanApprovalPlugin {
        private ApprovalRequest lastRequest;

        ApprovalRequest lastRequest() {
            return lastRequest;
        }

        @Override
        public Promise<ApprovalRecord> requestApproval(ApprovalRequest request) {
            lastRequest = request;
            return Promise.of(new ApprovalRecord(
                request.requestId(),
                request.subjectId(),
                request.requestedBy(),
                request.action(),
                ApprovalStatus.PENDING,
                request.requestedAt(),
                request.expiresAt(),
                null,
                null,
                null,
                request.context()
            ));
        }

        @Override
        public Promise<Optional<ApprovalRecord>> getApprovalStatus(String requestId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<ApprovalRecord> completeApproval(
            String requestId,
            ApprovalDecision decision,
            String reviewerId,
            String notes
        ) {
            return Promise.of(new ApprovalRecord(
                requestId,
                "subject",
                "requester",
                "action",
                decision == ApprovalDecision.APPROVED ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED,
                Instant.now(),
                null,
                Instant.now(),
                reviewerId,
                notes,
                Map.of()
            ));
        }

        @Override
        public Promise<List<ApprovalRecord>> listPendingForSubject(String subjectId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<ApprovalRecord>> listPendingForWorkspace(String workspaceId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Void> cancelApproval(String requestId, String reason) {
            return Promise.of(null);
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("dm-approval-test-plugin")
                .name("DM Approval Test Plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> start() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> stop() {
            return Promise.of(null);
        }
    }

    private static final class InMemoryRiskManagementPlugin implements RiskManagementPlugin {
        private double score = 0.1d;

        void setScore(double score) {
            this.score = score;
        }

        @Override
        public Promise<RiskScore> calculateRisk(String entityId, RiskModelId modelId, Map<String, Object> factors) {
            return Promise.of(new RiskScore(
                entityId,
                modelId,
                score,
                RiskScore.RiskLevel.LOW,
                Map.of("composite", score),
                Instant.now()
            ));
        }

        @Override
        public Promise<Void> setRiskLimits(String entityId, Map<String, java.math.BigDecimal> limits) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<RiskAlert>> getActiveAlerts(String entityId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<RiskReport> generateReport(String entityId, TimeRange range) {
            return Promise.of(new RiskReport(entityId, range, List.of(), List.of(), Map.of(), Instant.now()));
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("dm-risk-test")
                .name("DM Risk Test Plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> start() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> stop() {
            return Promise.of(null);
        }
    }

    private static final class InMemoryAuditTrailPlugin implements AuditTrailPlugin {
        private Map<String, Object> lastDetails = Map.of();

        Map<String, Object> lastDetails() {
            return lastDetails;
        }

        @Override
        public Promise<AuditEntry> logEvent(String entityId, String action, Map<String, Object> details) {
            lastDetails = Map.copyOf(details);
            return Promise.of(new AuditEntry(
                "audit-entry-1",
                entityId,
                action,
                lastDetails,
                "user-42",
                "hash",
                null,
                Instant.now()
            ));
        }

        @Override
        public Promise<List<AuditEntry>> getTrail(String entityId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<VerificationResult> verifyIntegrity(String entityId) {
            return Promise.of(new VerificationResult(entityId, true, 0, List.of(), Instant.now()));
        }

        @Override
        public Promise<Void> exportTrail(String entityId, ExportFormat format, OutputStream out) {
            return Promise.of(null);
        }

        @Override
        public Promise<Integer> purgeEntriesOlderThan(long cutoffEpochMs) {
            return Promise.of(0);
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("dm-audit-test-plugin")
                .name("DM Audit Test Plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> start() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> stop() {
            return Promise.of(null);
        }
    }

    private static final class InMemoryNotificationPlugin implements NotificationPlugin {

        @Override
        public Promise<String> dispatch(String recipientId, String template, Map<String, String> attributes) {
            return Promise.of(java.util.UUID.randomUUID().toString());
        }

        @Override
        public Promise<NotificationPlugin.DeliveryStatus> getDeliveryStatus(String notificationId) {
            return Promise.of(new NotificationPlugin.DeliveryStatus(
                notificationId, "recipient", "template",
                NotificationPlugin.DeliveryState.DELIVERED, 1,
                java.time.Instant.now(), null, java.time.Instant.now()
            ));
        }

        @Override
        public Promise<Void> retry(String notificationId) {
            return Promise.of(null);
        }

        @Override
        public Promise<java.util.List<NotificationPlugin.DeadLetterEntry>> listDeadLetterQueue(int limit, int offset) {
            return Promise.of(java.util.List.of());
        }

        @Override
        public Promise<Void> reprocessDeadLetter(String notificationId) {
            return Promise.of(null);
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("dm-notification-test-plugin")
                .name("DM Notification Test Plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> start() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> stop() {
            return Promise.of(null);
        }
    }
}
