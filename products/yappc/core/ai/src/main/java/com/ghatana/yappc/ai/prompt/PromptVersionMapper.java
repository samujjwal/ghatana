/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Prompt Version Mapper
 */
package com.ghatana.yappc.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Mapper for converting PromptVersion entities to/from data-cloud entity data.
 *
 * <p>Provides bidirectional mapping between PromptVersion domain objects and
 * data-cloud entity data maps.
 *
 * @doc.type class
 * @doc.purpose PromptVersion to data-cloud entity mapping
 * @doc.layer product
 * @doc.pattern Mapper
 */
public class PromptVersionMapper {

    private final ObjectMapper objectMapper;

    public PromptVersionMapper(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a PromptVersion domain object to a data map suitable for data-cloud persistence.
     *
     * @param promptVersion Source PromptVersion entity
     * @return field map for data-cloud entity data
     */
    @NotNull
    public Map<String, Object> toEntityData(@NotNull PromptVersion promptVersion) {
        return objectMapper.convertValue(promptVersion, Map.class);
    }

    /**
     * Converts a data-cloud entity data map back to a PromptVersion domain object.
     *
     * @param data Source entity data from data-cloud
     * @return PromptVersion domain object instance
     */
    @NotNull
    public PromptVersion fromEntityData(@NotNull Map<String, Object> data) {
        return objectMapper.convertValue(data, PromptVersion.class);
    }
}
