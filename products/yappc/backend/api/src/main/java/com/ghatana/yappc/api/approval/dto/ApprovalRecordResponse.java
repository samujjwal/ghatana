/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for approval record.
 *
 * @doc.type record
 * @doc.purpose Approval record response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record ApprovalRecordResponse(
    @JsonProperty("id") String id,
    @JsonProperty("stageIndex") int stageIndex,
    @JsonProperty("stageName") String stageName,
    @JsonProperty("approver") String approver,
    @JsonProperty("decision") String decision,
    @JsonProperty("comments") String comments,
    @JsonProperty("timestamp") String timestamp) {}
