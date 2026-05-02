package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentAssetVersion;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository contract for immutable content asset versions.
 *
 * @doc.type interface
 * @doc.purpose DMOS content version repository for immutable asset library history
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ContentVersionRepository {

    Promise<ContentAssetVersion> save(ContentAssetVersion version);

    Promise<Optional<ContentAssetVersion>> findLatestVersion(DmWorkspaceId workspaceId, String assetId);

    Promise<List<ContentAssetVersion>> listVersions(DmWorkspaceId workspaceId, String assetId);
}
