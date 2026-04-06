package com.ghatana.kernel.descriptor;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Contains build information for kernel components.
 *
 * <p>Build information includes version control metadata, build timestamps,
 * CI/CD information, and artifact details.</p>
 *
 * @doc.type class
 * @doc.purpose Build information and metadata for kernel components
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class BuildInformation {
    
    private final String buildId;
    private final String commitHash;
    private final String branch;
    private final Instant buildTimestamp;
    private final String builtBy;
    private final String ciPipelineId;
    private final String artifactUrl;
    private final Map<String, String> metadata;

    private BuildInformation(Builder builder) {
        this.buildId = builder.buildId;
        this.commitHash = builder.commitHash;
        this.branch = builder.branch;
        this.buildTimestamp = builder.buildTimestamp != null ? builder.buildTimestamp : Instant.now();
        this.builtBy = builder.builtBy;
        this.ciPipelineId = builder.ciPipelineId;
        this.artifactUrl = builder.artifactUrl;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    // Getters
    public String getBuildId() { return buildId; }
    public String getCommitHash() { return commitHash; }
    public String getBranch() { return branch; }
    public Instant getBuildTimestamp() { return buildTimestamp; }
    public String getBuiltBy() { return builtBy; }
    public String getCiPipelineId() { return ciPipelineId; }
    public String getArtifactUrl() { return artifactUrl; }
    public Map<String, String> getMetadata() { return metadata; }

    // Builder
    public static class Builder {
        private String buildId;
        private String commitHash;
        private String branch;
        private Instant buildTimestamp;
        private String builtBy;
        private String ciPipelineId;
        private String artifactUrl;
        private Map<String, String> metadata = new HashMap<>();

        public Builder withBuildId(String buildId) {
            this.buildId = buildId;
            return this;
        }

        public Builder withCommitHash(String commitHash) {
            this.commitHash = commitHash;
            return this;
        }

        public Builder withBranch(String branch) {
            this.branch = branch;
            return this;
        }

        public Builder withBuildTimestamp(Instant timestamp) {
            this.buildTimestamp = timestamp;
            return this;
        }

        public Builder withBuiltBy(String builtBy) {
            this.builtBy = builtBy;
            return this;
        }

        public Builder withCiPipelineId(String pipelineId) {
            this.ciPipelineId = pipelineId;
            return this;
        }

        public Builder withArtifactUrl(String url) {
            this.artifactUrl = url;
            return this;
        }

        public Builder withMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public BuildInformation build() {
            return new BuildInformation(this);
        }
    }
}
