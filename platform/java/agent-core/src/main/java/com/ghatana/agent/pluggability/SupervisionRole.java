/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

/**
 * Role of an agent in a supervision hierarchy.
 *
 * @doc.type enum
 * @doc.purpose Supervision role taxonomy for agent capability manifests
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum SupervisionRole {
    /** Agent supervises one or more child agents. */
    SUPERVISOR,
    /** Agent is supervised by a parent agent. */
    SUBORDINATE,
    /** Agent participates as a peer — no supervisory relationship. */
    PEER,
    /** Agent is self-aware but not supervised (root-level autonomous agent). */
    STANDALONE
}
