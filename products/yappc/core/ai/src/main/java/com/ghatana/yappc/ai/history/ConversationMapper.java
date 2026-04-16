/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Conversation Mapper
 */
package com.ghatana.yappc.ai.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Mapper for converting Conversation entities to/from data-cloud entity data.
 *
 * <p>Provides bidirectional mapping between Conversation domain objects and
 * data-cloud entity data maps.
 *
 * @doc.type class
 * @doc.purpose Conversation to data-cloud entity mapping
 * @doc.layer product
 * @doc.pattern Mapper
 */
public class ConversationMapper {

    private final ObjectMapper objectMapper;

    public ConversationMapper(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a Conversation domain object to a data map suitable for data-cloud persistence.
     *
     * @param conversation Source Conversation entity
     * @return field map for data-cloud entity data
     */
    @NotNull
    public Map<String, Object> toEntityData(@NotNull Conversation conversation) {
        return objectMapper.convertValue(conversation, Map.class);
    }

    /**
     * Converts a data-cloud entity data map back to a Conversation domain object.
     *
     * @param data Source entity data from data-cloud
     * @return Conversation domain object instance
     */
    @NotNull
    public Conversation fromEntityData(@NotNull Map<String, Object> data) {
        return objectMapper.convertValue(data, Conversation.class);
    }
}
