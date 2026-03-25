package com.ghatana.yappc.core.graph;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
/**
 * TaskNode component within the YAPPC platform.
 *
 * @doc.type record
 * @doc.purpose TaskNode component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record TaskNode(String adapterId, String taskId, List<String> dependsOn) {
    public TaskNode {
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(taskId, "taskId");
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
