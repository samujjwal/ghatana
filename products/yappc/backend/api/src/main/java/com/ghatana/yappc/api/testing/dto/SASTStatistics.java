/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/**
 * SAST statistics DTO.
 *
 * @doc.type record
 * @doc.purpose Summary counts for a SAST run
 * @doc.layer product
 * @doc.pattern DTO
 */
public record SASTStatistics(int totalFindings, int criticalFindings, int highFindings, int filesScanned) {}
