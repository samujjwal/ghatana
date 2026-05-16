package com.ghatana.yappc.domain.source;

import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Canonical source locator with provider, repoId, ref, path, and credential metadata.
 *              Represents a pointer to a source repository that can be resolved into a snapshot.
 * @doc.layer domain
 * @doc.pattern ValueObject
 *
 * P0: Java canonical source locator with provider, repoId, ref, path, credentialRef,
 *      tenant/workspace/project scope metadata.
 */
public final class SourceLocator {

    private final String provider;
    private final String repoId;
    private final String ref;
    private final String path;
    private final String credentialRef;
    private final String tenantId;
    private final String workspaceId;
    private final String projectId;

    private SourceLocator(Builder builder) {
        this.provider = Objects.requireNonNull(builder.provider, "provider must not be null");
        this.repoId = Objects.requireNonNull(builder.repoId, "repoId must not be null");
        this.ref = builder.ref;
        this.path = builder.path;
        this.credentialRef = builder.credentialRef;
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.projectId = Objects.requireNonNull(builder.projectId, "projectId must not be null");
    }

    /**
     * The source provider type (e.g., "github", "gitlab", "local-folder", "zip", "archive").
     */
    public String provider() {
        return provider;
    }

    /**
     * Repository identifier (e.g., "owner/repo" for GitHub, absolute path for local).
     */
    public String repoId() {
        return repoId;
    }

    /**
     * Optional commit SHA, branch name, or tag to resolve.
     */
    public Optional<String> ref() {
        return Optional.ofNullable(ref);
    }

    /**
     * Optional sub-path within the repository to scope the import.
     */
    public Optional<String> path() {
        return Optional.ofNullable(path);
    }

    /**
     * Optional reference to stored credentials for accessing private repositories.
     */
    public Optional<String> credentialRef() {
        return Optional.ofNullable(credentialRef);
    }

    /**
     * Tenant ID for scope isolation.
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * Workspace ID for scope isolation.
     */
    public String workspaceId() {
        return workspaceId;
    }

    /**
     * Project ID for scope isolation.
     */
    public String projectId() {
        return projectId;
    }

    /**
     * Creates a new builder for SourceLocator.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceLocator)) return false;
        SourceLocator that = (SourceLocator) o;
        return Objects.equals(provider, that.provider) &&
                Objects.equals(repoId, that.repoId) &&
                Objects.equals(ref, that.ref) &&
                Objects.equals(path, that.path) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(workspaceId, that.workspaceId) &&
                Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, repoId, ref, path, tenantId, workspaceId, projectId);
    }

    @Override
    public String toString() {
        return "SourceLocator{" +
                "provider='" + provider + '\'' +
                ", repoId='" + repoId + '\'' +
                ", ref='" + ref + '\'' +
                ", path='" + path + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", workspaceId='" + workspaceId + '\'' +
                ", projectId='" + projectId + '\'' +
                '}';
    }

    /**
     * Builder for SourceLocator.
     */
    public static class Builder {
        private String provider;
        private String repoId;
        private String ref;
        private String path;
        private String credentialRef;
        private String tenantId;
        private String workspaceId;
        private String projectId;

        private Builder() {}

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder repoId(String repoId) {
            this.repoId = repoId;
            return this;
        }

        public Builder ref(String ref) {
            this.ref = ref;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder credentialRef(String credentialRef) {
            this.credentialRef = credentialRef;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public SourceLocator build() {
            return new SourceLocator(this);
        }
    }
}
