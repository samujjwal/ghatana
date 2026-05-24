package com.ghatana.agent.runtime.replay;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable prompt snapshot for replay and audit.
 *
 * @doc.type record
 * @doc.purpose Records prompt hashes and template version used for one agent execution
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record AgentPromptSnapshot(
        String promptHash,
        String systemPromptHash,
        String templateVersion,
        String renderedPromptHash,
        Map<String, String> variables
) {

    public AgentPromptSnapshot {
        requireNonBlank(promptHash, "promptHash");
        requireNonBlank(templateVersion, "templateVersion");
        requireNonBlank(renderedPromptHash, "renderedPromptHash");
        systemPromptHash = systemPromptHash == null ? "" : systemPromptHash;
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    private static void requireNonBlank(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
