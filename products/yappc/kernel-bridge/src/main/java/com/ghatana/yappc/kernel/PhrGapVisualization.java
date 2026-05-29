package com.ghatana.yappc.kernel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * G10-007: PHR Gap Visualization
 *
 * Provides visualization of PHR implementation gaps including:
 * - Stable implemented routes
 * - Stable missing API
 * - Hidden routes
 * - Blocked routes
 * - Preview routes
 * - Test missing
 *
 * @doc.type class
 * @doc.purpose Visualize PHR implementation gaps
 * @doc.layer integration
 * @doc.pattern Visualization
 */
public class PhrGapVisualization {

    private String productId;
    private String version;
    private List<GapVisualizationItem> items;

    public PhrGapVisualization() {
        this.items = new ArrayList<>();
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public List<GapVisualizationItem> getItems() { return items; }
    public void setItems(List<GapVisualizationItem> items) { this.items = items; }

    /**
     * Add a gap visualization item
     */
    public void addItem(GapVisualizationItem item) {
        items.add(item);
    }

    /**
     * Generate visualization report
     */
    public String generateVisualizationReport() {
        StringBuilder report = new StringBuilder();
        report.append("PHR Gap Visualization Report\n");
        report.append("=============================\n");
        report.append("Product: ").append(productId).append("\n");
        report.append("Version: ").append(version).append("\n");
        report.append("Total Items: ").append(items.size()).append("\n\n");

        // Group by status
        Map<String, List<GapVisualizationItem>> grouped = new HashMap<>();
        for (GapVisualizationItem item : items) {
            grouped.computeIfAbsent(item.getStatus(), k -> new ArrayList<>()).add(item);
        }

        // Report each status
        for (Map.Entry<String, List<GapVisualizationItem>> entry : grouped.entrySet()) {
            report.append("## ").append(entry.getKey()).append(" (").append(entry.getValue().size()).append(")\n");
            for (GapVisualizationItem item : entry.getValue()) {
                report.append("  - ").append(item.getRouteId())
                      .append(" (").append(item.getRoutePath()).append(")")
                      .append(": ").append(item.getDescription())
                      .append("\n");
            }
            report.append("\n");
        }

        return report.toString();
    }

    /**
     * Generate markdown table for visualization
     */
    public String generateMarkdownTable() {
        StringBuilder table = new StringBuilder();
        table.append("| Route ID | Path | Status | Description |\n");
        table.append("|----------|------|--------|-------------|\n");

        for (GapVisualizationItem item : items) {
            table.append("| ").append(item.getRouteId())
                  .append(" | ").append(item.getRoutePath())
                  .append(" | ").append(item.getStatus())
                  .append(" | ").append(item.getDescription())
                  .append(" |\n");
        }

        return table.toString();
    }

    /**
     * Get summary statistics
     */
    public Map<String, Integer> getSummary() {
        Map<String, Integer> summary = new HashMap<>();
        for (GapVisualizationItem item : items) {
            summary.merge(item.getStatus(), 1, Integer::sum);
        }
        return summary;
    }

    /**
     * Gap Visualization Item model
     */
    public static class GapVisualizationItem {
        private String routeId;
        private String routePath;
        private String status;
        private String description;
        private String component;
        private List<String> missingItems;

        public String getRouteId() { return routeId; }
        public void setRouteId(String routeId) { this.routeId = routeId; }

        public String getRoutePath() { return routePath; }
        public void setRoutePath(String routePath) { this.routePath = routePath; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }

        public List<String> getMissingItems() { return missingItems; }
        public void setMissingItems(List<String> missingItems) { this.missingItems = missingItems; }
    }

    /**
     * Gap Status enum
     */
    public enum GapStatus {
        STABLE_IMPLEMENTED("Stable Implemented"),
        STABLE_MISSING_API("Stable Missing API"),
        HIDDEN("Hidden"),
        BLOCKED("Blocked"),
        PREVIEW("Preview"),
        TEST_MISSING("Test Missing");

        private final String displayName;

        GapStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }
}
