/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

import java.util.Date;
import java.util.List;

/**
 * Security scan result DTO.
 *
 * @doc.type record
 * @doc.purpose Represents the outcome of a security scan
 * @doc.layer product
 * @doc.pattern DTO
 */
public record SecurityScanResult(List<SecurityFinding> findings, SecurityScore score, Date scanDate) {}
