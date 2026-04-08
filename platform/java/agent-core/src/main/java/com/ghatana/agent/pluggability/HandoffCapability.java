/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

/**
 * Capability level for agent handoff participation.
 *
 * @doc.type enum
 * @doc.purpose Handoff capability taxonomy for agent capability manifests
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum HandoffCapability {
    /** Agent cannot participate in handoffs in any role. */
    NONE,
    /** Agent can receive a handoff (incoming context) but cannot initiate one. */
    RECEIVER_ONLY,
    /** Agent can initiate a handoff but cannot receive one from another agent. */
    INITIATOR_ONLY,
    /** Agent can both initiate and receive handoffs. */
    BIDIRECTIONAL
}
