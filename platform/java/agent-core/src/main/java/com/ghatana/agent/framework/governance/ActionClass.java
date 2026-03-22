/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

/**
 * Classifies the nature of an agent action for governance routing.
 *
 * <p>Action classes form a risk hierarchy: actions higher in the enum
 * generally require stricter governance controls. The classification drives
 * policy evaluation, approval routing, evidence recording, and compensation
 * planning.
 *
 * @doc.type enum
 * @doc.purpose Classifies agent actions by risk and reversibility for governance
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum ActionClass {

    /** Read-only data access; no side effects. */
    READ,

    /** Produces draft output requiring human confirmation before commit. */
    DRAFT,

    /** Writes data that can be undone (e.g., soft-delete, update with history). */
    WRITE_REVERSIBLE,

    /** Writes data that cannot be undone (e.g., send email, financial transfer). */
    WRITE_IRREVERSIBLE,

    /** Calls an external system or API outside the platform boundary. */
    CALL_EXTERNAL,

    /** Delegates execution to another agent or sub-workflow. */
    DELEGATE,

    /** Mutates agent memory (episodic, semantic, procedural, or preference). */
    MEMORY_MUTATION,

    /** Changes a governance policy, rule, or configuration. */
    POLICY_CHANGE;

    /**
     * Returns {@code true} if this action class is considered privileged
     * and should always be policy-evaluated before execution.
     */
    public boolean isPrivileged() {
        return switch (this) {
            case READ, DRAFT -> false;
            case WRITE_REVERSIBLE, WRITE_IRREVERSIBLE, CALL_EXTERNAL,
                 DELEGATE, MEMORY_MUTATION, POLICY_CHANGE -> true;
        };
    }

    /**
     * Returns {@code true} if this action class produces side effects that
     * cannot be trivially reversed.
     */
    public boolean isIrreversible() {
        return this == WRITE_IRREVERSIBLE || this == CALL_EXTERNAL || this == POLICY_CHANGE;
    }
}
