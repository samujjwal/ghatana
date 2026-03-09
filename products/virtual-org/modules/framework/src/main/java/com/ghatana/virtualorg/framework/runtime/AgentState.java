package com.ghatana.virtualorg.framework.runtime;

/**
 * Represents the current state of an agent in its execution lifecycle.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the finite states an agent can be in during autonomous operation.
 * Used by AgentRuntime to manage the think-act-observe loop.
 *
 * <p>
 * <b>State Transitions</b><br>
 * <pre>
 * IDLE ──event──→ PERCEIVING ──→ THINKING ──→ ACTING ──→ OBSERVING ──→ IDLE
 *   │                                │           │
 *   │                                ↓           ↓
 *   └─────────────────────────── WAITING ←── (low confidence / HITL)
 *                                    │
 *                                    ↓
 *                                 PAUSED
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Agent execution state definitions
 * @doc.layer product
 * @doc.pattern State
 */
public enum AgentState {

    /**
     * Agent is initializing and not ready for events.
     */
    INITIALIZING("Agent is initializing"),
    /**
     * Agent is idle and waiting for events.
     */
    IDLE("Agent is idle and waiting for work"),
    /**
     * Agent is perceiving and processing incoming events.
     */
    PERCEIVING("Agent is processing incoming events"),
    /**
     * Agent is reasoning about the task using LLM.
     */
    THINKING("Agent is reasoning about the task"),
    /**
     * Agent is executing an action (tool call).
     */
    ACTING("Agent is executing an action"),
    /**
     * Agent is observing and recording the outcome.
     */
    OBSERVING("Agent is recording the outcome"),
    /**
     * Agent is waiting for external input (HITL approval, human response).
     */
    WAITING("Agent is waiting for external input"),
    /**
     * Agent execution is paused by operator.
     */
    PAUSED("Agent execution is paused"),
    /**
     * Agent has encountered an error.
     */
    ERROR("Agent encountered an error"),
    /**
     * Agent has been stopped.
     */
    STOPPED("Agent has been stopped");

    private final String description;

    AgentState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if the agent can accept new events in this state.
     *
     * @return true if events can be queued
     */
    public boolean canAcceptEvents() {
        return this == IDLE || this == WAITING;
    }

    /**
     * Checks if the agent is actively processing.
     *
     * @return true if the agent is working
     */
    public boolean isActive() {
        return this == PERCEIVING || this == THINKING || this == ACTING || this == OBSERVING;
    }

    /**
     * Checks if the agent can be resumed.
     *
     * @return true if the agent can be resumed
     */
    public boolean canResume() {
        return this == PAUSED || this == WAITING;
    }

    /**
     * Checks if the agent needs attention (error or waiting).
     *
     * @return true if human attention may be needed
     */
    public boolean needsAttention() {
        return this == ERROR || this == WAITING;
    }
}
