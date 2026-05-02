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
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end campaign happy-path test for the current DMOS MVP slice.
 */
@DisplayName("Campaign E2E")
class CampaignE2EIT extends EventloopTestBase {

    @Test
    @DisplayName("draft to launch to pause flow completes with persisted state")
    void shouldCompleteCampaignHappyPath() {
        CampaignService service = new CampaignServiceImpl(
            new AllowAllKernelAdapter(),
            new InMemoryCampaignRepository(),
            new AlwaysCompliantPlugin(),
            (opCtx, campaign) -> Promise.of(new com.ghatana.digitalmarketing.application.campaign.CampaignPreflightDataProvider.CampaignPreflightData(
                true,
                1,
                1,
                0.0,
                1000.0
            ))
        );

        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-e2e"))
            .workspaceId(DmWorkspaceId.of("ws-e2e"))
            .actor(ActorRef.user("e2e-user"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        Campaign created = runPromise(() -> service.createCampaign(
            ctx,
            new CampaignService.CreateCampaignCommand("E2E Campaign", CampaignType.SOCIAL)
        ));
        Campaign launched = runPromise(() -> service.launchCampaign(ctx, created.getId()));
        Campaign paused = runPromise(() -> service.pauseCampaign(ctx, created.getId()));

        assertThat(created.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
        assertThat(paused.getStatus()).isEqualTo(CampaignStatus.PAUSED);
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
            return Promise.of("approval-e2e");
        }

        @Override
        public Promise<String> recordAudit(
            DmOperationContext context,
            String entityId,
            String action,
            Map<String, Object> attributes
        ) {
            return Promise.of("audit-e2e");
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
                .id("dm-e2e-compliance-plugin")
                .name("DM E2E Compliance Plugin")
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
