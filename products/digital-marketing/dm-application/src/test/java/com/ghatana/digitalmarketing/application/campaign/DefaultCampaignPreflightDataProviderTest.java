package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.application.audience.AudienceRepository;
import com.ghatana.digitalmarketing.application.budget.BudgetRepository;
import com.ghatana.digitalmarketing.application.content.ContentAssetRepository;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audience.Audience;
import com.ghatana.digitalmarketing.domain.budget.Budget;
import com.ghatana.digitalmarketing.domain.budget.BudgetStatus;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DefaultCampaignPreflightDataProvider")
class DefaultCampaignPreflightDataProviderTest extends EventloopTestBase {

    private EphemeralBudgetRepository budgetRepo;
    private EphemeralAudienceRepository audienceRepo;
    private EphemeralContentAssetRepository contentRepo;
    private StubConsentInteractionBroker consentBroker;
    private DefaultCampaignPreflightDataProvider provider;

    private DmOperationContext ctx;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        budgetRepo   = new EphemeralBudgetRepository();
        audienceRepo = new EphemeralAudienceRepository();
        contentRepo  = new EphemeralContentAssetRepository();
        consentBroker = new StubConsentInteractionBroker();
        provider     = new DefaultCampaignPreflightDataProvider(budgetRepo, audienceRepo, contentRepo, consentBroker);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        campaign = Campaign.builder()
            .id("camp-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("Test Campaign")
            .status(CampaignStatus.DRAFT)
            .type(CampaignType.EMAIL)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-42")
            .build();
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultCampaignPreflightDataProvider(null, audienceRepo, contentRepo, consentBroker));
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultCampaignPreflightDataProvider(budgetRepo, null, contentRepo, consentBroker));
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultCampaignPreflightDataProvider(budgetRepo, audienceRepo, null, consentBroker));
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultCampaignPreflightDataProvider(budgetRepo, audienceRepo, contentRepo, null));
    }

    @Test
    @DisplayName("resolve rejects null ctx or campaign")
    void shouldRejectNullArguments() {
        assertThatNullPointerException()
            .isThrownBy(() -> runPromise(() -> provider.resolve(null, campaign)));
        assertThatNullPointerException()
            .isThrownBy(() -> runPromise(() -> provider.resolve(ctx, null)));
    }

    @Test
    @DisplayName("returns not-approved when no budget exists")
    void shouldReturnNotApprovedWhenNoBudget() {
        // no budget set in in-memory repository — default is empty
        CampaignPreflightDataProvider.CampaignPreflightData data =
            runPromise(() -> provider.resolve(ctx, campaign));

        assertThat(data.budgetApproved()).isFalse();
        assertThat(data.approvedBudget()).isEqualTo(0.0);
        assertThat(data.totalSpend()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("returns approved and correct amounts when approved budget exists")
    void shouldReturnApprovedWhenBudgetPresent() {
        Budget budget = Budget.builder()
            .id("bud-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .campaignId("camp-1")
            .allocatedAmount(10000.0)
            .spentAmount(500.0)
            .currency("USD")
            .status(BudgetStatus.APPROVED)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-42")
            .build();
        budgetRepo.setApprovedBudget(budget);

        CampaignPreflightDataProvider.CampaignPreflightData data =
            runPromise(() -> provider.resolve(ctx, campaign));

        assertThat(data.budgetApproved()).isTrue();
        assertThat(data.approvedBudget()).isEqualTo(10000.0);
        assertThat(data.totalSpend()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("returns zero audience count when no audience assigned")
    void shouldReturnZeroAudienceWhenNoneAssigned() {
        CampaignPreflightDataProvider.CampaignPreflightData data =
            runPromise(() -> provider.resolve(ctx, campaign));

        assertThat(data.targetAudienceCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("returns audience size when audience is assigned")
    void shouldReturnAudienceSizeWhenAssigned() {
        Audience audience = Audience.builder()
            .id("aud-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("Segment A")
            .contactIds(List.of("c1", "c2", "c3"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-42")
            .build();
        audienceRepo.setAudience(audience);

        CampaignPreflightDataProvider.CampaignPreflightData data =
            runPromise(() -> provider.resolve(ctx, campaign));

        assertThat(data.targetAudienceCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("returns correct approved content count")
    void shouldReturnApprovedContentCount() {
        contentRepo.setApprovedCount(5);

        CampaignPreflightDataProvider.CampaignPreflightData data =
            runPromise(() -> provider.resolve(ctx, campaign));

        assertThat(data.approvedContentCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("fully resolved preflight data combines all sources correctly")
    void shouldCombineAllPreflightSources() {
        Budget budget = Budget.builder()
            .id("bud-2")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .campaignId("camp-1")
            .allocatedAmount(5000.0)
            .spentAmount(200.0)
            .currency("EUR")
            .status(BudgetStatus.APPROVED)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-42")
            .build();
        budgetRepo.setApprovedBudget(budget);

        Audience audience = Audience.builder()
            .id("aud-2")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("Segment B")
            .contactIds(List.of("c1", "c2"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-42")
            .build();
        audienceRepo.setAudience(audience);
        contentRepo.setApprovedCount(3);

        CampaignPreflightDataProvider.CampaignPreflightData data =
            runPromise(() -> provider.resolve(ctx, campaign));

        assertThat(data.budgetApproved()).isTrue();
        assertThat(data.approvedBudget()).isEqualTo(5000.0);
        assertThat(data.totalSpend()).isEqualTo(200.0);
        assertThat(data.targetAudienceCount()).isEqualTo(2);
        assertThat(data.approvedContentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("campaign activation preflight is blocked when PHR consent is denied")
    void shouldSurfaceDeniedPhrConsentForCampaignActivation() {
        consentBroker.setConsentGranted(false);

        CampaignPreflightDataProvider.CampaignPreflightData data =
            runPromise(() -> provider.resolve(ctx, campaign));

        assertThat(data.consentGranted()).isFalse();
        assertThat(data.consentPurpose()).isEqualTo("campaign-activation");
    }

    // -----------------------------------------------------------------------
    // Deterministic in-memory repository fixtures
    // -----------------------------------------------------------------------

    private static final class EphemeralBudgetRepository implements BudgetRepository {
        private Budget approvedBudget;

        void setApprovedBudget(Budget budget) {
            this.approvedBudget = budget;
        }

        @Override
        public Promise<Budget> save(Budget budget) {
            return Promise.of(budget);
        }

        @Override
        public Promise<Optional<Budget>> findById(DmWorkspaceId workspaceId, String budgetId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<Optional<Budget>> findApprovedByCampaign(DmWorkspaceId workspaceId, String campaignId) {
            return Promise.of(Optional.ofNullable(approvedBudget));
        }

        @Override
        public Promise<List<Budget>> listByWorkspace(DmWorkspaceId workspaceId) {
            return Promise.of(List.of());
        }
    }

    private static final class EphemeralAudienceRepository implements AudienceRepository {
        private Audience audience;

        void setAudience(Audience audience) {
            this.audience = audience;
        }

        @Override
        public Promise<Audience> save(Audience audience) {
            return Promise.of(audience);
        }

        @Override
        public Promise<Optional<Audience>> findById(DmWorkspaceId workspaceId, String audienceId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<Optional<Audience>> findByCampaign(DmWorkspaceId workspaceId, String campaignId) {
            return Promise.of(Optional.ofNullable(audience));
        }

        @Override
        public Promise<List<Audience>> findByContactId(String contactId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Void> disableAudience(String audienceId, String reason) {
            return Promise.complete();
        }
    }

    private static final class EphemeralContentAssetRepository implements ContentAssetRepository {
        private int approvedCount;

        void setApprovedCount(int count) {
            this.approvedCount = count;
        }

        @Override
        public Promise<com.ghatana.digitalmarketing.domain.content.ContentAsset> save(
                com.ghatana.digitalmarketing.domain.content.ContentAsset asset) {
            return Promise.of(asset);
        }

        @Override
        public Promise<Optional<com.ghatana.digitalmarketing.domain.content.ContentAsset>> findById(
                DmWorkspaceId workspaceId, String assetId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<com.ghatana.digitalmarketing.domain.content.ContentAsset>> findByCampaign(
                DmWorkspaceId workspaceId, String campaignId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Integer> countApprovedByCampaign(DmWorkspaceId workspaceId, String campaignId) {
            return Promise.of(approvedCount);
        }
    }

    private static final class StubConsentInteractionBroker implements ConsentInteractionBroker {
        private boolean consentGranted = true;

        void setConsentGranted(boolean granted) {
            this.consentGranted = granted;
        }

        @Override
        public Promise<ConsentDecision> checkConsentStatus(DmOperationContext ctx, ConsentCheckRequest request) {
            String status = consentGranted ? "granted" : "denied";
            return Promise.of(new ConsentDecision(
                consentGranted,
                status,
                Instant.now(),
                "evidence-ref-123"
            ));
        }
    }
}
