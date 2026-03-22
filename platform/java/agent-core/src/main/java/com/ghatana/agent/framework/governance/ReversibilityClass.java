/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

/**
 * Classifies the reversibility characteristics of an agent action.
 *
 * <p>Used in conjunction with {@link ActionClass} to determine whether compensation
 * plans or approval gates are required.
 *
 * @doc.type enum
 * @doc.purpose Classifies action reversibility for compensation planning
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum ReversibilityClass {

    /** Action can be trivially undone (e.g., undo, soft-delete). */
    REVERSIBLE,

    /** Action can be compensated but requires explicit compensation logic. */
    COMPENSATABLE,

    /** Action cannot be undone once executed. */
    IRREVERSIBLE
}
