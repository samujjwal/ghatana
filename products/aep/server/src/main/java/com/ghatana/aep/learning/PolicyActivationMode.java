/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.learning;

/**
 * Activation modes for a learned policy — controls how and how broadly a promoted
 * policy is applied to live traffic.
 *
 * <p>The lifecycle of a learned policy follows:
 * <pre>
 *   SHADOW → CANARY → ACTIVE → DEPRECATED
 *            ↓
 *       (rollback)
 * </pre>
 *
 * <ul>
 *   <li>{@link #SHADOW} — policy is evaluated on every match but its decisions are
 *       not applied to runtime behavior; used to validate correctness before any
 *       real impact.</li>
 *   <li>{@link #CANARY} — policy is applied to a configurable fraction of matching
 *       traffic; success/failure metrics are compared against the baseline policy in
 *       the remaining traffic.</li>
 *   <li>{@link #ACTIVE} — policy is fully promoted and applied to all matching traffic.</li>
 *   <li>{@link #DEPRECATED} — policy has been superseded or rolled back; retained for
 *       audit purposes but no longer applied.</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Policy activation mode for safe graduated rollout of learned policies
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum PolicyActivationMode {

    /**
     * Shadow mode — decisions are computed but not applied.
     * Metrics are still collected so the policy can be evaluated against live traffic.
     */
    SHADOW,

    /**
     * Canary mode — applied to a configured fraction ({@code canaryFraction}) of traffic.
     * The rollback pointer is always set when transitioning from CANARY to ACTIVE.
     */
    CANARY,

    /**
     * Fully active — applied to all matching traffic.
     */
    ACTIVE,

    /**
     * Deprecated — policy has been superseded or rolled back.
     * Retained in the store for provenance and compliance queries.
     */
    DEPRECATED
}
