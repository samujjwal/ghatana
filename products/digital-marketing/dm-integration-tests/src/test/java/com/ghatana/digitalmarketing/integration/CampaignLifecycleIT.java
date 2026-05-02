package com.ghatana.digitalmarketing.integration;

import com.ghatana.digitalmarketing.application.campaign.CampaignComplianceViolationException;
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
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for the DMOS Campaign lifecycle using in-memory fakes.
 */
@DisplayName("Campaign Lifecycle Integration")
class CampaignLifecycleIT extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryCompliancePlugin compliancePlugin;
    private CampaignService campaignService;
    private DmOperationContext writeCtx;
    private DmOperationContext readCtx;

    @BeforeEach
    void setUp() {
        CampaignRepository inMemoryRepo = new InMemoryCampaignRepository();
        kernelAdapter = new RecordingKernelAdapter();
        compliancePlugin = new InMemoryCompliancePlugin();
        campaignService = new CampaignServiceImpl(
            kernelAdapter,
            inMemoryRepo,
            compliancePlugin,
            (opCtx, campaign) -> Promise.of(new com.ghatana.digitalmarketing.application.campaign.CampaignPreflightDataProvider.CampaignPreflightData(
                true,
                1,
                1,
                0.0,
                1000.0
            ))
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
        compliancePlugin.setCompliant(true);
    }

    @Test
    @DisplayName("full lifecycle: create, launch, pause, resume, and get")
    void shouldExecuteFullLifecycle() {
        Campaign created = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Q4 Acquisition", CampaignType.EMAIL)));
        assertThat(created.getStatus()).isEqualTo(CampaignStatus.DRAFT);

        Campaign launched = runPromise(() -> campaignService.launchCampaign(writeCtx, created.getId()));
        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);

        Campaign paused = runPromise(() -> campaignService.pauseCampaign(writeCtx, created.getId()));
        assertThat(paused.getStatus()).isEqualTo(CampaignStatus.PAUSED);

        Campaign resumed = runPromise(() -> campaignService.launchCampaign(writeCtx, created.getId()));
        assertThat(resumed.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);

        Campaign fetched = runPromise(() -> campaignService.getCampaign(readCtx, created.getId()));
        assertThat(fetched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
    }

    @Test
    @DisplayName("create is denied when not authorized")
    void shouldDenyCreateWhenNotAuthorized() {
        kernelAdapter.setAuthorization("campaigns/*", "write", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.createCampaign(writeCtx,
                new CampaignService.CreateCampaignCommand("Blocked", CampaignType.SOCIAL))));
    }

    @Test
    @DisplayName("launch is blocked when compliance preflight fails")
    void shouldBlockLaunchOnComplianceFailure() {
        Campaign created = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Non-Compliant", CampaignType.EMAIL)));
        compliancePlugin.setCompliant(false);

        assertThatExceptionOfType(CampaignComplianceViolationException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.launchCampaign(writeCtx, created.getId())));
    }

    @Test
    @DisplayName("create and launch each emit distinct audit actions")
    void shouldRecordAuditForCreateAndLaunch() {
        Campaign created = runPromise(() -> campaignService.createCampaign(writeCtx,
            new CampaignService.CreateCampaignCommand("Audited", CampaignType.EMAIL)));
        runPromise(() -> campaignService.launchCampaign(writeCtx, created.getId()));

        assertThat(kernelAdapter.auditActions())
            .contains("create", "launch");
    }

    @Test
    @DisplayName("campaigns are isolated by workspace")
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

        Campaign inA = runPromise(() -> campaignService.createCampaign(wsACtx,
            new CampaignService.CreateCampaignCommand("Campaign A", CampaignType.EMAIL)));

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> campaignService.getCampaign(wsBReadCtx, inA.getId())));
    }

    private static final class InMemoryCampaignRepository implements CampaignRepository {
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
        private final List<String> auditActions = new java.util.concurrent.CopyOnWriteArrayList<>();

        void setDefaultAuthorization(boolean allowed) {
            defaultAuthorization = allowed;
        }

        void setAuthorization(String resource, String action, boolean allowed) {
            decisionMap.put(resource + "|" + action, allowed);
        }

        List<String> auditActions() {
            return auditActions;
        }

        @Override
        public void start() {
            // no-op for integration tests
        }

        @Override
        public void stop() {
            // no-op for integration tests
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(decisionMap.getOrDefault(resource + "|" + action, defaultAuthorization));
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
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

    private static final class InMemoryCompliancePlugin implements CompliancePlugin {
        private volatile boolean compliant = true;

        void setCompliant(boolean value) {
            compliant = value;
        }

        @Override
        public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context) {
            if (compliant) {
                return Promise.of(new ComplianceResult(true, List.of(), ruleSetId, Instant.now()));
            }
            return Promise.of(new ComplianceResult(
                false,
                List.of(new ComplianceViolation(
                    "DM-CP-001",
                    "Preflight failed",
                    ComplianceRule.Severity.HIGH,
                    Map.of("reason", "simulated")
                )),
                ruleSetId,
                Instant.now()
            ));
        }

        @Override
        public Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> addRule(String ruleSetId, ComplianceRule rule) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<AuditEntry>> getAuditTrail(String entityId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<ComplianceViolation>> getActiveViolations(String ruleSetId) {
            return Promise.of(List.of());
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("dm-it-compliance-plugin")
                .name("DM IT Compliance Plugin")
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
