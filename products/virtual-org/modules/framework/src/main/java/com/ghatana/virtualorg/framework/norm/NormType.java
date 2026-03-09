package com.ghatana.virtualorg.framework.norm;

/**
 * Types of norms in the organization.
 *
 * <p><b>Purpose</b><br>
 * Defines the three fundamental norm types from deontic logic:
 * - OBLIGATION: What an agent MUST do
 * - PROHIBITION: What an agent MUST NOT do
 * - PERMISSION: What an agent CAN do
 *
 * @doc.type enum
 * @doc.purpose Norm type classification
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public enum NormType {
    /**
     * An obligation is a norm that requires an agent to perform an action.
     * Violation occurs when the action is NOT performed within the deadline.
     */
    OBLIGATION,

    /**
     * A prohibition is a norm that forbids an agent from performing an action.
     * Violation occurs when the action IS performed.
     */
    PROHIBITION,

    /**
     * A permission is a norm that allows an agent to perform an action.
     * This is the weakest form - no violation is possible.
     */
    PERMISSION
}
