package com.ghatana.softwareorg.engineering.domain;

/**
 * Represents a build result in the engineering department.
 *
 * @doc.type class
 * @doc.purpose Build result domain model
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class BuildResult {

    private final String buildId;
    private final String featureId;
    private final boolean success;
    private final long durationMs;

    public BuildResult(String buildId, String featureId, boolean success, long durationMs) {
        this.buildId = buildId;
        this.featureId = featureId;
        this.success = success;
        this.durationMs = durationMs;
    }

    public String getBuildId() {
        return buildId;
    }

    public String getId() {
        return buildId;
    }

    public String getFeatureId() {
        return featureId;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
