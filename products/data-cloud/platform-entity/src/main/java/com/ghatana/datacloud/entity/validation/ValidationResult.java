/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.validation;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of entity schema validation.
 *
 * <p>Three terminal states:
 * <ul>
 *   <li>{@code SUCCESS} — all rules passed.</li>
 *   <li>{@code FAILURE} — one or more violations found; call {@link #violations()} for details.</li>
 *   <li>{@code UNREGISTERED} — no schema registered for the collection; consider the entity valid.</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Immutable schema validation result carrying violations list
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record ValidationResult(
        State state,
        List<String> violations
) {

    /** Outcome of a validation run. */
    public enum State {
        SUCCESS,
        FAILURE,
        UNREGISTERED
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Returns a successful result with no violations.
     *
     * @return valid result
     */
    public static ValidationResult success() {
        return new ValidationResult(State.SUCCESS, Collections.emptyList());
    }

    /**
     * Returns a failure result with the supplied violations.
     *
     * @param violations non-empty list of violation messages
     * @return invalid result
     */
    public static ValidationResult failure(List<String> violations) {
        return new ValidationResult(State.FAILURE, Collections.unmodifiableList(violations));
    }

    /**
     * Returns an unregistered result — no schema was registered for the
     * collection, so validation cannot be performed.
     *
     * @return unregistered result (treated as valid by the API layer)
     */
    public static ValidationResult unregistered() {
        return new ValidationResult(State.UNREGISTERED, Collections.emptyList());
    }

    // -------------------------------------------------------------------------
    // Convenience
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} for {@code SUCCESS} and {@code UNREGISTERED} states.
     *
     * @return whether the entity should be accepted
     */
    public boolean valid() {
        return state != State.FAILURE;
    }

    /**
     * Returns a human-readable summary of all violations.
     *
     * @return violation summary, or empty string if valid
     */
    public String violationSummary() {
        if (violations.isEmpty()) {
            return "";
        }
        return String.join("; ", violations);
    }
}
