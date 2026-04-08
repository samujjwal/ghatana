/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

/**
 * Terminal status codes for an {@link AgentResponse}.
 *
 * @doc.type enum
 * @doc.purpose Agent response status taxonomy for inter-agent protocol
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum AgentResponseStatus {
    /** The recipient processed the message and produced a result. */
    SUCCESS,
    /** The recipient rejected the message (e.g., policy, mode mismatch). */
    REJECTED,
    /** Processing failed due to an internal error in the recipient. */
    ERROR,
    /** The recipient could not find the resource or agent referenced. */
    NOT_FOUND,
    /** The recipient timed out before producing a result. */
    TIMEOUT,
    /** The request was routed to another agent via handoff. */
    HANDED_OFF
}
