/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.requirements.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.yappc.api.requirements.dto.CreateRequirementRequest.Priority;

/**
 * Request DTO for updating a requirement.
 *
 * @doc.type record
 * @doc.purpose Requirement update request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record UpdateRequirementRequest(
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("priority") Priority priority) {}
