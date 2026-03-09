/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 *
 * This source code and the accompanying materials are the confidential
 * and proprietary information of Ghatana Inc. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Ghatana Inc.
 *
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */
package com.ghatana.security.govern;

import com.ghatana.platform.audit.AuditEvent;

import java.util.Objects;

/**
 * Result of a policy enforcement operation.
 * Contains the decision (granted/denied), reason, and associated audit event.
 *
 * @doc.type class
 * @doc.purpose Policy enforcement result
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class PolicyEnforcementResult {

    private final boolean granted;
    private final String reason;
    private final AuditEvent auditEvent;

    private PolicyEnforcementResult(Builder builder) {
        this.granted = builder.granted;
        this.reason = builder.reason;
        this.auditEvent = builder.auditEvent;
    }

    public boolean isGranted() { return granted; }
    public String getReason() { return reason; }
    public AuditEvent getAuditEvent() { return auditEvent; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean granted;
        private String reason;
        private AuditEvent auditEvent;

        public Builder granted(boolean granted) { this.granted = granted; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder auditEvent(AuditEvent auditEvent) { this.auditEvent = auditEvent; return this; }

        public PolicyEnforcementResult build() { return new PolicyEnforcementResult(this); }
    }

    /**
     * Creates a granted result.
     */
    public static PolicyEnforcementResult granted(String reason) {
        return builder().granted(true).reason(reason).build();
    }

    /**
     * Creates a denied result.
     */
    public static PolicyEnforcementResult denied(String reason) {
        return builder().granted(false).reason(reason).build();
    }

    /**
     * Creates a granted result with audit event.
     */
    public static PolicyEnforcementResult granted(String reason, AuditEvent auditEvent) {
        return builder().granted(true).reason(reason).auditEvent(auditEvent).build();
    }

    /**
     * Creates a denied result with audit event.
     */
    public static PolicyEnforcementResult denied(String reason, AuditEvent auditEvent) {
        return builder().granted(false).reason(reason).auditEvent(auditEvent).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyEnforcementResult that)) return false;
        return granted == that.granted &&
                Objects.equals(reason, that.reason) &&
                Objects.equals(auditEvent, that.auditEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(granted, reason, auditEvent);
    }

    @Override
    public String toString() {
        return "PolicyEnforcementResult{granted=" + granted + ", reason='" + reason + "'}";
    }
}