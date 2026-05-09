package com.ghatana.digitalmarketing.infra.optimization;

import com.ghatana.digitalmarketing.application.optimization.NextBestActionRecommendationRepository;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionRecommendation;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory {@link NextBestActionRecommendationRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Stores next-best-action recommendations by ID and supports filtering by tenant, workspace,
 * campaign, and status. Used for local development and test deployments where a
 * full PostgreSQL database is not available.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory next-best-action recommendation repository for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class InMemoryNextBestActionRecommendationRepository implements NextBestActionRecommendationRepository {

    private final ConcurrentHashMap<String, NextBestActionRecommendation> store = new ConcurrentHashMap<>();

    @Override
    public Promise<NextBestActionRecommendation> save(NextBestActionRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        store.put(recommendation.getId(), recommendation);
        return Promise.of(recommendation);
    }

    @Override
    public Promise<NextBestActionRecommendation> update(NextBestActionRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        if (!store.containsKey(recommendation.getId())) {
            return Promise.ofException(new IllegalArgumentException(
                "Recommendation not found: " + recommendation.getId()));
        }
        store.put(recommendation.getId(), recommendation);
        return Promise.of(recommendation);
    }

    @Override
    public Promise<Optional<NextBestActionRecommendation>> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.of(Optional.ofNullable(store.get(id)));
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        List<NextBestActionRecommendation> results = store.values().stream()
            .filter(r -> r.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
        return Promise.of(results);
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByWorkspace(String tenantId, String workspaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        List<NextBestActionRecommendation> results = store.values().stream()
            .filter(r -> r.getTenantId().equals(tenantId) && r.getWorkspaceId().equals(workspaceId))
            .collect(Collectors.toList());
        return Promise.of(results);
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByCampaign(String tenantId, String campaignId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        List<NextBestActionRecommendation> results = store.values().stream()
            .filter(r -> r.getTenantId().equals(tenantId) && r.getCampaignId().equals(campaignId))
            .collect(Collectors.toList());
        return Promise.of(results);
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByStatus(String tenantId, NextBestActionStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        List<NextBestActionRecommendation> results = store.values().stream()
            .filter(r -> r.getTenantId().equals(tenantId) && r.getStatus().equals(status))
            .collect(Collectors.toList());
        return Promise.of(results);
    }
}
