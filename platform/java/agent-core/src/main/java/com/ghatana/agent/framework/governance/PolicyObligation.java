/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An obligation imposed by a policy decision that must be fulfilled before
 * or after an action proceeds.
 *
 * @doc.type record
 * @doc.purpose Represents a governance obligation attached to a policy decision
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record PolicyObligation(
        /** Type of obligation. */
        @NotNull ObligationType type,

        /** Human-readable description of what must be done. */
        @NotNull String description,

        /** Whether this obligation must be fulfilled before execution. */
        boolean beforeExecution
) {

    public PolicyObligation {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }

    /**
     * Types of governance obligations.
     */
    public enum ObligationType {
        /** Explicit approval from a designated role is required. */
        APPROVAL_REQUIRED,

        /** Full action artifacts must be logged for audit trail. */
        LOG_FULL_ARTIFACTS,

        /** Sensitive content must be redacted from the output. */
        REDACT_OUTPUT,

        /** A compensation plan must be registered before execution. */
        REQUIRE_COMPENSATION_PLAN,

        /** Enhanced monitoring/observability must be active during execution. */
        ELEVATE_MONITORING
    }
}
