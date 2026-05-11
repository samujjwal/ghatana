/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.lifecycle;

/**
 * Unified operational lifecycle for every governed agent turn.
 *
 * <p>PERCEIVE, REASON, ACT, CAPTURE, and REFLECT remain the agent cognitive
 * lifecycle. ADMIT, VERIFY, and COMPLETE are platform runtime phases.
 */
public enum AgentLifecyclePhase {
    ADMIT,
    PERCEIVE,
    REASON,
    VERIFY,
    ACT,
    CAPTURE,
    REFLECT,
    COMPLETE
}
