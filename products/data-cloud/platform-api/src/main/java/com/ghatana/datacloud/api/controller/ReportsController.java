/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.analytics.Report;
import com.ghatana.datacloud.analytics.ReportCacheService;
import com.ghatana.datacloud.analytics.ReportService;
import com.ghatana.datacloud.api.dto.report.GenerateReportRequest;
import com.ghatana.datacloud.api.dto.report.ReportResponse;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.http.AsyncServlet;

import java.util.List;
import java.util.Map;

/**
 * REST controller for report generation and retrieval.
 *
 * <p>Handles HTTP requests for:
 * <ul>
 *   <li>POST /api/v1/reports/generate - Generate new report</li>
 *   <li>GET /api/v1/reports - List reports</li>
 *   <li>GET /api/v1/reports/{id} - Get report by ID</li>
 *   <li>GET /api/v1/reports/{id}/download - Download report</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose REST controller for report generation and retrieval
 * @doc.layer product
 * @doc.pattern Controller, REST API
 */
public class ReportsController implements AsyncServlet {

    private final ReportService reportService;
    private final ReportCacheService cacheService;

    public ReportsController(ReportService reportService, ReportCacheService cacheService) {
        this.reportService = reportService;
        this.cacheService = cacheService;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        String path = request.getRelativePath();
        String method = request.getMethod().toString();

        // Route to appropriate handler
        if ("POST".equals(method) && path.endsWith("/generate")) {
            return generateReport(request);
        } else if ("GET".equals(method)) {
            if (path.matches(".*/reports/[^/]+/download")) {
                return downloadReport(request, extractId(path));
            } else if (path.matches(".*/reports/[^/]+")) {
                return getReport(request, extractId(path));
            } else if (path.endsWith("/reports")) {
                return listReports(request);
            }
        } else if ("DELETE".equals(method) && path.matches(".*/reports/[^/]+")) {
            return deleteReport(request, extractId(path));
        }

        return Promise.of(HttpResponse.ofCode(404).withPlainText("Not Found"));
    }

    private Promise<HttpResponse> generateReport(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    GenerateReportRequest req = parseRequest(body.getStringUtf8());
                    Report report = reportService.generate(req.toReport());
                    
                    // Cache the report if configured
                    if (req.cache()) {
                        cacheService.cache(report.getId(), report);
                    }
                    
                    return Promise.of(HttpResponse.ok200()
                        .withJson(toJson(ReportResponse.from(report))));
                } catch (Exception e) {
                    return Promise.of(HttpResponse.ofCode(400)
                        .withPlainText("Invalid request: " + e.getMessage()));
                }
            });
    }

    private Promise<HttpResponse> listReports(HttpRequest request) {
        String tenantId = extractTenantId(request);
        String status = request.getQueryParameter("status");
        
        List<Report> reports = reportService.list(tenantId, status);
        return Promise.of(HttpResponse.ok200()
            .withJson(toJson(Map.of(
                "reports", reports.stream().map(ReportResponse::from).toList(),
                "total", reports.size()
            ))));
    }

    private Promise<HttpResponse> getReport(HttpRequest request, String id) {
        String tenantId = extractTenantId(request);
        
        // Try cache first
        return cacheService.get(id)
            .then(cached -> {
                if (cached != null && cached.getTenantId().equals(tenantId)) {
                    return Promise.of(HttpResponse.ok200()
                        .withJson(toJson(ReportResponse.from(cached))));
                }
                
                // Fall back to service
                return reportService.get(id)
                    .then(report -> {
                        if (report == null || !report.getTenantId().equals(tenantId)) {
                            return Promise.of(HttpResponse.ofCode(404)
                                .withPlainText("Report not found"));
                        }
                        return Promise.of(HttpResponse.ok200()
                            .withJson(toJson(ReportResponse.from(report))));
                    });
            });
    }

    private Promise<HttpResponse> downloadReport(HttpRequest request, String id) {
        String tenantId = extractTenantId(request);
        
        return reportService.get(id)
            .then(report -> {
                if (report == null || !report.getTenantId().equals(tenantId)) {
                    return Promise.of(HttpResponse.ofCode(404)
                        .withPlainText("Report not found"));
                }
                
                byte[] content = reportService.download(id);
                if (content == null) {
                    return Promise.of(HttpResponse.ofCode(404)
                        .withPlainText("Report content not available"));
                }
                
                return Promise.of(HttpResponse.ok200()
                    .withHeader("Content-Type", getContentType(report.getFormat()))
                    .withHeader("Content-Disposition", 
                        "attachment; filename=\"" + report.getName() + "\"")
                    .withBody(content));
            });
    }

    private Promise<HttpResponse> deleteReport(HttpRequest request, String id) {
        String tenantId = extractTenantId(request);
        
        return reportService.get(id)
            .then(report -> {
                if (report == null || !report.getTenantId().equals(tenantId)) {
                    return Promise.of(HttpResponse.ofCode(404)
                        .withPlainText("Report not found"));
                }
                
                reportService.delete(id);
                cacheService.invalidate(id);
                
                return Promise.of(HttpResponse.ok200()
                    .withJson(toJson(Map.of("deleted", true))));
            });
    }

    private String extractId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("reports".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private String extractTenantId(HttpRequest request) {
        String tenantId = request.getHeader("X-Tenant-ID");
        return tenantId != null ? tenantId : "default-tenant";
    }

    private GenerateReportRequest parseRequest(String json) {
        // Simple JSON parsing - in production use Jackson
        Map<String, Object> map = parseJson(json);
        return new GenerateReportRequest(
            (String) map.get("name"),
            (String) map.get("query"),
            (String) map.getOrDefault("format", "CSV"),
            map.containsKey("cache") ? (Boolean) map.get("cache") : true
        );
    }

    private Map<String, Object> parseJson(String json) {
        // Simplified - in production use Jackson
        return Map.of();
    }

    private String toJson(Object obj) {
        // Simplified - in production use Jackson
        return "{}";
    }

    private String getContentType(String format) {
        return switch (format.toUpperCase()) {
            case "CSV" -> "text/csv";
            case "PDF" -> "application/pdf";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }
}
