/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

/**
 * Defines the modes in which an agent may interact with other agents or
 * with human principals.
 *
 * @doc.type enum
 * @doc.purpose Interaction mode taxonomy for agent capability manifests
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum InteractionMode {
    /** Agent operates entirely autonomously with no human checkpoints. */
    AUTONOMOUS,
    /** Agent requires human-in-the-loop approval before certain actions. */
    SUPERVISED,
    /** Agent collaborates with other agents as peers (sub-agents or equals). */
    COLLABORATIVE,
    /** Agent delegates sub-tasks to specialist agents and aggregates results. */
    ORCHESTRATOR,
    /** Agent acts as a specialist receiving delegated tasks from an orchestrator. */
    SPECIALIST,
    /** Agent interacts directly with human principals in a conversational flow. */
    CONVERSATIONAL
}
