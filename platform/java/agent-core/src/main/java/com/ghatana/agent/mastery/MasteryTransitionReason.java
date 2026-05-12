/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

/**
 * Reason for a mastery state transition.
 *
 * @doc.type enum
 * @doc.purpose Reason for mastery state transition
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum MasteryTransitionReason {
    FIRST_OBSERVATION,
    REPEATED_SUCCESS,
    EVALUATION_PASSED,
    REGRESSION_FAILED,
    VERSION_CHANGED,
    SECURITY_GUIDANCE_CHANGED,
    API_CONTRACT_CHANGED,
    PROCEDURE_OUTPERFORMED,
    USER_OR_REPO_CONVENTION_CHANGED,
    MANUAL_REVIEW,
    RETIREMENT_REQUESTED
}
