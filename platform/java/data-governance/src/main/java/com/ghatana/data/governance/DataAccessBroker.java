/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;

/**
 * Unified gatekeeper for data access decisions.
 *
 * <p>Composes consent verification ({@link ConsentManager}) and purpose-limitation
 * enforcement ({@link PurposeLimitationEnforcer}) into a single {@code checkAccess}
 * call that callers invoke before reading or processing any governed data asset.
 *
 * <p>Access is granted only when:
 * <ol>
 *   <li>The data subject has given consent for the declared purpose, AND</li>
 *   <li>The data asset has been bound to that purpose via {@link PurposeLimitationEnforcer}.</li>
 * </ol>
 *
 * @doc.type interface
 * @doc.purpose Unified consent + purpose-limitation gate for governed data access
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface DataAccessBroker {

    /**
     * Assert that the given subject has consented and the data asset is bound to the purpose.
     *
     * @param tenantId  owning tenant
     * @param subjectId the data-subject identity (e.g. user ID)
     * @param dataId    logical data asset identifier
     * @param purpose   the declared processing purpose
     * @return completed promise if access is granted; failed with a descriptive exception otherwise
     */
    Promise<Void> checkAccess(String tenantId, String subjectId, String dataId, String purpose);
}
