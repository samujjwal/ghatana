package com.ghatana.yappc.core.doctor;

import java.util.List;

/**
 * Describes a tool availability check.
 * @doc.type record
 * @doc.purpose Describes a tool availability check.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record ToolCheck(String name, List<String> command) {
    public ToolCheck {
        command = List.copyOf(command);
    }
}
