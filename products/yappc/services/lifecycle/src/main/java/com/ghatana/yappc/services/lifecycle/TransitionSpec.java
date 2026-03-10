/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Immutable specification of a single allowed lifecycle phase transition, loaded from
 * {@code config/lifecycle/transitions.yaml}.
 *
 * <p>Each {@code TransitionSpec} declares the two phases involved, the transition type
 * (forward/backward), and the list of artifact IDs that must be present before the
 * transition is permitted.
 *
 * @doc.type class
 * @doc.purpose Data model for a lifecycle transition rule loaded from YAML
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TransitionSpec {

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;

    @JsonProperty("type")
    private String type; // "forward" | "backward"

    @JsonProperty("description")
    private String description;

    @JsonProperty("required_artifacts")
    private List<String> requiredArtifacts;

    /** Required by Jackson. */
    public TransitionSpec() {}

    public String getFrom()         { return from; }
    public String getTo()           { return to; }
    public String getType()         { return type; }
    public String getDescription()  { return description; }

    public List<String> getRequiredArtifacts() {
        return requiredArtifacts == null ? List.of() : List.copyOf(requiredArtifacts);
    }

    /** {@code true} iff this transition matches the requested from/to phases (case-insensitive). */
    public boolean matches(String fromPhase, String toPhase) {
        return from != null && to != null
            && from.equalsIgnoreCase(fromPhase)
            && to.equalsIgnoreCase(toPhase);
    }

    @Override
    public String toString() {
        return "TransitionSpec{" + from + " → " + to + ", type=" + type + '}';
    }
}
