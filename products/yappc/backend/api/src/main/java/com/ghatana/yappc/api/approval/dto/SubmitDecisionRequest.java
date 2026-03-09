/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for submitting an approval decision.
 *
 * @doc.type record
 * @doc.purpose Submit approval decision request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record SubmitDecisionRequest(
    @NotBlank @JsonProperty("decision") String decision, // "APPROVE" or "REJECT"
    @JsonProperty("comments") String comments) {}
