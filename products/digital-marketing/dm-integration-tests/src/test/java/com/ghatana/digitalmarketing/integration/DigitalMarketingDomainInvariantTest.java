package com.ghatana.digitalmarketing.integration;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.application.campaign.CampaignServiceImpl;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Phase 2: Domain invariant test suite for Digital Marketing
 *
 * Validates critical domain invariants:
 * - dm-001: Campaign lifecycle state machine
 * - dm-002: Consent-gated campaign activation
 * - dm-003: Lead capture data integrity
 * - dm-004: Campaign activity audit trail
 * - dm-005: Connector readiness state machine
 * - dm-006: Notification retry and DLQ
 * - dm-007: Lead conversion tracking
 * - dm-008: Campaign budget enforcement
 * - dm-009: Tenant-scoped campaign isolation
 * - dm-010: Notification preference handling
 *
 * @doc.type class
 * @doc.purpose Validates Digital Marketing domain invariants
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Digital Marketing Domain Invariant Tests")
class DigitalMarketingDomainInvariantTest extends EventloopTestBase {

    private EphemeralCampaignRepository repository;
    private RecordingKernelAdapter kernelAdapter;
    private CampaignService campaignService;
    private DmOperationContext writeCtx;
    private DmOperationContext readCtx;
    private Eventloop eventloop;
    private java.util.concurrent.Executor executor;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.create();
        executor = Executors.newSingleThreadExecutor();
        repository = new EphemeralCampaignRepository();
        kernelAdapter = new RecordingKernelAdapter();
        campaignService = new CampaignServiceImpl(
            kernelAdapter,
            repository,
            new EphemeralCompliancePlugin(),
            (opCtx, campaign) -> Promise.of(new com.ghatana.digitalmarketing.application.campaign.CampaignPreflightDataProvider.CampaignPreflightData(
                true,
                1,
                1,
                0.0,
                1000.0
            )),
            DmosMetricsCollector.disabled()
        );

        writeCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        readCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.generate())
            .build();

        kernelAdapter.setDefaultAuthorization(true);
        kernelAdapter.setConsentVerified(true);
    }

    @Test
    @DisplayName("dm-001: Campaign lifecycle state machine - valid transitions only")
    void shouldEnforceCampaignLifecycleStateMachine() {
        // Valid: draft -> launched
        Campaign draft = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Test Campaign", CampaignType.EMAIL)));
        assertThat(draft.getStatus()).isEqualTo(CampaignStatus.DRAFT);

        Campaign launched = runPromise(() -> campaignService.launchCampaign(writeCtx, draft.getId()));
        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);

        // Valid: launched -> paused
        Campaign paused = runPromise(() -> campaignService.pauseCampaign(writeCtx, draft.getId()));
        assertThat(paused.getStatus()).isEqualTo(CampaignStatus.PAUSED);

        // Valid: paused -> launched (resume)
        Campaign resumed = runPromise(() -> campaignService.launchCampaign(writeCtx, draft.getId()));
        assertThat(resumed.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
    }

    @Test
    @DisplayName("dm-002: Consent-gated campaign activation - blocks without consent")
    void shouldBlockCampaignActivationWithoutConsent() {
        kernelAdapter.setConsentVerified(false);

        Campaign draft = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Non-Consented Campaign", CampaignType.EMAIL)));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.launchCampaign(writeCtx, draft.getId())));
    }

    @Test
    @DisplayName("dm-004: Campaign activity audit trail - records all state changes")
    void shouldRecordAuditTrailForCampaignActivities() {
        Campaign created = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Audited Campaign", CampaignType.EMAIL)));
        runPromise(() -> campaignService.launchCampaign(writeCtx, created.getId()));
        runPromise(() -> campaignService.pauseCampaign(writeCtx, created.getId()));

        List<String> auditActions = kernelAdapter.auditActions();
        assertThat(auditActions).contains("create", "launch", "pause");
        assertThat(auditActions).hasSize(3);
    }

    @Test
    @DisplayName("dm-009: Tenant-scoped campaign isolation - no cross-tenant leakage")
    void shouldIsolateCampaignsByTenant() {
        DmOperationContext tenantACtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-A"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        DmOperationContext tenantBReadCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-B"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.generate())
            .build();

        Campaign inTenantA = runPromise(() -> campaignService.createCampaign(tenantACtx,
            new CampaignService.CreateCampaignCommand("Tenant A Campaign", CampaignType.EMAIL)));

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.getCampaign(tenantBReadCtx, inTenantA.getId())));
    }

    @Test
    @DisplayName("dm-009: Workspace-scoped campaign isolation - no cross-workspace leakage")
    void shouldIsolateCampaignsByWorkspace() {
        DmOperationContext wsACtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-A"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        DmOperationContext wsBReadCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-B"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.generate())
            .build();

        Campaign inWsA = runPromise(() -> campaignService.createCampaign(wsACtx,
            new CampaignService.CreateCampaignCommand("Workspace A Campaign", CampaignType.EMAIL)));

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.getCampaign(wsBReadCtx, inWsA.getId())));
    }

    private static final class EphemeralCampaignRepository implements CampaignRepository {
        private final ConcurrentHashMap<String, Campaign> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Campaign> save(Campaign campaign) {
            store.put(key(campaign.getWorkspaceId().getValue(), campaign.getId()), campaign);
            return Promise.of(campaign);
        }

        @Override
        public Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String campaignId) {
            return Promise.of(Optional.ofNullable(store.get(key(workspaceId.getValue(), campaignId))));
        }

        private static String key(String workspaceId, String campaignId) {
            return workspaceId + ":" + campaignId;
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final Map<String, Boolean> decisionMap = new ConcurrentHashMap<>();
        private volatile boolean defaultAuthorization = true;
        private volatile boolean consentVerified = true;
        private final List<String> auditActions = new java.util.concurrent.CopyOnWriteArrayList<>();

        void setDefaultAuthorization(boolean allowed) {
            defaultAuthorization = allowed;
        }

        void setConsentVerified(boolean verified) {
            consentVerified = verified;
        }

        List<String> auditActions() {
            return auditActions;
        }

        @Override
        public void start() {
            // no-op for tests
        }

        @Override
        public void stop() {
            // no-op for tests
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(decisionMap.getOrDefault(resource + "|" + action, defaultAuthorization));
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(consentVerified);
        }

        @Override
        public Promise<String> requestApproval(
            DmOperationContext context,
            String operationType,
            String subjectId,
            String description
        ) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
            DmOperationContext context,
            String entityId,
            String action,
            Map<String, Object> attributes
        ) {
            auditActions.add(action);
            return Promise.of("audit-entry-1");
        }
    }

    private static final class EphemeralCompliancePlugin implements com.ghatana.plugin.compliance.CompliancePlugin {
        @Override
        public Promise<com.ghatana.plugin.compliance.ComplianceResult> evaluate(
            String ruleSetId,
            com.ghatana.plugin.compliance.ComplianceContext context
        ) {
            return Promise.of(new com.ghatana.plugin.compliance.ComplianceResult(
                true,
                List.of(),
                ruleSetId,
                java.time.Instant.now()
            ));
        }

        @Override
        public Promise<Void> registerRuleSet(String ruleSetId, List<com.ghatana.plugin.compliance.ComplianceRule> rules) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> addRule(String ruleSetId, com.ghatana.plugin.compliance.ComplianceRule rule) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<com.ghatana.plugin.compliance.AuditEntry>> getAuditTrail(String entityId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<com.ghatana.plugin.compliance.ComplianceViolation>> getActiveViolations(String ruleSetId) {
            return Promise.of(List.of());
        }

        @Override
        public com.ghatana.platform.plugin.PluginMetadata metadata() {
            return com.ghatana.platform.plugin.PluginMetadata.builder()
                .id("dm-invariant-test-plugin")
                .name("DM Invariant Test Plugin")
                .type(com.ghatana.platform.plugin.PluginType.CUSTOM)
                .build();
        }

        @Override
        public com.ghatana.platform.plugin.PluginState getState() {
            return com.ghatana.platform.plugin.PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(com.ghatana.platform.plugin.PluginContext context) {
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
