/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for stage information.
 *
 * @doc.type record
 * @doc.purpose Stage response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record StageResponse(
    @JsonProperty("name") String name,
    @JsonProperty("approvers") List<String> approvers,
    @JsonProperty("approverPersonas") List<String> approverPersonas,
    @JsonProperty("requiredApprovals") int requiredApprovals,
    @JsonProperty("parallel") boolean parallel) {}
