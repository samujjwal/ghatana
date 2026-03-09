/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

import java.util.List;

/**
 * TestGenerationResult.
 *
 * @doc.type record
 * @doc.purpose test generation result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestGenerationResult(
        List<GeneratedTest> generatedTests,
        TestStatistics statistics,
        String outputPath
) {}
