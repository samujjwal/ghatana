package com.ghatana.digitalmarketing.infra.googleads;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import io.activej.promise.Promise;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
/**
 * In-memory implementation of {@link DmGoogleAdsCampaignLinkRepository} for local and test profiles.
 *
 * @doc.type class
 * @doc.purpose In-memory Google Ads campaign link repository for local development and testing
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DmGoogleAdsCampaignLinkInMemoryRepository implements DmGoogleAdsCampaignLinkRepository {
    private final ConcurrentHashMap<String, DmGoogleAdsCampaignLink> storeByInternalId = new ConcurrentHashMap<>();
    @Override
    public Promise<DmGoogleAdsCampaignLink> save(DmGoogleAdsCampaignLink link) {
        storeByInternalId.put(link.getInternalCampaignId(), link);
        return Promise.of(link);
    }
    @Override
    public Promise<Optional<DmGoogleAdsCampaignLink>> findByInternalCampaignId(String internalCampaignId) {
        return Promise.of(Optional.ofNullable(storeByInternalId.get(internalCampaignId)));
    }
}
