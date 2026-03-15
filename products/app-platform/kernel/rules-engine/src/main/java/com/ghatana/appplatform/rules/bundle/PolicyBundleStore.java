/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.bundle;

import java.util.List;
import java.util.Optional;

/**
 * Storage port for OPA policy bundles (K03-002).
 *
 * <p>Implementations store bundle content and metadata in external storage (S3/MinIO).
 * Versioning is handled by the store: each upload increments the version for the named bundle.
 *
 * @doc.type interface
 * @doc.purpose Storage port for policy bundles (S3/MinIO-backed)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface PolicyBundleStore {

    /**
     * Persists a new bundle version.
     *
     * @param bundle the fully constructed bundle to store (with hash and version already set)
     */
    void save(PolicyBundle bundle);

    /**
     * Returns the bundle with the given ID.
     */
    Optional<PolicyBundle> findById(String bundleId);

    /**
     * Returns the currently active bundle, if any.
     */
    Optional<PolicyBundle> findActive();

    /**
     * Returns all bundle versions ordered by version ascending.
     */
    List<PolicyBundle> findAll();

    /**
     * Replaces an existing bundle entry (used to toggle active flag).
     */
    void update(PolicyBundle bundle);

    /**
     * Returns the highest version number stored across all bundles, or 0 if no bundles exist.
     */
    int latestVersion();
}
