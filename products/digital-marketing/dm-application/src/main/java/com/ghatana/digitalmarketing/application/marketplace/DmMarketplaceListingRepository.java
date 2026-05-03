package com.ghatana.digitalmarketing.application.marketplace;

import com.ghatana.digitalmarketing.domain.marketplace.DmMarketplaceListing;
import com.ghatana.digitalmarketing.domain.marketplace.DmMarketplaceListingStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for marketplace listing persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for marketplace listing storage (DMOS-F5-001)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmMarketplaceListingRepository {

    Promise<DmMarketplaceListing> save(DmMarketplaceListing listing);

    Promise<DmMarketplaceListing> update(DmMarketplaceListing listing);

    Promise<Optional<DmMarketplaceListing>> findById(String id);

    Promise<List<DmMarketplaceListing>> listByTenant(String tenantId);

    Promise<List<DmMarketplaceListing>> listByStatus(DmMarketplaceListingStatus status);
}
