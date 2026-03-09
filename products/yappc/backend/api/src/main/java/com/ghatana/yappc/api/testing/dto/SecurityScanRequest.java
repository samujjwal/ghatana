/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/** Security Scan Request DTO.  *
 * @doc.type record
 * @doc.purpose security scan request
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SecurityScanRequest(String projectPath, String[] scanTypes) {}
