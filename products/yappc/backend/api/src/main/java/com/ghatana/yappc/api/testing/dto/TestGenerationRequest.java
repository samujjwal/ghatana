/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/**
 * Test Generation Request DTO.
 * 
 * @param sourcePath Path to source code file
 * @param testType Type of tests (unit, integration, all)
 * @param framework Test framework (junit, jest, pytest)
 * @param outputPath Output path for generated tests
 * @param coverageTarget Target coverage percentage
  *
 * @doc.type record
 * @doc.purpose test generation request
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TestGenerationRequest(
        String sourcePath,
        String testType,
        String framework,
        String outputPath,
        Integer coverageTarget
) {}
