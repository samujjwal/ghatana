package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Persistence port for {@link DmGoogleAdsCampaignLink}.
 *
 * @doc.type class
 * @doc.purpose Stores mapping between internal DMOS campaigns and Google Ads campaign IDs (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmGoogleAdsCampaignLinkRepository {

    Promise<DmGoogleAdsCampaignLink> save(DmGoogleAdsCampaignLink link);

    Promise<Optional<DmGoogleAdsCampaignLink>> findByInternalCampaignId(String internalCampaignId);
}
