/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.dto.report;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Request DTO for report generation.
 *
 * @doc.type class
 * @doc.purpose Request DTO for report generation
 * @doc.layer product
 * @doc.pattern DTO, Request
 */
@Data
@Builder
public class GenerateReportRequest {
    private String name;
    private String query;
    private String format;
    private boolean cache;

    public GenerateReportRequest(String name, String query, String format, boolean cache) {
        this.name = name;
        this.query = query;
        this.format = format != null ? format : "CSV";
        this.cache = cache;
    }
}
