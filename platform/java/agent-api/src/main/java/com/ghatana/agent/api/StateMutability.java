/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

/**
 * How an agent manages mutable state.
 *
 * @doc.type enum
 * @doc.purpose State mutability classification
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum StateMutability {

    /** Purely functional — no mutable state. */
    STATELESS,

    /** Local in-memory state (e.g., sliding windows). Lost on restart unless checkpointed. */
    LOCAL_STATE,

    /** Externally-persisted state (Redis, database). Survives restarts. */
    EXTERNAL_STATE,

    /** Both local and external state. Requires coordinated checkpoint/recovery. */
    HYBRID_STATE
}
