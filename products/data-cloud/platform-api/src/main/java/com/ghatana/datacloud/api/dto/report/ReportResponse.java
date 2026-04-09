/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.dto.report;

import com.ghatana.datacloud.analytics.Report;
import lombok.Builder;
import lombok.Data;


/**
 * Response DTO for report operations.
 *
 * @doc.type class
 * @doc.purpose Response DTO for report operations
 * @doc.layer product
 * @doc.pattern DTO, Response
 */
@Data
@Builder
public class ReportResponse {
    private String id;
    private String name;
    private String format;
    private String status;
    private long createdAt;
    private String tenantId;
    private String downloadUrl;

    public static ReportResponse from(Report report) {
        return ReportResponse.builder()
            .id(report.getId())
            .name(report.getName())
            .format(report.getFormat())
            .status(report.getStatus())
            .createdAt(report.getCreatedAt())
            .tenantId(report.getTenantId())
            .build();
    }
}
