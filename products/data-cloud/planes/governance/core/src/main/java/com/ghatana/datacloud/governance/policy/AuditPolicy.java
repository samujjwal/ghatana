/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import java.util.Set;

/**
 * Audit policy for governance operations.
 *
 * <p>Defines which operations must be audited and at what level.
 *
 * @doc.type record
 * @doc.purpose Defines audit requirements for governance operations
 * @doc.layer product
 * @doc.pattern Policy
 */
public record AuditPolicy(
    String policyId,
    boolean auditCreate,
    boolean auditUpdate,
    boolean auditDelete,
    boolean auditRead,
    Set<String> auditedResourceTypes,
    AuditLevel auditLevel) {

    public enum AuditLevel {
        MINIMAL,
        STANDARD,
        DETAILED,
        FULL
    }

    public AuditPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (auditedResourceTypes == null) {
            auditedResourceTypes = Set.of();
        }
    }

    public boolean shouldAudit(String resourceType, String action) {
        if (!auditedResourceTypes.isEmpty() && !auditedResourceTypes.contains(resourceType)) {
            return false;
        }

        return switch (action.toLowerCase()) {
            case "create" -> auditCreate;
            case "update" -> auditUpdate;
            case "delete" -> auditDelete;
            case "read" -> auditRead;
            default -> false;
        };
    }
}
