/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * @doc.type enum
 * @doc.purpose Represents the promotion state of a learned artifact
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum PromotionState {
    DRAFT,
    EVALUATED,
    APPROVED,
    ACTIVE,
    ROLLED_BACK
}
