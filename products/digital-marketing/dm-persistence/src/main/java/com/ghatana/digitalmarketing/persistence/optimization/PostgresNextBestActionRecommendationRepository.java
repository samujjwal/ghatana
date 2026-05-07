package com.ghatana.digitalmarketing.persistence.optimization;

import com.ghatana.digitalmarketing.application.optimization.NextBestActionRecommendationRepository;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionRecommendation;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionStatus;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PostgreSQL implementation of {@link NextBestActionRecommendationRepository}.
 *
 * <p>Currently uses in-memory storage for development. Production implementation
 * should use PostgreSQL with proper schema and connection pooling.</p>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence for next-best-action recommendations (P3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresNextBestActionRecommendationRepository implements NextBestActionRecommendationRepository {

    private final Map<String, NextBestActionRecommendation> storage = new ConcurrentHashMap<>();

    @Override
    public Promise<NextBestActionRecommendation> save(NextBestActionRecommendation recommendation) {
        storage.put(recommendation.getId(), recommendation);
        return Promise.of(recommendation);
    }

    @Override
    public Promise<NextBestActionRecommendation> update(NextBestActionRecommendation recommendation) {
        if (!storage.containsKey(recommendation.getId())) {
            return Promise.ofException(new IllegalArgumentException("Recommendation not found: " + recommendation.getId()));
        }
        storage.put(recommendation.getId(), recommendation);
        return Promise.of(recommendation);
    }

    @Override
    public Promise<Optional<NextBestActionRecommendation>> findById(String id) {
        return Promise.of(Optional.ofNullable(storage.get(id)));
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByTenant(String tenantId) {
        List<NextBestActionRecommendation> result = new ArrayList<>();
        for (NextBestActionRecommendation rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByWorkspace(String tenantId, String workspaceId) {
        List<NextBestActionRecommendation> result = new ArrayList<>();
        for (NextBestActionRecommendation rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getWorkspaceId().equals(workspaceId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByCampaign(String tenantId, String campaignId) {
        List<NextBestActionRecommendation> result = new ArrayList<>();
        for (NextBestActionRecommendation rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getCampaignId().equals(campaignId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByStatus(String tenantId, NextBestActionStatus status) {
        List<NextBestActionRecommendation> result = new ArrayList<>();
        for (NextBestActionRecommendation rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getStatus() == status) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }
}
