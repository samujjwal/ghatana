package com.ghatana.digitalmarketing.infra.campaign;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    private final ConcurrentHashMap<String, Campaign> store = new ConcurrentHashMap<>();

    @Override
    public Promise<Campaign> save(Campaign campaign) {
        Objects.requireNonNull(campaign, "campaign must not be null");
        store.put(key(campaign.getWorkspaceId(), campaign.getId()), campaign);
        return Promise.of(campaign);
    }

    @Override
    public Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String campaignId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        return Promise.of(Optional.ofNullable(store.get(key(workspaceId, campaignId))));
    }

    private static String key(DmWorkspaceId workspaceId, String campaignId) {
        return workspaceId.getValue() + ":" + campaignId;
    }
}
