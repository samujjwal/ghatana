/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

/**
 * Type of evidence supporting a mastery state transition.
 *
 * @doc.type enum
 * @doc.purpose Type of evidence for mastery transitions
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum MasteryEvidenceType {
    EPISODE,
    TRACE,
    EVALUATION_RUN,
    TEST_RUN,
    HUMAN_REVIEW,
    OFFICIAL_DOC_SNAPSHOT,
    SECURITY_ADVISORY,
    REGRESSION_RESULT,
    TOOL_OUTPUT,
    USER_FEEDBACK
}
