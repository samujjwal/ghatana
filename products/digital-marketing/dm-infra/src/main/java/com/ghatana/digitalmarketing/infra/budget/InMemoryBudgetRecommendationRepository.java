package com.ghatana.digitalmarketing.infra.budget;

import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import io.activej.promise.Promise;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory budget recommendation repository for development and E2E smoke tests.
 *
 * @doc.type class
 * @doc.purpose Development adapter for budget recommendation persistence
 * @doc.layer infra
 * @doc.pattern Repository
 */
public final class InMemoryBudgetRecommendationRepository implements BudgetRecommendationRepository {

    private final Map<String, BudgetRecommendation> byId = new ConcurrentHashMap<>();

    @Override
    public Promise<BudgetRecommendation> save(BudgetRecommendation recommendation) {
        byId.put(recommendation.getRecommendationId(), recommendation);
        return Promise.of(recommendation);
    }

    @Override
    public Promise<Optional<BudgetRecommendation>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
        return Promise.of(byId.values().stream()
            .filter(recommendation -> recommendation.getWorkspaceId().equals(workspaceId))
            .max(Comparator.comparing(BudgetRecommendation::getGeneratedAt)));
    }

    @Override
    public Promise<Optional<BudgetRecommendation>> findById(String recommendationId) {
        return Promise.of(Optional.ofNullable(byId.get(recommendationId)));
    }

    @Override
    public Promise<Optional<BudgetRecommendation>> findById(DmWorkspaceId workspaceId, String recommendationId) {
        return Promise.of(Optional.ofNullable(byId.get(recommendationId))
            .filter(recommendation -> recommendation.getWorkspaceId().equals(workspaceId)));
    }
}
