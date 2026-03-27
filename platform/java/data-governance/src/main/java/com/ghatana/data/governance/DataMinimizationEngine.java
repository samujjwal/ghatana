/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Strips or masks data fields that are not required for a given processing purpose.
 *
 * <p>Implements the GDPR data-minimisation principle (Article 5(1)(c)) by
 * removing fields whose classification exceeds the sensitivity level permitted
 * for the declared purpose, and masking fields that are partially permitted.
 *
 * @doc.type interface
 * @doc.purpose Strip or mask data fields not required for the declared processing purpose
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface DataMinimizationEngine {

    /**
     * Apply minimization rules to a flat key-value data map.
     *
     * <p>Fields classified as sensitive but not permitted for {@code purpose} will be removed.
     * Fields that are permitted but should be masked will have their values replaced with a
     * masked string (e.g. {@code "***"}).
     *
     * @param tenantId owning tenant (used to look up tenant-specific rules)
     * @param purpose  the declared processing purpose
     * @param data     original data (may contain sensitive fields); not modified in place
     * @return promise resolving to a new, minimized map
     */
    Promise<Map<String, Object>> minimize(String tenantId, String purpose, Map<String, Object> data);
}
