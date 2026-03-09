/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/**
 * Security score DTO.
 *
 * @doc.type record
 * @doc.purpose Aggregated score/grade for a security scan
 * @doc.layer product
 * @doc.pattern DTO
 */
public record SecurityScore(int score, String grade, int totalFindings) {}
