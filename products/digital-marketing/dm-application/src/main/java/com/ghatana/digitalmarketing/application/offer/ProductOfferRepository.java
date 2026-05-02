package com.ghatana.digitalmarketing.application.offer;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.offer.ProductOffer;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository for product offer catalog persistence.
 *
 * @doc.type interface
 * @doc.purpose DMOS offer catalog repository contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ProductOfferRepository {

    Promise<ProductOffer> save(ProductOffer offer);

    Promise<Optional<ProductOffer>> findById(DmWorkspaceId workspaceId, String offerId);

    Promise<List<ProductOffer>> listByWorkspace(DmWorkspaceId workspaceId, boolean includeInactive);
}
