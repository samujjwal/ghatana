/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — Artifact Store Contract
 */
package com.ghatana.yappc.storage;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Contract for versioned artifact storage used across YAPPC lifecycle phases.
 *
 * <p>Implementations must be tenant-safe and support versioned storage. All operations
 * are async (ActiveJ {@link Promise}-based) to avoid blocking the event loop.
 *
 * @doc.type interface
 * @doc.purpose Versioned artifact storage contract for YAPPC lifecycle phases
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public interface ArtifactStore {

    /**
     * Stores artifact content at {@code path}.
     *
     * @param path    artifact path (e.g., {@code products/p1/phases/intent})
     * @param content raw artifact bytes
     * @return version identifier assigned to this upload
     */
    Promise<String> put(String path, byte[] content);

    /**
     * Retrieves artifact content at {@code path} (must include version segment).
     *
     * @param path versioned artifact path
     * @return raw artifact bytes
     */
    Promise<byte[]> get(String path);

    /**
     * Lists available versions for a path prefix.
     *
     * @param prefix path prefix to list
     * @return list of version identifiers
     */
    Promise<List<String>> list(String prefix);

    /**
     * Stores metadata key-value pairs for the given path.
     *
     * @param path path to attach metadata to
     * @param meta key-value metadata
     */
    Promise<Void> putMetadata(String path, Map<String, String> meta);

    /**
     * Retrieves metadata for the given path. Returns an empty map if none stored.
     *
     * @param path path to retrieve metadata for
     * @return key-value metadata map
     */
    Promise<Map<String, String>> getMetadata(String path);
}
