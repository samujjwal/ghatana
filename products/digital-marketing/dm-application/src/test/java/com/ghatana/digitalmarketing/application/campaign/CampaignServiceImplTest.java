package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("CampaignServiceImpl")
class CampaignServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private InMemoryCampaignRepository repository;
    private ToggleCompliancePlugin compliancePlugin;
    private CampaignServiceImpl service;
    private DmOperationContext ctx;

    private InMemoryKillSwitchService killSwitchService;
    private InMemoryCommandService commandService;
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new InMemoryCampaignRepository();
        compliancePlugin = new ToggleCompliancePlugin();
        killSwitchService = new InMemoryKillSwitchService();
        commandService = new InMemoryCommandService();
        objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        service = new CampaignServiceImpl(
            kernelAdapter,
            repository,
            compliancePlugin,
            (opCtx, campaign) -> Promise.of(new CampaignPreflightDataProvider.CampaignPreflightData(
                true,
                1,
                1,
                0.0,
                1000.0
            )),
            DmosMetricsCollector.noop(),
            killSwitchService,
            commandService,
            objectMapper
        );

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        kernelAdapter.setDefaultAuthorization(true);
        compliancePlugin.setCompliant(true);
    }

    @Test
    @DisplayName("constructor throws on null dependencies")
    void shouldRejectNullDependencies() {
        CampaignPreflightDataProvider preflightProvider = (opCtx, campaign) -> Promise.of(
            new CampaignPreflightDataProvider.CampaignPreflightData(true, 1, 1, 0.0, 1000.0)
        );

        assertThatNullPointerException()
            .isThrownBy(() -> new CampaignServiceImpl(null, repository, compliancePlugin, preflightProvider, DmosMetricsCollector.noop()));
        assertThatNullPointerException()
            .isThrownBy(() -> new CampaignServiceImpl(kernelAdapter, null, compliancePlugin, preflightProvider, DmosMetricsCollector.noop()));
        assertThatNullPointerException()
            .isThrownBy(() -> new CampaignServiceImpl(kernelAdapter, repository, null, preflightProvider, DmosMetricsCollector.noop()));
        assertThatNullPointerException()
            .isThrownBy(() -> new CampaignServiceImpl(kernelAdapter, repository, compliancePlugin, null, DmosMetricsCollector.noop()));
        assertThatNullPointerException()
            .isThrownBy(() -> new CampaignServiceImpl(kernelAdapter, repository, compliancePlugin, preflightProvider, null))
            .withMessageContaining("metrics");
    }

    @Test
    @DisplayName("createCampaign creates draft campaign and records audit")
    void shouldCreateCampaignInDraft() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("Q4 Acquisition", CampaignType.EMAIL)
        ));

        assertThat(created.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(created.getId()).isNotBlank();
        assertThat(kernelAdapter.auditActions()).contains("create");
    }

    @Test
    @DisplayName("createCampaign rejects when not authorized")
    void shouldDenyCreateWhenNotAuthorized() {
        kernelAdapter.setAuthorization("campaigns/*", "write", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.createCampaign(
                ctx,
                new CampaignService.CreateCampaignCommand("Denied", CampaignType.EMAIL)
            )));
    }

    @Test
    @DisplayName("launchCampaign transitions draft to launched")
    void shouldLaunchDraftCampaign() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("Launchable", CampaignType.EMAIL)
        ));

        Campaign launched = runPromise(() -> service.launchCampaign(ctx, created.getId()));

        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
        assertThat(kernelAdapter.auditActions()).contains("launch");
    }

    @Test
    @DisplayName("launchCampaign throws compliance violation when preflight fails")
    void shouldThrowOnComplianceFailure() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("Blocked", CampaignType.EMAIL)
        ));
        compliancePlugin.setCompliant(false);

        assertThatExceptionOfType(CampaignComplianceViolationException.class)
            .isThrownBy(() -> runPromise(() -> service.launchCampaign(ctx, created.getId())));
    }

    @Test
    @DisplayName("pauseCampaign transitions launched to paused")
    void shouldPauseLaunchedCampaign() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("Pausable", CampaignType.EMAIL)
        ));
        runPromise(() -> service.launchCampaign(ctx, created.getId()));

        Campaign paused = runPromise(() -> service.pauseCampaign(ctx, created.getId()));

        assertThat(paused.getStatus()).isEqualTo(CampaignStatus.PAUSED);
        assertThat(kernelAdapter.auditActions()).contains("pause");
    }

    @Test
    @DisplayName("getCampaign enforces authorization and returns existing campaign")
    void shouldReadCampaign() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("Readable", CampaignType.SOCIAL)
        ));

        Campaign found = runPromise(() -> service.getCampaign(ctx, created.getId()));
        assertThat(found.getId()).isEqualTo(created.getId());

        kernelAdapter.setAuthorization("campaigns/" + created.getId(), "read", false);
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getCampaign(ctx, created.getId())));
    }

    @Test
    @DisplayName("launchCampaign throws when campaign is missing")
    void shouldThrowWhenLaunchingMissingCampaign() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.launchCampaign(ctx, "missing")));
    }

    @Test
    @DisplayName("launchCampaign rejects when not authorized")
    void shouldDenyLaunchWhenNotAuthorized() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("AuthDenied", CampaignType.EMAIL)
        ));

        kernelAdapter.setAuthorization("campaigns/" + created.getId(), "launch", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.launchCampaign(ctx, created.getId())));
    }

    @Test
    @DisplayName("pauseCampaign rejects when not authorized")
    void shouldDenyPauseWhenNotAuthorized() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("PauseAuthDenied", CampaignType.EMAIL)
        ));
        runPromise(() -> service.launchCampaign(ctx, created.getId()));

        kernelAdapter.setAuthorization("campaigns/" + created.getId(), "pause", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.pauseCampaign(ctx, created.getId())));
    }

    @Test
    @DisplayName("pauseCampaign throws when campaign is missing")
    void shouldThrowWhenPausingMissingCampaign() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.pauseCampaign(ctx, "no-such-campaign")));
    }

    @Test
    @DisplayName("launchCampaign issues GOOGLE_ADS_CAMPAIGN_CREATE command for PAID_SEARCH campaigns")
    void shouldIssueGoogleAdsCommandForPaidSearchCampaign() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("PaidSearchCampaign", CampaignType.PAID_SEARCH)
        ));

        Campaign launched = runPromise(() -> service.launchCampaign(ctx, created.getId()));

        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);

        // Verify command was issued
        Long pendingCount = runPromise(() -> commandService.countByStatus(
            ctx, com.ghatana.digitalmarketing.domain.command.DmCommandStatus.PENDING));
        assertThat(pendingCount).isEqualTo(1L);

        // Verify command type and payload
        var commands = runPromise(() -> commandService.listPending(ctx, 10));
        assertThat(commands).hasSize(1);
        var cmd = commands.get(0);
        assertThat(cmd.getCommandType()).isEqualTo(
            com.ghatana.digitalmarketing.domain.command.DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE);
        assertThat(cmd.getSerializedPayload()).contains("internalCampaignId");
        assertThat(cmd.getSerializedPayload()).contains(created.getId());
    }

    @Test
    @DisplayName("launchCampaign does not issue Google Ads command for non-PAID_SEARCH campaigns")
    void shouldNotIssueGoogleAdsCommandForEmailCampaign() {
        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("EmailCampaign", CampaignType.EMAIL)
        ));

        Campaign launched = runPromise(() -> service.launchCampaign(ctx, created.getId()));

        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);

        // Verify no commands were issued
        Long pendingCount = runPromise(() -> commandService.countByStatus(
            ctx, com.ghatana.digitalmarketing.domain.command.DmCommandStatus.PENDING));
        assertThat(pendingCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("launchCampaign blocks Google Ads command when kill switch is active")
    void shouldBlockGoogleAdsCommandWhenKillSwitchActive() {
        // Activate kill switch for Google Ads publish
        runPromise(() -> killSwitchService.activateKillSwitch(
            "TENANT", ctx.getTenantId().getValue(),
            com.ghatana.digitalmarketing.application.governance.DmKillSwitchService.Features.GOOGLE_ADS_PUBLISH,
            "Test block", "test-user"));

        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("BlockedCampaign", CampaignType.PAID_SEARCH)
        ));

        Campaign launched = runPromise(() -> service.launchCampaign(ctx, created.getId()));

        // Campaign should still be launched internally
        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);

        // Verify no commands were issued due to kill switch
        Long pendingCount = runPromise(() -> commandService.countByStatus(
            ctx, com.ghatana.digitalmarketing.domain.command.DmCommandStatus.PENDING));
        assertThat(pendingCount).isEqualTo(0L);
    }

    private static final class InMemoryCampaignRepository implements CampaignRepository {
        private final ConcurrentHashMap<String, Campaign> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Campaign> save(Campaign campaign) {
            store.put(campaign.getWorkspaceId().getValue() + ":" + campaign.getId(), campaign);
            return Promise.of(campaign);
        }

        @Override
        public Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String campaignId) {
            return Promise.of(Optional.ofNullable(store.get(workspaceId.getValue() + ":" + campaignId)));
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
            // no-op
        }

        @Override
        public void stop() {
            // no-op
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
            return Promise.of("audit-1");
        }
    }

    private static final class ToggleCompliancePlugin implements CompliancePlugin {
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
                .id("dm-application-test-compliance-plugin")
                .name("DM Application Test Compliance Plugin")
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

    /**
     * In-memory kill switch service for testing.
     */
    private static class InMemoryKillSwitchService implements com.ghatana.digitalmarketing.application.governance.DmKillSwitchService {
        private final java.util.Set<String> activeSwitches = java.util.concurrent.ConcurrentHashMap.newKeySet();

        @Override
        public Promise<Boolean> isKillSwitchActive(String tenantId, String workspaceId, String feature) {
            String key = tenantId + ":" + workspaceId + ":" + feature;
            return Promise.of(activeSwitches.contains(key));
        }

        @Override
        public Promise<Void> activateKillSwitch(String scope, String scopeId, String feature, String reason, String activatedBy) {
            String key = scopeId + ":" + feature;
            activeSwitches.add(key);
            return Promise.of(null);
        }

        @Override
        public Promise<Void> deactivateKillSwitch(String scope, String scopeId, String feature, String deactivatedBy) {
            String key = scopeId + ":" + feature;
            activeSwitches.remove(key);
            return Promise.of(null);
        }

        @Override
        public Promise<Void> recordKillSwitchAudit(String tenantId, String workspaceId, String feature, boolean wasBlocked, String correlationId) {
            return Promise.of(null);
        }
    }

    /**
     * In-memory command service for testing.
     */
    private static class InMemoryCommandService implements com.ghatana.digitalmarketing.application.command.DmCommandService {
        private final java.util.List<com.ghatana.digitalmarketing.domain.command.DmCommand> commands = new java.util.ArrayList<>();

        @Override
        public Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> issue(
                DmOperationContext ctx, IssueCommandRequest request) {
            var command = com.ghatana.digitalmarketing.domain.command.DmCommand.builder()
                .id(java.util.UUID.randomUUID().toString())
                .commandType(request.commandType())
                .tenantId(ctx.getTenantId().getValue())
                .workspaceId(ctx.getWorkspaceId().getValue())
                .correlationId(ctx.getCorrelationId().getValue())
                .issuedBy(ctx.getActor().getPrincipalId())
                .serializedPayload(request.serializedPayload())
                .status(com.ghatana.digitalmarketing.domain.command.DmCommandStatus.PENDING)
                .attemptCount(0)
                .createdAt(java.time.Instant.now())
                .scheduledAt(java.time.Instant.now())
                .build();
            commands.add(command);
            return Promise.of(command);
        }

        @Override
        public Promise<java.util.Optional<com.ghatana.digitalmarketing.domain.command.DmCommand>> findById(DmOperationContext ctx, String id) {
            return Promise.of(commands.stream().filter(c -> c.getId().equals(id)).findFirst());
        }

        @Override
        public Promise<java.util.List<com.ghatana.digitalmarketing.domain.command.DmCommand>> listPending(DmOperationContext ctx, int limit) {
            return Promise.of(commands.stream()
                .filter(c -> c.getTenantId().equals(ctx.getTenantId().getValue())
                    && c.getStatus() == com.ghatana.digitalmarketing.domain.command.DmCommandStatus.PENDING)
                .limit(limit)
                .toList());
        }

        @Override
        public Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> markExecuting(DmOperationContext ctx, String commandId) {
            return findById(ctx, commandId)
                .then(opt -> opt.map(c -> {
                    var updated = c.markExecuting();
                    commands.remove(c);
                    commands.add(updated);
                    return Promise.of(updated);
                }).orElse(Promise.ofException(new NoSuchElementException("Command not found: " + commandId))));
        }

        @Override
        public Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> markSucceeded(DmOperationContext ctx, String commandId) {
            return findById(ctx, commandId)
                .then(opt -> opt.map(c -> {
                    var updated = c.markSucceeded();
                    commands.remove(c);
                    commands.add(updated);
                    return Promise.of(updated);
                }).orElse(Promise.ofException(new NoSuchElementException("Command not found: " + commandId))));
        }

        @Override
        public Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> markFailed(DmOperationContext ctx, String commandId, String failureReason) {
            return findById(ctx, commandId)
                .then(opt -> opt.map(c -> {
                    var updated = c.markFailed(failureReason);
                    commands.remove(c);
                    commands.add(updated);
                    return Promise.of(updated);
                }).orElse(Promise.ofException(new NoSuchElementException("Command not found: " + commandId))));
        }

        @Override
        public Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> markRolledBack(DmOperationContext ctx, String commandId) {
            return findById(ctx, commandId)
                .then(opt -> opt.map(c -> {
                    var updated = c.markRolledBack();
                    commands.remove(c);
                    commands.add(updated);
                    return Promise.of(updated);
                }).orElse(Promise.ofException(new NoSuchElementException("Command not found: " + commandId))));
        }

        @Override
        public Promise<Long> countByStatus(DmOperationContext ctx, com.ghatana.digitalmarketing.domain.command.DmCommandStatus status) {
            return Promise.of(commands.stream()
                .filter(c -> c.getTenantId().equals(ctx.getTenantId().getValue()) && c.getStatus() == status)
                .count());
        }
    }
}
