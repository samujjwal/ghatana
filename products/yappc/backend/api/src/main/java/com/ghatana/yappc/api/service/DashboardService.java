/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import io.activej.promise.Promise;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing YAPPC dashboards.
 * 
 * Dashboards are persona-aware views that combine widgets and actions
 * for specific domains and user levels.
 *
 * @doc.type class
 * @doc.purpose Dashboard management and retrieval
 * @doc.layer platform
 * @doc.pattern Service
 */
public class DashboardService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);
    
    // In-memory dashboard cache (will be replaced with DB in Phase 3)
    private final Map<String, Dashboard> dashboardCache = new HashMap<>();
    
    public DashboardService() {
        initializeDefaultDashboards();
    }
    
    private void initializeDefaultDashboards() {
        // Development Dashboard
        dashboardCache.put("development.main", new Dashboard(
            "development.main",
            "development",
            "Development Dashboard",
            Map.of("kind", "grid", "columns", 12, "items", List.of()),
            List.of(
                new Widget("dev-velocity", "metric", "Sprint Velocity", 
                    Map.of("queryRef", "sprint.velocity", "jsonPath", "$.currentVelocity")),
                new Widget("dev-pr-status", "table", "Pull Requests",
                    Map.of("queryRef", "github.prs", "jsonPath", "$.pullRequests"))
            ),
            List.of(
                new Action("pr.create", "Create PR", "code.write",
                    Map.of("presentation", "drawer"), Map.of("kind", "mutation", "operationName", "createPullRequest"))
            ),
            Map.of("levels", List.of(1, 2), "personas", List.of("developer", "tech-lead"))
        ));
        
        // Operations Dashboard
        dashboardCache.put("operations.main", new Dashboard(
            "operations.main",
            "operations",
            "Operations Dashboard",
            Map.of("kind", "grid", "columns", 12, "items", List.of()),
            List.of(
                new Widget("ops-deployment", "metric", "Deployment Status",
                    Map.of("queryRef", "deploy.status", "jsonPath", "$.status")),
                new Widget("ops-sla", "chart", "SLA Compliance",
                    Map.of("queryRef", "sla.metrics", "jsonPath", "$.compliance"))
            ),
            List.of(
                new Action("deploy.trigger", "Deploy", "deploy.execute",
                    Map.of("presentation", "modal"), Map.of("kind", "Job", "operationName", "triggerDeployment"))
            ),
            Map.of("levels", List.of(1, 2, 3), "personas", List.of("devops", "sre"))
        ));
        
        // Security Dashboard
        dashboardCache.put("security.main", new Dashboard(
            "security.main",
            "security",
            "Security Dashboard",
            Map.of("kind", "grid", "columns", 12, "items", List.of()),
            List.of(
                new Widget("sec-vulns", "metric", "Open Vulnerabilities",
                    Map.of("queryRef", "security.vulns", "jsonPath", "$.openCount")),
                new Widget("sec-scan", "table", "Recent Scans",
                    Map.of("queryRef", "security.scans", "jsonPath", "$.recentScans"))
            ),
            List.of(
                new Action("scan.trigger", "Run Scan", "security.scan",
                    Map.of("presentation", "inline"), Map.of("kind", "Job", "operationName", "triggerSecurityScan"))
            ),
            Map.of("levels", List.of(2, 3, 4), "personas", List.of("security-engineer", "ciso"))
        ));
        
        // Quality Dashboard
        dashboardCache.put("quality.main", new Dashboard(
            "quality.main",
            "quality",
            "Quality Dashboard",
            Map.of("kind", "grid", "columns", 12, "items", List.of()),
            List.of(
                new Widget("qa-coverage", "metric", "Test Coverage",
                    Map.of("queryRef", "quality.coverage", "jsonPath", "$.percentage")),
                new Widget("qa-defects", "table", "Open Defects",
                    Map.of("queryRef", "quality.defects", "jsonPath", "$.openDefects"))
            ),
            List.of(
                new Action("test.run", "Run Tests", "test.execute",
                    Map.of("presentation", "inline"), Map.of("kind", "Job", "operationName", "triggerTestRun"))
            ),
            Map.of("levels", List.of(1, 2), "personas", List.of("qa-engineer"))
        ));
        
        // Product Dashboard
        dashboardCache.put("product.main", new Dashboard(
            "product.main",
            "product",
            "Product Dashboard",
            Map.of("kind", "grid", "columns", 12, "items", List.of()),
            List.of(
                new Widget("prod-roadmap", "timeline", "Roadmap Progress",
                    Map.of("queryRef", "product.roadmap", "jsonPath", "$.milestones")),
                new Widget("prod-adoption", "chart", "Feature Adoption",
                    Map.of("queryRef", "product.adoption", "jsonPath", "$.adoptionRates"))
            ),
            List.of(
                new Action("requirement.create", "New Requirement", "requirements.define",
                    Map.of("presentation", "wizard"), Map.of("kind", "Flow", "flowId", "product.requirement"))
            ),
            Map.of("levels", List.of(3, 4), "personas", List.of("product-manager", "product-owner"))
        ));
        
        logger.info("Initialized {} default dashboards", dashboardCache.size());
    }
    
    public Promise<List<Dashboard>> getDashboards() {
        return Promise.of(new ArrayList<>(dashboardCache.values()));
    }
    
    public Promise<List<Dashboard>> getDashboardsByDomain(String domainId) {
        List<Dashboard> filtered = dashboardCache.values().stream()
            .filter(d -> d.domainId().equals(domainId))
            .toList();
        return Promise.of(filtered);
    }
    
    public Promise<Dashboard> getDashboardById(String id) {
        Dashboard dashboard = dashboardCache.get(id);
        if (dashboard == null) {
            return Promise.ofException(new RuntimeException("Dashboard not found: " + id));
        }
        return Promise.of(dashboard);
    }
    
    // Domain Records
    public record Dashboard(
        String id,
        String domainId,
        String title,
        Map<String, Object> layout,
        List<Widget> widgets,
        List<Action> actions,
        Map<String, Object> audience
    ) {}
    
    public record Widget(
        String id,
        String type,
        String title,
        Map<String, Object> dataBinding
    ) {}
    
    public record Action(
        String id,
        String label,
        String permission,
        Map<String, Object> ui,
        Map<String, Object> execution
    ) {}
}
