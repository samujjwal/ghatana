/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/**
 * Test Template DTO.
 *
 * @param id Template ID
 * @param name Template name
 * @param framework Test framework
 * @param description Template description
 *
 * @doc.type record
 * @doc.purpose Test template DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public record TestTemplate(
        String id,
        String name,
        String framework,
        String description
) {}
