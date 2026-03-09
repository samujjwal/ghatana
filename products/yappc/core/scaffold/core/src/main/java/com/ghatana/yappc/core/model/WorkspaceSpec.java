package com.ghatana.yappc.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Minimal representation of a workspace specification understood by the scaffold system.
 * @doc.type class
 * @doc.purpose Minimal representation of a workspace specification understood by the scaffold system.
 * @doc.layer platform
 * @doc.pattern Specification
 */
public final class WorkspaceSpec {
    private final String name;
    private final String mode;

    @JsonCreator
    public WorkspaceSpec(
            @JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "mode", required = true) String mode) {
        this.name = Objects.requireNonNull(name, "name");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public static WorkspaceSpec defaultMonorepo(String name) {
        return new WorkspaceSpec(name, "monorepo");
    }

    public String getName() {
        return name;
    }

    public String getMode() {
        return mode;
    }
}
