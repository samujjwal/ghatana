/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/**
 * SAST request DTO.
 *
 * @doc.type record
 * @doc.purpose Request to run static analysis security testing
 * @doc.layer product
 * @doc.pattern DTO
 */
public record SASTRequest(String projectPath, String ruleSet) {}
