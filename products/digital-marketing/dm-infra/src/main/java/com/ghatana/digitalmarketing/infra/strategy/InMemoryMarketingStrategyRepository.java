package com.ghatana.digitalmarketing.infra.strategy;

import com.ghatana.digitalmarketing.application.strategy.MarketingStrategyRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import io.activej.promise.Promise;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory marketing strategy repository for development and E2E smoke tests.
 *
 * @doc.type class
 * @doc.purpose Development adapter for marketing strategy persistence
 * @doc.layer infra
 * @doc.pattern Repository
 */
public final class InMemoryMarketingStrategyRepository implements MarketingStrategyRepository {

    private final Map<String, MarketingStrategy> byId = new ConcurrentHashMap<>();

    @Override
    public Promise<MarketingStrategy> save(MarketingStrategy strategy) {
        byId.put(strategy.getStrategyId(), strategy);
        return Promise.of(strategy);
    }

    @Override
    public Promise<Optional<MarketingStrategy>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
        return Promise.of(byId.values().stream()
            .filter(strategy -> strategy.getWorkspaceId().equals(workspaceId))
            .max(Comparator.comparing(MarketingStrategy::getGeneratedAt)));
    }

    @Override
    public Promise<Optional<MarketingStrategy>> findById(DmWorkspaceId workspaceId, String strategyId) {
        return Promise.of(Optional.ofNullable(byId.get(strategyId))
            .filter(strategy -> strategy.getWorkspaceId().equals(workspaceId)));
    }
}
