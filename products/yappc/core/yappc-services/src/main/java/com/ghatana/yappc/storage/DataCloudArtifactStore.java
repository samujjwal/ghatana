/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — DataCloud-backed Artifact Store
 *
 * CONSOLIDATION NOTE (2026-04-24):
 *   This class is a deprecated forwarding wrapper.
 *   The canonical implementation is
 *   com.ghatana.yappc.services.lifecycle.storage.YappcDataCloudArtifactStore
 *   in the :products:yappc:core:services-lifecycle module.
 *   Migrate all usages and remove this file in the next major refactor cycle.
 */
package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.services.lifecycle.storage.YappcDataCloudArtifactStore;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * @deprecated Use {@link com.ghatana.yappc.services.lifecycle.storage.YappcDataCloudArtifactStore}
 *             in {@code :products:yappc:core:services-lifecycle} instead.
 *             This class is a forwarding wrapper kept for backward compatibility only.
 *
 * @doc.type class
 * @doc.purpose Deprecated forwarding wrapper — delegates to canonical YappcDataCloudArtifactStore
 * @doc.layer infrastructure
 * @doc.pattern Repository/Adapter
 */
@Deprecated(forRemoval = true)
public class DataCloudArtifactStore implements ArtifactStore {

    private final YappcDataCloudArtifactStore delegate;

    /**
     * @deprecated Use {@link YappcDataCloudArtifactStore} directly.
     */
    @Deprecated(forRemoval = true)
    public DataCloudArtifactStore(DataCloudClient client, ObjectMapper mapper) {
        this.delegate = new YappcDataCloudArtifactStore(client, mapper);
    }

    @Override
    public Promise<String> put(String path, byte[] content) {
        return delegate.put(path, content);
    }

    @Override
    public Promise<byte[]> get(String path) {
        return delegate.get(path);
    }

    @Override
    public Promise<List<String>> list(String prefix) {
        return delegate.list(prefix);
    }

    @Override
    public Promise<Void> putMetadata(String path, Map<String, String> meta) {
        return delegate.putMetadata(path, meta);
    }

    @Override
    public Promise<Map<String, String>> getMetadata(String path) {
        return delegate.getMetadata(path);
    }

    @Override
    public Promise<Void> delete(String path) {
        return delegate.delete(path);
    }
}
