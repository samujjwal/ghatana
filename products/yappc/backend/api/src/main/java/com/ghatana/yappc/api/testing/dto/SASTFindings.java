/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

import java.util.Date;
import java.util.List;

/**
 * SAST findings DTO.
 *
 * @doc.type record
 * @doc.purpose Findings and statistics produced by a SAST run
 * @doc.layer product
 * @doc.pattern DTO
 */
public record SASTFindings(List<SecurityFinding> findings, SASTStatistics statistics, Date scanDate) {}
