package com.ghatana.yappc.kernel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * G10-009: PHR Backlog Export
 *
 * Exports PHR backlog grouped by verification pass for tracking
 * implementation progress across different verification stages.
 *
 * @doc.type class
 * @doc.purpose Export PHR backlog grouped by verification pass
 * @doc.layer integration
 * @doc.pattern Export
 */
public class PhrBacklogExport {

    private String productId;
    private String version;
    private String exportDate;
    private Map<String, List<BacklogItem>> backlogByPass;

    public PhrBacklogExport() {
        this.backlogByPass = new HashMap<>();
        this.exportDate = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new java.util.Date());
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getExportDate() { return exportDate; }
    public void setExportDate(String exportDate) { this.exportDate = exportDate; }

    public Map<String, List<BacklogItem>> getBacklogByPass() { return backlogByPass; }
    public void setBacklogByPass(Map<String, List<BacklogItem>> backlogByPass) { this.backlogByPass = backlogByPass; }

    /**
     * Add a backlog item to a specific verification pass
     */
    public void addItem(String verificationPass, BacklogItem item) {
        backlogByPass.computeIfAbsent(verificationPass, k -> new ArrayList<>()).add(item);
    }

    /**
     * Generate markdown export
     */
    public String generateMarkdownExport() {
        StringBuilder md = new StringBuilder();
        md.append("# PHR Backlog Export\n\n");
        md.append("**Product:** ").append(productId).append("\n");
        md.append("**Version:** ").append(version).append("\n");
        md.append("**Export Date:** ").append(exportDate).append("\n\n");

        for (Map.Entry<String, List<BacklogItem>> entry : backlogByPass.entrySet()) {
            md.append("## ").append(entry.getKey()).append("\n\n");
            md.append("| ID | Title | Status | Priority |\n");
            md.append("|----|-------|--------|----------|\n");

            for (BacklogItem item : entry.getValue()) {
                md.append("| ").append(item.getId())
                      .append(" | ").append(item.getTitle())
                      .append(" | ").append(item.getStatus())
                      .append(" | ").append(item.getPriority())
                      .append(" |\n");
            }
            md.append("\n");
        }

        return md.toString();
    }

    /**
     * Generate JSON export
     */
    public String generateJsonExport() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"productId\": \"").append(productId).append("\",\n");
        json.append("  \"version\": \"").append(version).append("\",\n");
        json.append("  \"exportDate\": \"").append(exportDate).append("\",\n");
        json.append("  \"backlogByPass\": {\n");

        boolean firstPass = true;
        for (Map.Entry<String, List<BacklogItem>> entry : backlogByPass.entrySet()) {
            if (!firstPass) json.append(",\n");
            firstPass = false;
            json.append("    \"").append(entry.getKey()).append("\": [\n");

            boolean firstItem = true;
            for (BacklogItem item : entry.getValue()) {
                if (!firstItem) json.append(",\n");
                firstItem = false;
                json.append("      {\n");
                json.append("        \"id\": \"").append(item.getId()).append("\",\n");
                json.append("        \"title\": \"").append(item.getTitle()).append("\",\n");
                json.append("        \"status\": \"").append(item.getStatus()).append("\",\n");
                json.append("        \"priority\": \"").append(item.getPriority()).append("\",\n");
                json.append("        \"description\": \"").append(item.getDescription()).append("\"\n");
                json.append("      }");
            }
            json.append("\n    ]");
        }

        json.append("\n  }\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * Get summary statistics
     */
    public Map<String, Integer> getSummary() {
        Map<String, Integer> summary = new HashMap<>();
        summary.put("totalItems", backlogByPass.values().stream().mapToInt(List::size).sum());
        summary.put("totalPasses", backlogByPass.size());
        
        for (Map.Entry<String, List<BacklogItem>> entry : backlogByPass.entrySet()) {
            summary.put(entry.getKey() + "_count", entry.getValue().size());
        }
        
        return summary;
    }

    /**
     * Backlog Item model
     */
    public static class BacklogItem {
        private String id;
        private String title;
        private String status;
        private String priority;
        private String description;
        private String assignee;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getAssignee() { return assignee; }
        public void setAssignee(String assignee) { this.assignee = assignee; }
    }

    /**
     * Verification Pass enum
     */
    public enum VerificationPass {
        V1("Verification Pass V1"),
        V2("Verification Pass V2"),
        V3("Verification Pass V3"),
        V4("Verification Pass V4"),
        V5("Verification Pass V5"),
        V6("Verification Pass V6");

        private final String displayName;

        VerificationPass(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }
}
