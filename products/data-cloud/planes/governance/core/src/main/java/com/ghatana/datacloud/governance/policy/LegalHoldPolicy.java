/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import java.time.Instant;
import java.util.Set;

/**
 * Legal hold policy for data preservation.
 *
 * <p>Prevents deletion or modification of data under legal hold.
 *
 * @doc.type record
 * @doc.purpose Defines legal hold requirements for data preservation
 * @doc.layer product
 * @doc.pattern Policy
 */
public record LegalHoldPolicy(
    String policyId,
    String caseId,
    String caseName,
    Instant holdStartDate,
    Instant holdEndDate,
    Set<String> heldResourceIds,
    String authorizedBy,
    String notes) {

    public LegalHoldPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        if (holdStartDate == null) {
            throw new IllegalArgumentException("holdStartDate must not be null");
        }
        if (heldResourceIds == null) {
            heldResourceIds = Set.of();
        }
        if (authorizedBy == null || authorizedBy.isBlank()) {
            throw new IllegalArgumentException("authorizedBy must not be blank");
        }
    }

    public boolean isActive() {
        if (holdEndDate == null) {
            return true; // Indefinite hold
        }
        return Instant.now().isBefore(holdEndDate);
    }

    public boolean isResourceOnHold(String resourceId) {
        return isActive() && heldResourceIds.contains(resourceId);
    }

    public boolean canModify(String resourceId) {
        return !isResourceOnHold(resourceId);
    }

    public boolean canDelete(String resourceId) {
        return !isResourceOnHold(resourceId);
    }
}
