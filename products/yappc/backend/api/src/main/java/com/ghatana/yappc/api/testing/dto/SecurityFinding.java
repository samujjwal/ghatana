/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing.dto;

/**
 * Security finding DTO.
 *
 * @doc.type record
 * @doc.purpose Represents a single security finding
 * @doc.layer product
 * @doc.pattern DTO
 */
public record SecurityFinding(
    String id,
    String severity,
    String title,
    String description,
    String filePath,
    int lineNumber,
    String remediation) {

    public SecurityFinding(String id, String severity, String title, String filePath, int lineNumber, String remediation) {
        this(id, severity, title, title, filePath, lineNumber, remediation);
    }
}
