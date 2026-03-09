/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/**
 * CoverageAnalysisRequest.
 *
 * @doc.type record
 * @doc.purpose coverage analysis request
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CoverageAnalysisRequest(
        String projectPath,
        String[] includePatterns,
        String[] excludePatterns
) {}
