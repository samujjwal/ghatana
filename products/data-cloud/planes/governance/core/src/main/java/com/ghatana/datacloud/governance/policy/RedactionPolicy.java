/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import java.util.Set;

/**
 * Redaction policy for sensitive data.
 *
 * <p>Defines which fields should be redacted and how.
 *
 * @doc.type record
 * @doc.purpose Defines redaction requirements for sensitive fields
 * @doc.layer product
 * @doc.pattern Policy
 */
public record RedactionPolicy(
    String policyId,
    Set<String> redactedFields,
    RedactionMode redactionMode,
    Set<String> exemptRoles) {

    public enum RedactionMode {
        FULL,
        PARTIAL,
        HASH,
        MASK
    }

    public RedactionPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (redactedFields == null) {
            redactedFields = Set.of();
        }
        if (exemptRoles == null) {
            exemptRoles = Set.of();
        }
    }

    public boolean shouldRedact(String field, String userRole) {
        if (!redactedFields.contains(field)) {
            return false;
        }
        if (exemptRoles.contains(userRole)) {
            return false;
        }
        return true;
    }

    public String applyRedaction(String value) {
        if (value == null) {
            return null;
        }

        return switch (redactionMode) {
            case FULL -> "[REDACTED]";
            case PARTIAL -> {
                if (value.length() <= 4) {
                    yield "[REDACTED]";
                }
                yield value.substring(0, 2) + "****" + value.substring(value.length() - 2);
            }
            case HASH -> "[HASH:" + Integer.toHexString(value.hashCode()) + "]";
            case MASK -> "*".repeat(value.length());
        };
    }
}
