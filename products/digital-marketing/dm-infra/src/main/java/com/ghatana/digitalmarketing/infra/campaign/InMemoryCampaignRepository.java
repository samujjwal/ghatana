package com.ghatana.digitalmarketing.infra.campaign;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import io.activej.promise.Promise;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory {@link CampaignRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Keys are composed as {@code "<workspaceId>:<campaignId>"} to scope campaigns within
 * their workspace. Tenant isolation is guaranteed at the workspace level since each workspace
 * belongs to exactly one tenant.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory campaign persistence adapter for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class InMemoryCampaignRepository implements CampaignRepository {

    private static final int MAX_PAGE_SIZE = 100;

    private final ConcurrentHashMap<String, Campaign> store = new ConcurrentHashMap<>();

    @Override
    public Promise<Campaign> save(DmTenantId tenantId, Campaign campaign) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(campaign, "campaign must not be null");
        store.put(key(tenantId, campaign.getWorkspaceId(), campaign.getId()), campaign);
        return Promise.of(campaign);
    }

    @Override
    public Promise<Optional<Campaign>> findById(DmTenantId tenantId, DmWorkspaceId workspaceId, String campaignId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        return Promise.of(Optional.ofNullable(store.get(key(tenantId, workspaceId, campaignId))));
    }

    @Override
    public Promise<List<Campaign>> listByWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId, int limit, int offset) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int boundedOffset = Math.max(offset, 0);

        String prefix = tenantId.getValue() + ":" + workspaceId.getValue() + ":";
        List<Campaign> results = store.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(java.util.Map.Entry::getValue)
            .sorted(Comparator.comparing(Campaign::getCreatedAt).reversed()
                .thenComparing(Campaign::getId))
            .skip(boundedOffset)
            .limit(boundedLimit)
            .collect(Collectors.toList());

        return Promise.of(results);
    }

    @Override
    public Promise<Long> countByWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        String prefix = tenantId.getValue() + ":" + workspaceId.getValue() + ":";
        long count = store.keySet().stream()
            .filter(k -> k.startsWith(prefix))
            .count();

        return Promise.of(count);
    }

    private static String key(DmTenantId tenantId, DmWorkspaceId workspaceId, String campaignId) {
        return tenantId.getValue() + ":" + workspaceId.getValue() + ":" + campaignId;
    }
}
