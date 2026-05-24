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
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Load and performance integration tests for the current campaign MVP slice.
 */
@DisplayName("Campaign Load and Performance")
class CampaignLoadPerfIT extends EventloopTestBase {

    private CampaignService campaignService;
    private EphemeralCampaignRepository campaignRepository;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        campaignRepository = new EphemeralCampaignRepository();
        campaignService = new CampaignServiceImpl(
            new AllowAllKernelAdapter(),
            campaignRepository,
            new AlwaysCompliantPlugin(),
            (opCtx, campaign) -> Promise.of(new com.ghatana.digitalmarketing.application.campaign.CampaignPreflightDataProvider.CampaignPreflightData(
                true,
                1,
                1,
                0.0,
                1000.0
            )),
            DmosMetricsCollector.disabled()
        );

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-load"))
            .workspaceId(DmWorkspaceId.of("ws-load"))
            .actor(ActorRef.user("load-user"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();
    }

    @Test
    @DisplayName("load smoke: can create and launch 100 campaigns")
    void shouldHandleLoadSmoke() {
        int campaignCount = 100;
        java.util.List<String> ids = new java.util.ArrayList<>();

        for (int i = 0; i < campaignCount; i++) {
            int idx = i;
            Campaign created = runPromise(() -> campaignService.createCampaign(
                ctx,
                new CampaignService.CreateCampaignCommand("Load-" + idx, CampaignType.EMAIL)
            ));
            ids.add(created.getId());
            runPromise(() -> campaignService.launchCampaign(ctx, created.getId()));
        }

        assertThat(campaignRepository.size()).isEqualTo(campaignCount);
        for (String id : ids) {
            Campaign fetched = runPromise(() -> campaignService.getCampaign(ctx, id));
            assertThat(fetched.getStatus().name()).isEqualTo("LAUNCHED");
        }
    }

    @Test
    @DisplayName("performance smoke: create and launch 50 campaigns within target budget")
    void shouldMeetPerformanceBudget() {
        int campaignCount = 50;
        Instant start = Instant.now();
        java.util.List<String> ids = new java.util.ArrayList<>();

        for (int i = 0; i < campaignCount; i++) {
            int idx = i;
            Campaign created = runPromise(() -> campaignService.createCampaign(
                ctx,
                new CampaignService.CreateCampaignCommand("Perf-" + idx, CampaignType.PAID_SEARCH)
            ));
            ids.add(created.getId());
            runPromise(() -> campaignService.launchCampaign(ctx, created.getId()));
        }

        Duration elapsed = Duration.between(start, Instant.now());
        assertThat(elapsed.toMillis()).isLessThan(5000L);
        for (String id : ids) {
            Campaign fetched = runPromise(() -> campaignService.getCampaign(ctx, id));
            assertThat(fetched.getStatus().name()).isEqualTo("LAUNCHED");
        }
    }

    private static final class EphemeralCampaignRepository implements CampaignRepository {
        private final ConcurrentHashMap<String, Campaign> store = new ConcurrentHashMap<>();

        int size() {
            return store.size();
        }

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

    private static final class AllowAllKernelAdapter implements DigitalMarketingKernelAdapter {
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
            return Promise.of(true);
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
            return Promise.of("approval-load");
        }

        @Override
        public Promise<String> recordAudit(
            DmOperationContext context,
            String entityId,
            String action,
            Map<String, Object> attributes
        ) {
            return Promise.of("audit-load");
        }
    }

    private static final class AlwaysCompliantPlugin implements CompliancePlugin {
        @Override
        public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context) {
            return Promise.of(new ComplianceResult(true, List.of(), ruleSetId, Instant.now()));
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
                .id("dm-load-perf-compliance-plugin")
                .name("DM Load Perf Compliance Plugin")
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
