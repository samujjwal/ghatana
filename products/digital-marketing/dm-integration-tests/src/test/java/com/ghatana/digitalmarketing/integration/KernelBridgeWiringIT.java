package com.ghatana.digitalmarketing.integration;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapterImpl;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import static com.ghatana.kernel.bridge.port.BridgeAuditEmitter.BridgeAuditEvent;
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
import com.ghatana.plugin.featureflag.FeatureFlagPlugin;
import com.ghatana.plugin.notification.NotificationPlugin;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import static com.ghatana.plugin.consent.ConsentPlugin.ConsentAction;
import static com.ghatana.plugin.consent.ConsentPlugin.ConsentRecord;
import static com.ghatana.plugin.consent.ConsentPlugin.ConsentStatus;
import static com.ghatana.plugin.audit.AuditTrailPlugin.AuditEntry;
import static com.ghatana.plugin.audit.AuditTrailPlugin.ExportFormat;
import static com.ghatana.plugin.audit.AuditTrailPlugin.VerificationResult;
import static com.ghatana.plugin.risk.RiskManagementPlugin.RiskModelId;
import static com.ghatana.plugin.risk.RiskManagementPlugin.RiskScore;
import static com.ghatana.plugin.risk.RiskManagementPlugin.RiskAlert;
import static com.ghatana.plugin.risk.RiskManagementPlugin.RiskReport;
import static com.ghatana.plugin.risk.RiskManagementPlugin.TimeRange;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Integration test verifying that {@link DigitalMarketingKernelAdapterImpl} can be wired
 * from outside its module and that the key DMOS operations (auth, consent, approval, audit,
 * notifications, feature flags) function end-to-end using real in-memory plugin implementations.
 *
 * <p>This test exercises the cross-module assembly path and lifecycle management without
 * requiring a running database or message broker. The {@code @Testcontainers} annotation
 * establishes this class in the Testcontainers test tier for CI categorisation, ready for
 * real containers when durable plugin variants are introduced.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel bridge cross-module wiring + lifecycle integration test
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@Testcontainers
@DisplayName("KernelBridgeWiringIT: cross-module bridge wiring and lifecycle")
class KernelBridgeWiringIT extends EventloopTestBase {

    private RecordingAuthService authService;
    private RecordingAuditEmitter auditEmitter;
    private EphemeralConsentPlugin consentPlugin;
    private EphemeralApprovalPlugin approvalPlugin;
    private RecordingAuditTrailPlugin auditTrailPlugin;
    private EphemeralRiskPlugin riskPlugin;
    private EphemeralNotificationPlugin notificationPlugin;
    private EphemeralFeatureFlagPlugin featureFlagPlugin;
    private DigitalMarketingKernelAdapterImpl adapter;

    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        authService = new RecordingAuthService();
        auditEmitter = new RecordingAuditEmitter();
        consentPlugin = new EphemeralConsentPlugin();
        approvalPlugin = new EphemeralApprovalPlugin();
        auditTrailPlugin = new RecordingAuditTrailPlugin();
        riskPlugin = new EphemeralRiskPlugin();
        notificationPlugin = new EphemeralNotificationPlugin();
        featureFlagPlugin = new EphemeralFeatureFlagPlugin();

        adapter = new DigitalMarketingKernelAdapterImpl(
            authService,
            auditEmitter,
            new NoopHealthIndicator(),
            consentPlugin,
            approvalPlugin,
            auditTrailPlugin,
            riskPlugin,
            notificationPlugin,
            featureFlagPlugin,
            false
        );

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-wiring"))
            .workspaceId(DmWorkspaceId.of("ws-wiring"))
            .actor(ActorRef.user("actor-1"))
            .correlationId(DmCorrelationId.of("corr-wiring"))
            .idempotencyKey(DmIdempotencyKey.of("idk-wiring"))
            .build();
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bridge lifecycle: start enables operations, stop disables them")
    void shouldManageLifecycle() {
        assertThat(adapter.started()).isFalse();

        adapter.start();
        assertThat(adapter.started()).isTrue();

        adapter.stop();
        assertThat(adapter.started()).isFalse();
    }

    @Test
    @DisplayName("operations are rejected before start")
    void shouldRejectOperationsBeforeStart() {
        assertThatIllegalStateException()
            .isThrownBy(() -> adapter.isAuthorized(ctx, "campaigns/c-1", "launch"))
            .withMessageContaining("not started");
    }

    @Test
    @DisplayName("bridge survives stop+restart cycle")
    void shouldSupportRestartCycle() {
        adapter.start();
        adapter.stop();
        adapter.start();

        assertThat(adapter.started()).isTrue();
        authService.setDecision("campaigns/c-1", "create", true);
        boolean result = runPromise(() -> adapter.isAuthorized(ctx, "campaigns/c-1", "create"));
        assertThat(result).isTrue();
    }

    // ── Authorization ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("isAuthorized delegates to auth service and propagates bridge context")
    void shouldDelegateAuthorization() {
        adapter.start();
        authService.setDecision("campaigns/c-1", "launch", true);

        boolean result = runPromise(() -> adapter.isAuthorized(ctx, "campaigns/c-1", "launch"));

        assertThat(result).isTrue();
        assertThat(authService.lastContext().getTenantId()).isEqualTo("tenant-wiring");
        assertThat(authService.lastContext().getPrincipalId()).isEqualTo("actor-1");
        assertThat(authService.lastContext().getCorrelationId()).isEqualTo("corr-wiring");
    }

    @Test
    @DisplayName("isAuthorized returns false when auth service denies")
    void shouldDenyAuthorization() {
        adapter.start();
        authService.setDecision("campaigns/c-2", "delete", false);

        boolean result = runPromise(() -> adapter.isAuthorized(ctx, "campaigns/c-2", "delete"));

        assertThat(result).isFalse();
    }

    // ── Consent ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyConsent returns true when consent is granted")
    void shouldVerifyGrantedConsent() {
        adapter.start();
        consentPlugin.setConsent("contact-1", "marketing-email", true);

        boolean result = runPromise(() -> adapter.verifyConsent(ctx, "contact-1", "marketing-email"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifyConsent returns false when consent is absent")
    void shouldReturnFalseForMissingConsent() {
        adapter.start();

        boolean result = runPromise(() -> adapter.verifyConsent(ctx, "contact-99", "marketing-email"));

        assertThat(result).isFalse();
    }

    // ── Approval ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("requestApproval returns a non-blank request id")
    void shouldRequestApproval() {
        adapter.start();

        String requestId = runPromise(
            () -> adapter.requestApproval(ctx, "LaunchCampaign", "campaign-1", "Launch Q4 campaign")
        );

        assertThat(requestId).isNotBlank();
        assertThat(approvalPlugin.lastRequest().subjectId()).isEqualTo("campaign-1");
        assertThat(approvalPlugin.lastRequest().requestedBy()).isEqualTo("actor-1");
    }

    // ── Audit ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordAudit enriches attributes with tenant, actor, and correlation id")
    void shouldEnrichAuditAttributes() {
        adapter.start();

        String entryId = runPromise(
            () -> adapter.recordAudit(ctx, "campaign-1", "launch", Map.of("channel", "email"))
        );

        assertThat(entryId).isNotBlank();
        assertThat(auditTrailPlugin.lastDetails())
            .containsEntry("tenantId",     "tenant-wiring")
            .containsEntry("workspaceId",  "ws-wiring")
            .containsEntry("correlationId","corr-wiring")
            .containsEntry("actor",        "actor-1")
            .containsEntry("channel",      "email");
    }

    // ── Notifications (KE-02) ───────────────────────────────────────────────────

    @Test
    @DisplayName("notifyUser completes without exception (KE-02 forward stub)")
    void shouldCompleteNotificationWithoutException() {
        adapter.start();

        Void result = runPromise(() -> adapter.notifyUser(
            ctx,
            "actor-1",
            "dmos.campaign.launched",
            Map.of("campaignId", "c-1", "campaignName", "Spring Sale")
        ));

        // Notification is log-only at this stage; Void result must resolve to null.
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("notifyUser rejects call before start")
    void shouldRejectNotificationBeforeStart() {
        assertThatIllegalStateException()
            .isThrownBy(() -> adapter.notifyUser(
                ctx,
                "actor-1",
                "dmos.campaign.launched",
                Map.of()
            ))
            .withMessageContaining("not started");
    }

    // ── Feature Flags (KE-05) ───────────────────────────────────────────────────

    @Test
    @DisplayName("isFeatureEnabled returns false by default (fail-closed, KE-05)")
    void shouldReturnFalseForAllFlagsByDefault() {
        adapter.start();

        boolean enabled = runPromise(() -> adapter.isFeatureEnabled(ctx, "dmos.multivariate-testing"));

        assertThat(enabled).isFalse();
    }

    // ── Risk ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluateRisk returns score from risk plugin")
    void shouldEvaluateRisk() {
        adapter.start();
        riskPlugin.setScore(0.35d);

        double score = runPromise(() -> adapter.evaluateRisk(
            ctx,
            "campaign-1",
            "DM_CAMPAIGN_LAUNCH",
            Map.of("budget", 5000)
        ));

        assertThat(score).isEqualTo(0.35d);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static final class RecordingAuthService implements BridgeAuthorizationService {
        private final Map<String, Boolean> decisions = new ConcurrentHashMap<>();
        private volatile BridgeContext lastContext;

        void setDecision(String resource, String action, boolean allowed) {
            decisions.put(resource + "|" + action, allowed);
        }

        BridgeContext lastContext() {
            return lastContext;
        }

        @Override
        public Promise<Boolean> isAuthorized(BridgeContext context, String resource, String action) {
            lastContext = context;
            return Promise.of(decisions.getOrDefault(resource + "|" + action, true));
        }
    }

    private static final class RecordingAuditEmitter implements BridgeAuditEmitter {
        private final List<BridgeAuditEvent> events = new ArrayList<>();

        @Override
        public void emit(BridgeAuditEvent event) {
            events.add(event);
        }
    }

    private static final class NoopHealthIndicator implements BridgeHealthIndicator {
        @Override
        public void reportHealthy(String bridgeId) { }

        @Override
        public void reportDegraded(String bridgeId, String reason) { }

        @Override
        public void reportUnhealthy(String bridgeId, String reason) { }
    }

    private static final class EphemeralFeatureFlagPlugin implements FeatureFlagPlugin {
        private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

        @Override
        public Promise<Boolean> isEnabled(String flagKey, String tenantId) {
            return Promise.of(flags.getOrDefault(tenantId + "|" + flagKey, Boolean.FALSE));
        }

        @Override
        public Promise<String> getString(String flagKey, String tenantId, String defaultValue) {
            return Promise.of(defaultValue);
        }

        @Override
        public Promise<Integer> getInt(String flagKey, String tenantId, int defaultValue) {
            return Promise.of(defaultValue);
        }

        @Override
        public Promise<Boolean> getBoolean(String flagKey, String tenantId, boolean defaultValue) {
            return Promise.of(flags.getOrDefault(tenantId + "|" + flagKey, defaultValue));
        }

        @Override
        public Promise<Map<String, Object>> getAllFlags(String tenantId) {
            return Promise.of(Map.of());
        }
    }

    private static final class EphemeralConsentPlugin implements ConsentPlugin {
        private final Map<String, Boolean> consents = new ConcurrentHashMap<>();

        void setConsent(String subjectId, String purpose, boolean granted) {
            consents.put(subjectId + "|" + purpose, granted);
        }

        @Override
        public Promise<ConsentRecord> recordConsent(String subjectId, String purpose, ConsentAction action) {
            return Promise.of(new ConsentRecord(
                "c-1", subjectId, purpose,
                action == ConsentAction.GRANT ? ConsentStatus.GRANTED : ConsentStatus.DENIED,
                action, "consent", Instant.now(), null, null, "{}"
            ));
        }

        @Override
        public Promise<Boolean> verifyConsent(String subjectId, String purpose) {
            return Promise.of(consents.getOrDefault(subjectId + "|" + purpose, false));
        }

        @Override public Promise<Void> revokeConsent(String id) { return Promise.of(null); }
        @Override public Promise<List<ConsentRecord>> getConsentHistory(String id) { return Promise.of(List.of()); }
        @Override public Promise<ConsentStatus> getCurrentConsent(String subjectId, String purpose) {
            boolean granted = consents.getOrDefault(subjectId + "|" + purpose, false);
            return Promise.of(granted ? ConsentStatus.GRANTED : ConsentStatus.NOT_REQUESTED);
        }
        @Override public Promise<Integer> deleteAllForSubject(String id) { return Promise.of(0); }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder().id("wiring-it-consent").name("Wiring IT Consent").type(PluginType.CUSTOM).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext ctx) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class EphemeralApprovalPlugin implements HumanApprovalPlugin {
        private volatile ApprovalRequest lastRequest;

        ApprovalRequest lastRequest() { return lastRequest; }

        @Override
        public Promise<ApprovalRecord> requestApproval(ApprovalRequest request) {
            lastRequest = request;
            return Promise.of(new ApprovalRecord(
                request.requestId(), request.subjectId(), request.requestedBy(), request.action(),
                ApprovalStatus.PENDING, request.requestedAt(), request.expiresAt(), null, null, null
            ));
        }

        @Override public Promise<Optional<ApprovalRecord>> getApprovalStatus(String id) { return Promise.of(Optional.empty()); }
        @Override
        public Promise<ApprovalRecord> completeApproval(String id, ApprovalDecision decision, String reviewerId, String notes) {
            return Promise.of(new ApprovalRecord(
                id, "subject", "requester", "action",
                decision == ApprovalDecision.APPROVED ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED,
                Instant.now(), null, Instant.now(), reviewerId, notes
            ));
        }
        @Override public Promise<List<ApprovalRecord>> listPendingForSubject(String id) { return Promise.of(List.of()); }
        @Override public Promise<Void> cancelApproval(String id, String reason) { return Promise.of(null); }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder().id("wiring-it-approval").name("Wiring IT Approval").type(PluginType.CUSTOM).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext ctx) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class RecordingAuditTrailPlugin implements AuditTrailPlugin {
        private volatile Map<String, Object> lastDetails = Map.of();

        Map<String, Object> lastDetails() { return lastDetails; }

        @Override
        public Promise<AuditEntry> logEvent(String entityId, String action, Map<String, Object> details) {
            lastDetails = Map.copyOf(details);
            return Promise.of(new AuditEntry(
                "audit-wiring-1", entityId, action, lastDetails, "actor-1", "hash", null, Instant.now()
            ));
        }

        @Override public Promise<List<AuditEntry>> getTrail(String entityId) { return Promise.of(List.of()); }
        @Override public Promise<VerificationResult> verifyIntegrity(String entityId) {
            return Promise.of(new VerificationResult(entityId, true, 0, List.of(), Instant.now()));
        }
        @Override public Promise<Void> exportTrail(String entityId, ExportFormat format, OutputStream out) { return Promise.of(null); }
        @Override public Promise<Integer> purgeEntriesOlderThan(long retentionDays) { return Promise.of(0); }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder().id("wiring-it-audit").name("Wiring IT Audit").type(PluginType.CUSTOM).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext ctx) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class EphemeralRiskPlugin implements RiskManagementPlugin {
        private volatile double score = 0.1d;

        void setScore(double score) { this.score = score; }

        @Override
        public Promise<RiskScore> calculateRisk(String entityId, RiskModelId modelId, Map<String, Object> factors) {
            return Promise.of(new RiskScore(
                entityId, modelId, score, RiskScore.RiskLevel.LOW, Map.of("composite", score), Instant.now()
            ));
        }

        @Override public Promise<Void> setRiskLimits(String entityId, Map<String, java.math.BigDecimal> limits) { return Promise.of(null); }
        @Override public Promise<List<RiskAlert>> getActiveAlerts(String entityId) { return Promise.of(List.of()); }
        @Override public Promise<RiskReport> generateReport(String entityId, TimeRange range) {
            return Promise.of(new RiskReport(entityId, range, List.of(), List.of(), Map.of(), Instant.now()));
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder().id("wiring-it-risk").name("Wiring IT Risk").type(PluginType.CUSTOM).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext ctx) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }

    private static final class EphemeralNotificationPlugin implements NotificationPlugin {
        @Override
        public Promise<String> dispatch(String recipientId, String template, Map<String, String> attributes) {
            return Promise.of("notif-" + recipientId);
        }

        @Override
        public Promise<DeliveryStatus> getDeliveryStatus(String notificationId) {
            return Promise.of(new DeliveryStatus(
                notificationId, "recipient", "template",
                DeliveryState.DELIVERED, 1, Instant.now(), null, Instant.now()
            ));
        }

        @Override public Promise<Void> retry(String notificationId) { return Promise.of(null); }
        @Override public Promise<List<DeadLetterEntry>> listDeadLetterQueue(int limit, int offset) { return Promise.of(List.of()); }
        @Override public Promise<Void> reprocessDeadLetter(String notificationId) { return Promise.of(null); }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder().id("wiring-it-notification").name("Wiring IT Notification").type(PluginType.CUSTOM).build();
        }
        @Override public PluginState getState() { return PluginState.RUNNING; }
        @Override public Promise<Void> initialize(PluginContext ctx) { return Promise.of(null); }
        @Override public Promise<Void> start() { return Promise.of(null); }
        @Override public Promise<Void> stop() { return Promise.of(null); }
    }
}
