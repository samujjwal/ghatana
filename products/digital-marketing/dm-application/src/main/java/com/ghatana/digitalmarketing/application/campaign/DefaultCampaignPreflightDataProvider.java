package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.application.audience.AudienceRepository;
import com.ghatana.digitalmarketing.application.budget.BudgetRepository;
import com.ghatana.digitalmarketing.application.content.ContentAssetRepository;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.budget.Budget;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.Optional;

/**
 * Production implementation of {@link CampaignPreflightDataProvider}.
 *
 * <p>Aggregates budget approval status, audience segment count, approved content
 * asset count, and spend data from the DMOS repositories. All data lookups are
 * workspace-scoped and tenant-isolated via the operation context.</p>
 *
 * @doc.type class
 * @doc.purpose Resolve campaign preflight evidence for DMOS compliance evaluation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultCampaignPreflightDataProvider implements CampaignPreflightDataProvider {

    private final BudgetRepository budgetRepository;
    private final AudienceRepository audienceRepository;
    private final ContentAssetRepository contentAssetRepository;

    /**
     * Constructs the provider with all required repositories.
     *
     * @param budgetRepository        budget persistence port; must not be null
     * @param audienceRepository      audience segment persistence port; must not be null
     * @param contentAssetRepository  content asset persistence port; must not be null
     */
    public DefaultCampaignPreflightDataProvider(
            BudgetRepository budgetRepository,
            AudienceRepository audienceRepository,
            ContentAssetRepository contentAssetRepository) {
        this.budgetRepository       = Objects.requireNonNull(budgetRepository,       "budgetRepository must not be null");
        this.audienceRepository     = Objects.requireNonNull(audienceRepository,     "audienceRepository must not be null");
        this.contentAssetRepository = Objects.requireNonNull(contentAssetRepository, "contentAssetRepository must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Queries the three domain repositories sequentially and assembles
     * {@link CampaignPreflightData}. All queries are workspace-scoped from
     * {@code ctx.getWorkspaceId()}.</p>
     */
    @Override
    public Promise<CampaignPreflightData> resolve(DmOperationContext ctx, Campaign campaign) {
        Objects.requireNonNull(ctx,      "ctx must not be null");
        Objects.requireNonNull(campaign, "campaign must not be null");

        return budgetRepository.findApprovedByCampaign(ctx.getWorkspaceId(), campaign.getId())
            .then(optBudget ->
                audienceRepository.findByCampaign(ctx.getWorkspaceId(), campaign.getId())
                    .then(optAudience ->
                        contentAssetRepository.countApprovedByCampaign(ctx.getWorkspaceId(), campaign.getId())
                            .map(approvedContentCount -> {
                                Optional<Budget> budget = optBudget;
                                boolean budgetApproved  = budget.isPresent() && budget.get().isApprovedAndSolvent();
                                int audienceCount       = optAudience.map(a -> a.size()).orElse(0);
                                double totalSpend       = budget.map(Budget::getSpentAmount).orElse(0.0);
                                double approvedBudget   = budget.map(Budget::getAllocatedAmount).orElse(0.0);

                                return new CampaignPreflightData(
                                    budgetApproved,
                                    audienceCount,
                                    approvedContentCount,
                                    totalSpend,
                                    approvedBudget
                                );
                            })
                    )
            );
    }
}
