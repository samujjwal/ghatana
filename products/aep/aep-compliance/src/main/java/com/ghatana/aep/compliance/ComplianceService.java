/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import com.ghatana.data.governance.ConsentManager;
import com.ghatana.data.governance.DataAccessBroker;
import io.activej.promise.Promise;

/**
 * AEP compliance service that orchestrates consent verification and retention
 * checks as a single call for agent data-access decisions.
 *
 * <p>This is the primary entry point callers use in the AEP pipeline to verify
 * that a given data-access operation is compliant with:
 * <ol>
 *   <li>Subject consent (via {@link ConsentManager})</li>
 *   <li>Purpose limitation (via {@link DataAccessBroker})</li>
 *   <li>Retention policies (records are not past their retention deadline)</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose AEP compliance orchestrator for consent, purpose, and retention checks
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ComplianceService {

    private final DataAccessBroker dataAccessBroker;
    private final RetentionPolicyEnforcer retentionEnforcer;

    /**
     * Construct the compliance service.
     *
     * @param dataAccessBroker  broker that checks consent + purpose limitation
     * @param retentionEnforcer checks retention policy compliance
     */
    public ComplianceService(
            DataAccessBroker dataAccessBroker,
            RetentionPolicyEnforcer retentionEnforcer) {
        this.dataAccessBroker = dataAccessBroker;
        this.retentionEnforcer = retentionEnforcer;
    }

    /**
     * Perform a full compliance check before accessing a data record.
     *
     * @param tenantId  owning tenant
     * @param subjectId data subject (e.g. user ID)
     * @param dataId    logical data asset identifier
     * @param purpose   the declared processing purpose
     * @return completed promise if all checks pass; failed otherwise
     */
    public Promise<Void> checkCompliance(
            String tenantId, String subjectId, String dataId, String purpose) {
        return dataAccessBroker.checkAccess(tenantId, subjectId, dataId, purpose)
            .then(() -> retentionEnforcer.checkRetention(tenantId, dataId));
    }
}
