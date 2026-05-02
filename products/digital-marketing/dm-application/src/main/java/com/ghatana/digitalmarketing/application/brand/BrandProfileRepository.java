package com.ghatana.digitalmarketing.application.brand;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.brand.BrandProfile;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository for brand profile persistence.
 *
 * @doc.type interface
 * @doc.purpose DMOS brand profile repository contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface BrandProfileRepository {

    Promise<BrandProfile> save(BrandProfile profile);

    Promise<Optional<BrandProfile>> findByWorkspace(DmWorkspaceId workspaceId);
}
