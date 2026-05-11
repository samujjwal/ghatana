/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StreamProcessorAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();
        if (!metadata.containsKey("checkpointing")) {
            errors.add("[type:stream_processor] checkpointing is required");
        }
        if (!metadata.containsKey("windowing")) {
            errors.add("[type:stream_processor] windowing semantics are required");
        }
        if (!metadata.containsKey("backpressure")) {
            errors.add("[type:stream_processor] backpressure semantics are required");
        }
        if (!metadata.containsKey("stateSemantics")) {
            errors.add("[type:stream_processor] state semantics are required");
        }
        return errors;
    }
}
