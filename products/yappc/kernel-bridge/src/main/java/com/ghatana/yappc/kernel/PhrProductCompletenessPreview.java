package com.ghatana.yappc.kernel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * G10-010: PHR Product Completeness Preview
 *
 * Provides completeness preview for web/mobile/backend parity,
 * showing which features are implemented across all platforms.
 *
 * @doc.type class
 * @doc.purpose Preview PHR product completeness for web/mobile/backend parity
 * @doc.layer integration
 * @doc.pattern Preview
 */
public class PhrProductCompletenessPreview {

    private String productId;
    private String version;
    private List<FeatureCompleteness> features;

    public PhrProductCompletenessPreview() {
        this.features = new ArrayList<>();
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public List<FeatureCompleteness> getFeatures() { return features; }
    public void setFeatures(List<FeatureCompleteness> features) { this.features = features; }

    /**
     * Add a feature completeness item
     */
    public void addFeature(FeatureCompleteness feature) {
        features.add(feature);
    }

    /**
     * Generate completeness report
     */
    public String generateCompletenessReport() {
        StringBuilder report = new StringBuilder();
        report.append("PHR Product Completeness Preview\n");
        report.append("================================\n");
        report.append("Product: ").append(productId).append("\n");
        report.append("Version: ").append(version).append("\n");
        report.append("Total Features: ").append(features.size()).append("\n\n");

        // Calculate parity statistics
        int fullyImplemented = 0;
        int partiallyImplemented = 0;
        int notImplemented = 0;

        for (FeatureCompleteness feature : features) {
            if (feature.isWebImplemented() && feature.isMobileImplemented() && feature.isBackendImplemented()) {
                fullyImplemented++;
            } else if (feature.isWebImplemented() || feature.isMobileImplemented() || feature.isBackendImplemented()) {
                partiallyImplemented++;
            } else {
                notImplemented++;
            }
        }

        report.append("Parity Summary:\n");
        report.append("  Fully Implemented: ").append(fullyImplemented).append("\n");
        report.append("  Partially Implemented: ").append(partiallyImplemented).append("\n");
        report.append("  Not Implemented: ").append(notImplemented).append("\n\n");

        // Feature details
        report.append("Feature Details:\n");
        for (FeatureCompleteness feature : features) {
            report.append("  ").append(feature.getFeatureId())
                  .append(" (").append(feature.getFeatureName()).append("):\n");
            report.append("    Web: ").append(feature.isWebImplemented() ? "✓" : "✗")
                  .append(" | Mobile: ").append(feature.isMobileImplemented() ? "✓" : "✗")
                  .append(" | Backend: ").append(feature.isBackendImplemented() ? "✓" : "✗")
                  .append("\n");
        }

        return report.toString();
    }

    /**
     * Generate markdown table for visualization
     */
    public String generateMarkdownTable() {
        StringBuilder table = new StringBuilder();
        table.append("| Feature | Web | Mobile | Backend | Status |\n");
        table.append("|---------|-----|--------|---------|--------|\n");

        for (FeatureCompleteness feature : features) {
            String webStatus = feature.isWebImplemented() ? "✓" : "✗";
            String mobileStatus = feature.isMobileImplemented() ? "✓" : "✗";
            String backendStatus = feature.isBackendImplemented() ? "✓" : "✗";
            String overallStatus = feature.getOverallStatus();

            table.append("| ").append(feature.getFeatureName())
                  .append(" | ").append(webStatus)
                  .append(" | ").append(mobileStatus)
                  .append(" | ").append(backendStatus)
                  .append(" | ").append(overallStatus)
                  .append(" |\n");
        }

        return table.toString();
    }

    /**
     * Get parity percentage
     */
    public double getParityPercentage() {
        if (features.isEmpty()) return 0.0;
        
        int completeCount = 0;
        for (FeatureCompleteness feature : features) {
            if (feature.isWebImplemented() && feature.isMobileImplemented() && feature.isBackendImplemented()) {
                completeCount++;
            }
        }
        
        return (completeCount * 100.0) / features.size();
    }

    /**
     * Feature Completeness model
     */
    public static class FeatureCompleteness {
        private String featureId;
        private String featureName;
        private boolean webImplemented;
        private boolean mobileImplemented;
        private boolean backendImplemented;
        private String notes;

        public String getFeatureId() { return featureId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }

        public String getFeatureName() { return featureName; }
        public void setFeatureName(String featureName) { this.featureName = featureName; }

        public boolean isWebImplemented() { return webImplemented; }
        public void setWebImplemented(boolean webImplemented) { this.webImplemented = webImplemented; }

        public boolean isMobileImplemented() { return mobileImplemented; }
        public void setMobileImplemented(boolean mobileImplemented) { this.mobileImplemented = mobileImplemented; }

        public boolean isBackendImplemented() { return backendImplemented; }
        public void setBackendImplemented(boolean backendImplemented) { this.backendImplemented = backendImplemented; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public String getOverallStatus() {
            if (webImplemented && mobileImplemented && backendImplemented) {
                return "Complete";
            } else if (webImplemented || mobileImplemented || backendImplemented) {
                return "Partial";
            } else {
                return "Missing";
            }
        }
    }
}
