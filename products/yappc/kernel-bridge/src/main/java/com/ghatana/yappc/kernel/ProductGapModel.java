package com.ghatana.yappc.kernel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * G10-002: Product Product Gap Model
 *
 * Extends YAPPC product model to support Product-specific gap tracking
 * for routes, pages, APIs, tests, and policies.
 *
 * @doc.type class
 * @doc.purpose Model Product product gaps for route/page/API/test/policy
 * @doc.layer integration
 * @doc.pattern Domain Model
 */
public class ProductGapModel {

    private String productId;
    private String version;
    private List<RouteGap> routeGaps;
    private List<PageGap> pageGaps;
    private List<ApiGap> apiGaps;
    private List<TestGap> testGaps;
    private List<PolicyGap> policyGaps;

    public ProductGapModel() {
        this.routeGaps = new ArrayList<>();
        this.pageGaps = new ArrayList<>();
        this.apiGaps = new ArrayList<>();
        this.testGaps = new ArrayList<>();
        this.policyGaps = new ArrayList<>();
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public List<RouteGap> getRouteGaps() { return routeGaps; }
    public void setRouteGaps(List<RouteGap> routeGaps) { this.routeGaps = routeGaps; }

    public List<PageGap> getPageGaps() { return pageGaps; }
    public void setPageGaps(List<PageGap> pageGaps) { this.pageGaps = pageGaps; }

    public List<ApiGap> getApiGaps() { return apiGaps; }
    public void setApiGaps(List<ApiGap> apiGaps) { this.apiGaps = apiGaps; }

    public List<TestGap> getTestGaps() { return testGaps; }
    public void setTestGaps(List<TestGap> testGaps) { this.testGaps = testGaps; }

    public List<PolicyGap> getPolicyGaps() { return policyGaps; }
    public void setPolicyGaps(List<PolicyGap> policyGaps) { this.policyGaps = policyGaps; }

    /**
     * Calculate total gap count
     */
    public int getTotalGapCount() {
        return routeGaps.size() + pageGaps.size() + apiGaps.size() + testGaps.size() + policyGaps.size();
    }

    /**
     * Get gap summary by type
     */
    public Map<String, Integer> getGapSummary() {
        Map<String, Integer> summary = new HashMap<>();
        summary.put("routes", routeGaps.size());
        summary.put("pages", pageGaps.size());
        summary.put("apis", apiGaps.size());
        summary.put("tests", testGaps.size());
        summary.put("policies", policyGaps.size());
        return summary;
    }

    /**
     * Route Gap model
     */
    public static class RouteGap {
        private String routeId;
        private String routePath;
        private GapSeverity severity;
        private String description;
        private String missingComponent;
        private List<String> requiredRoles;

        public String getRouteId() { return routeId; }
        public void setRouteId(String routeId) { this.routeId = routeId; }

        public String getRoutePath() { return routePath; }
        public void setRoutePath(String routePath) { this.routePath = routePath; }

        public GapSeverity getSeverity() { return severity; }
        public void setSeverity(GapSeverity severity) { this.severity = severity; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getMissingComponent() { return missingComponent; }
        public void setMissingComponent(String missingComponent) { this.missingComponent = missingComponent; }

        public List<String> getRequiredRoles() { return requiredRoles; }
        public void setRequiredRoles(List<String> requiredRoles) { this.requiredRoles = requiredRoles; }
    }

    /**
     * Page Gap model
     */
    public static class PageGap {
        private String pageId;
        private String routeId;
        private GapSeverity severity;
        private String description;
        private String missingScreen;
        private List<String> missingFeatures;

        public String getPageId() { return pageId; }
        public void setPageId(String pageId) { this.pageId = pageId; }

        public String getRouteId() { return routeId; }
        public void setRouteId(String routeId) { this.routeId = routeId; }

        public GapSeverity getSeverity() { return severity; }
        public void setSeverity(GapSeverity severity) { this.severity = severity; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getMissingScreen() { return missingScreen; }
        public void setMissingScreen(String missingScreen) { this.missingScreen = missingScreen; }

        public List<String> getMissingFeatures() { return missingFeatures; }
        public void setMissingFeatures(List<String> missingFeatures) { this.missingFeatures = missingFeatures; }
    }

    /**
     * API Gap model
     */
    public static class ApiGap {
        private String apiId;
        private String endpoint;
        private String method;
        private GapSeverity severity;
        private String description;
        private String missingHandler;
        private List<String> missingValidations;

        public String getApiId() { return apiId; }
        public void setApiId(String apiId) { this.apiId = apiId; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public GapSeverity getSeverity() { return severity; }
        public void setSeverity(GapSeverity severity) { this.severity = severity; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getMissingHandler() { return missingHandler; }
        public void setMissingHandler(String missingHandler) { this.missingHandler = missingHandler; }

        public List<String> getMissingValidations() { return missingValidations; }
        public void setMissingValidations(List<String> missingValidations) { this.missingValidations = missingValidations; }
    }

    /**
     * Test Gap model
     */
    public static class TestGap {
        private String testId;
        private String targetComponent;
        private GapSeverity severity;
        private String description;
        private String missingTestType;
        private List<String> missingScenarios;

        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }

        public String getTargetComponent() { return targetComponent; }
        public void setTargetComponent(String targetComponent) { this.targetComponent = targetComponent; }

        public GapSeverity getSeverity() { return severity; }
        public void setSeverity(GapSeverity severity) { this.severity = severity; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getMissingTestType() { return missingTestType; }
        public void setMissingTestType(String missingTestType) { this.missingTestType = missingTestType; }

        public List<String> getMissingScenarios() { return missingScenarios; }
        public void setMissingScenarios(List<String> missingScenarios) { this.missingScenarios = missingScenarios; }
    }

    /**
     * Policy Gap model
     */
    public static class PolicyGap {
        private String policyId;
        private String policyType;
        private GapSeverity severity;
        private String description;
        private String missingPolicyRule;
        private List<String> affectedRoutes;

        public String getPolicyId() { return policyId; }
        public void setPolicyId(String policyId) { this.policyId = policyId; }

        public String getPolicyType() { return policyType; }
        public void setPolicyType(String policyType) { this.policyType = policyType; }

        public GapSeverity getSeverity() { return severity; }
        public void setSeverity(GapSeverity severity) { this.severity = severity; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getMissingPolicyRule() { return missingPolicyRule; }
        public void setMissingPolicyRule(String missingPolicyRule) { this.missingPolicyRule = missingPolicyRule; }

        public List<String> getAffectedRoutes() { return affectedRoutes; }
        public void setAffectedRoutes(List<String> affectedRoutes) { this.affectedRoutes = affectedRoutes; }
    }

    /**
     * Gap Severity enum
     */
    public enum GapSeverity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO
    }
}
