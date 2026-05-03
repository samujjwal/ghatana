package com.ghatana.digitalmarketing.infra.content;

import com.ghatana.digitalmarketing.application.content.ContentVersionRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentAssetVersion;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ContentVersionRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Versions are keyed by {@code "<workspaceId>:<versionId>"} and the "latest" lookup
 * performs a linear scan over all versions for the given asset, returning the one with
 * the highest version number.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory content version persistence adapter for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class InMemoryContentVersionRepository implements ContentVersionRepository {

    private final ConcurrentHashMap<String, ContentAssetVersion> store = new ConcurrentHashMap<>();

    @Override
    public Promise<ContentAssetVersion> save(ContentAssetVersion version) {
        Objects.requireNonNull(version, "version must not be null");
        store.put(key(version.getWorkspaceId(), version.getVersionId()), version);
        return Promise.of(version);
    }

    @Override
    public Promise<Optional<ContentAssetVersion>> findLatestVersion(
            DmWorkspaceId workspaceId,
            String assetId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");
        return Promise.of(
            store.values().stream()
                .filter(v -> v.getWorkspaceId().equals(workspaceId) && v.getAssetId().equals(assetId))
                .max(Comparator.comparingInt(ContentAssetVersion::getVersionNumber))
        );
    }

    @Override
    public Promise<List<ContentAssetVersion>> listVersions(DmWorkspaceId workspaceId, String assetId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");
        List<ContentAssetVersion> result = new ArrayList<>();
        for (ContentAssetVersion v : store.values()) {
            if (v.getWorkspaceId().equals(workspaceId) && v.getAssetId().equals(assetId)) {
                result.add(v);
            }
        }
        result.sort(Comparator.comparingInt(ContentAssetVersion::getVersionNumber));
        return Promise.of(result);
    }

    private static String key(DmWorkspaceId workspaceId, String versionId) {
        return workspaceId.getValue() + ":" + versionId;
    }
}
