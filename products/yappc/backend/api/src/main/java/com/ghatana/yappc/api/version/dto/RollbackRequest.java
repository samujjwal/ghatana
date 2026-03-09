/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.version.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for rollback operation.
 *
 * @doc.type record
 * @doc.purpose Rollback request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record RollbackRequest(
    @Min(1) @JsonProperty("targetVersion") int targetVersion,
    @NotBlank @JsonProperty("authorId") String authorId,
    @JsonProperty("authorName") String authorName,
    @NotBlank @JsonProperty("reason") String reason,
    @JsonProperty("skipApproval") boolean skipApproval) {}
