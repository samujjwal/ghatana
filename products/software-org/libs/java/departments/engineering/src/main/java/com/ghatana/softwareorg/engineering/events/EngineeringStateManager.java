package com.ghatana.softwareorg.engineering.events;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.ghatana.softwareorg.engineering.domain.FeatureRequest;
import com.ghatana.softwareorg.engineering.domain.CodeReview;
import com.ghatana.softwareorg.engineering.domain.BuildResult;

/**
 * Manages engineering department state (features, commits, builds, reviews).
 * Thread-safe storage for domain objects with tenant isolation.
 */
public class EngineeringStateManager {

    private final Map<String, Map<String, FeatureRequest>> featuresByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, BuildResult>> buildsByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CodeReview>> reviewsByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> commitsByTenant
            = new ConcurrentHashMap<>();

    public void recordFeature(String tenantId, FeatureRequest feature) {
        featuresByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(feature.getId(), feature);
    }

    public FeatureRequest getFeature(String tenantId, String featureId) {
        return featuresByTenant.getOrDefault(tenantId, new ConcurrentHashMap<>())
                .get(featureId);
    }

    public void recordBuild(String tenantId, BuildResult build) {
        buildsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(build.getId(), build);
    }

    public BuildResult getBuild(String tenantId, String buildId) {
        return buildsByTenant.getOrDefault(tenantId, new ConcurrentHashMap<>())
                .get(buildId);
    }

    public void recordCodeReview(String tenantId, CodeReview review) {
        reviewsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(review.getPrId(), review);
    }

    public CodeReview getCodeReview(String tenantId, String prId) {
        return reviewsByTenant.getOrDefault(tenantId, new ConcurrentHashMap<>())
                .get(prId);
    }

    public void recordCommit(String tenantId, String commitId, int filesChanged,
            int linesAdded, int linesDeleted) {
        Map<String, Object> commitData = new HashMap<>();
        commitData.put("commitId", commitId);
        commitData.put("filesChanged", filesChanged);
        commitData.put("linesAdded", linesAdded);
        commitData.put("linesDeleted", linesDeleted);
        commitData.put("timestamp", System.currentTimeMillis());

        commitsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(commitId, commitData);
    }

    public void recordQualityGate(String tenantId, String featureId, boolean passed,
            double coverage, double testPassRate) {
        Map<String, Object> gateData = new HashMap<>();
        gateData.put("featureId", featureId);
        gateData.put("passed", passed);
        gateData.put("coverage", coverage);
        gateData.put("testPassRate", testPassRate);
        gateData.put("timestamp", System.currentTimeMillis());

        String key = "gate_" + featureId;
        featuresByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(key, null); // Store in features map for now
    }
}
