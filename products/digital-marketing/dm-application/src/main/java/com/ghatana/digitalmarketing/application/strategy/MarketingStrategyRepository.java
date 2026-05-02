package com.ghatana.digitalmarketing.application.strategy;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository contract for persisting and retrieving marketing strategies.
 *
 * @doc.type class
 * @doc.purpose Data access contract for MarketingStrategy aggregate storage
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface MarketingStrategyRepository {

    /**
     * Persists a marketing strategy.
     *
     * @param strategy the strategy to save
     * @return promise resolving to the saved strategy
     */
    Promise<MarketingStrategy> save(MarketingStrategy strategy);

    /**
     * Retrieves the latest strategy for a workspace.
     *
     * @param workspaceId target workspace
     * @return promise resolving to an optional containing the latest strategy, or empty if none exists
     */
    Promise<Optional<MarketingStrategy>> findLatestByWorkspace(DmWorkspaceId workspaceId);

    /**
     * Retrieves a specific strategy by its identifier.
     *
     * @param strategyId the strategy identifier
     * @return promise resolving to an optional containing the strategy, or empty if not found
     */
    Promise<Optional<MarketingStrategy>> findById(String strategyId);
}
