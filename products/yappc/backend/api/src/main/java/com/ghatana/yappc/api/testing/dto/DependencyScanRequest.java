/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/**
 * Dependency scan request DTO.
 *
 * @doc.type record
 * @doc.purpose Request to scan dependency manifests/lockfiles for known CVEs
 * @doc.layer product
 * @doc.pattern DTO
 */
public record DependencyScanRequest(String projectPath, boolean includeDevDependencies) {}
