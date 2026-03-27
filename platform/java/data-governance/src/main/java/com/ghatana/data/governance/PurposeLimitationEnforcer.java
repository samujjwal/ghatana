/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;
import java.util.Set;

/**
 * Enforces purpose-limitation rules per the GDPR Article 5(1)(b) principle:
 * personal data collected for one purpose must not be reused for incompatible purposes.
 *
 * <p>Callers bind a data asset to a set of permitted purposes at ingestion time, then
 * call {@link #enforceForPurpose} at every downstream access. A violation throws
 * {@link PurposeViolationException}.
 *
 * @doc.type interface
 * @doc.purpose Bind data assets to allowed processing purposes and enforce at access time
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface PurposeLimitationEnforcer {

    /**
     * Bind (or overwrite) the permitted purposes for a data asset.
     *
     * @param tenantId       owning tenant
     * @param dataId         logical identifier for the data asset (e.g. field path, collection key)
     * @param allowedPurposes non-empty set of permitted purpose labels
     * @return completed promise on success
     */
    Promise<Void> bindPurpose(String tenantId, String dataId, Set<String> allowedPurposes);

    /**
     * Verify that {@code requestedPurpose} is among the allowed purposes for {@code dataId}.
     *
     * @param tenantId         owning tenant
     * @param dataId           data asset identifier
     * @param requestedPurpose purpose the caller intends to use the data for
     * @return completed promise if access is allowed; failed with {@link PurposeViolationException} otherwise
     */
    Promise<Void> enforceForPurpose(String tenantId, String dataId, String requestedPurpose);

    /**
     * Return the set of allowed purposes for the given data asset, or empty if no binding exists.
     *
     * @param tenantId owning tenant
     * @param dataId   data asset identifier
     * @return promise resolving to the set of allowed purposes (may be empty)
     */
    Promise<Set<String>> getAllowedPurposes(String tenantId, String dataId);
}
