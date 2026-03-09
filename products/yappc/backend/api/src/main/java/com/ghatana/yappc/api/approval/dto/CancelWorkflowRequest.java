/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for cancelling a workflow.
 *
 * @doc.type record
 * @doc.purpose Cancel workflow request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record CancelWorkflowRequest(@JsonProperty("reason") String reason) {}
