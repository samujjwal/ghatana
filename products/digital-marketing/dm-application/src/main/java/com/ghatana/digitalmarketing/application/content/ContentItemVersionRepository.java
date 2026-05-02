package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository contract for rich {@link ContentVersion} persistence.
 *
 * <p>Distinct from the basic {@link ContentVersionRepository} which manages legacy
 * {@code ContentAssetVersion} snapshots. This repository manages the enriched
 * {@code ContentVersion} aggregate introduced in F1-017.</p>
 *
 * @doc.type interface
 * @doc.purpose DMOS enriched content version persistence contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ContentItemVersionRepository {

    Promise<ContentVersion> save(ContentVersion version);

    Promise<Optional<ContentVersion>> findById(DmWorkspaceId workspaceId, String versionId);

    Promise<Optional<ContentVersion>> findLatestApproved(DmWorkspaceId workspaceId, String itemId);

    Promise<List<ContentVersion>> findByItemId(DmWorkspaceId workspaceId, String itemId);

    Promise<Optional<ContentVersion>> findLatestByItemId(DmWorkspaceId workspaceId, String itemId);
}
