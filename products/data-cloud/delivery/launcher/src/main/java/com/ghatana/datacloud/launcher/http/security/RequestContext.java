package com.ghatana.datacloud.launcher.http.security;

import com.ghatana.platform.governance.security.Principal;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical request context containing authenticated identity, tenant/scope resolution,
 * and authorization metadata. Created once at request entry after authentication/authorization.
 *
 * <p>This class is immutable and thread-safe. It provides the single source of truth for:
 * <ul>
 *   <li>Authenticated tenant identity (never from spoofable headers/query params)</li>
 *   <li>Workspace/project scope within tenant</li>
 *   <li>User principal and roles</li>
 *   <li>Request correlation and trace IDs</li>
 *   <li>Audit context for the request lifecycle</li>
 *   <li>Unified observability identifiers for runtime truth tracking</li>
 * </ul>
 *
 * <p>Handlers should receive this context rather than parsing tenant/scope from HTTP requests directly.
 * This prevents spoofing attacks and ensures consistent authorization enforcement.
 *
 * <p><b>Observability Integration:</b><br>
 * This context provides unified correlation identifiers that should be passed to
 * {@link com.ghatana.datacloud.observability.ObservabilityService} for consistent
 * runtime truth tracking across all async/AI workflows.
 *
 * @doc.type class
 * @doc.purpose Canonical authenticated request context for tenant-scoped operations with unified observability
 * @doc.layer product
 * @doc.pattern Value Object, Security Context
 */
public final class RequestContext {

    private final String tenantId;
    private final String workspaceId;
    private final String projectId;
    private final Principal principal;
    private final Set<String> roles;
    private final String correlationId;
    private final String traceId;
    private final Instant createdAt;
    private final String requestPath;
    private final String requestMethod;
    private final Map<String, String> metadata;
    private final boolean supportAccess;
    private final String supportReason;

    // Unified observability identifiers for runtime truth tracking
    private final String surface;
    private final String runId;
    private final String jobId;
    private final String agentId;
    private final String pipelineId;
    private final String artifactId;

    private RequestContext(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId is required");
        this.workspaceId = builder.workspaceId;
        this.projectId = builder.projectId;
        this.principal = builder.principal;
        this.roles = builder.roles != null ? Set.copyOf(builder.roles) : Set.of();
        this.correlationId = Objects.requireNonNull(builder.correlationId, "correlationId is required");
        this.traceId = builder.traceId != null ? builder.traceId : builder.correlationId;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.requestPath = builder.requestPath;
        this.requestMethod = builder.requestMethod;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        this.supportAccess = builder.supportAccess;
        this.supportReason = builder.supportReason;
        // Unified observability identifiers
        this.surface = builder.surface;
        this.runId = builder.runId;
        this.jobId = builder.jobId;
        this.agentId = builder.agentId;
        this.pipelineId = builder.pipelineId;
        this.artifactId = builder.artifactId;
    }

    /**
     * Returns the canonical tenant ID - always from authenticated identity or trusted gateway context.
     * Never from spoofable X-Tenant-Id header or query parameter.
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * Returns the workspace ID within the tenant, if applicable.
     */
    public Optional<String> workspaceId() {
        return Optional.ofNullable(workspaceId);
    }

    /**
     * Returns the project ID within the workspace/tenant, if applicable.
     */
    public Optional<String> projectId() {
        return Optional.ofNullable(projectId);
    }

    /**
     * Returns the authenticated principal, if available.
     */
    public Optional<Principal> principal() {
        return Optional.ofNullable(principal);
    }

    /**
     * Returns the authenticated user's roles.
     */
    public Set<String> roles() {
        return roles;
    }

    /**
     * Checks if the context has the specified role.
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Returns true if any of the specified roles are present.
     */
    public boolean hasAnyRole(String... roleArray) {
        for (String role : roleArray) {
            if (roles.contains(role)) return true;
        }
        return false;
    }

    /**
     * Returns the correlation ID for request tracing.
     */
    public String correlationId() {
        return correlationId;
    }

    /**
     * Returns the trace ID for distributed tracing.
     */
    public String traceId() {
        return traceId;
    }

    /**
     * Returns when this context was created.
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Returns the request path that created this context.
     */
    public Optional<String> requestPath() {
        return Optional.ofNullable(requestPath);
    }

    /**
     * Returns the request method that created this context.
     */
    public Optional<String> requestMethod() {
        return Optional.ofNullable(requestMethod);
    }

    /**
     * Returns additional metadata associated with this context.
     */
    public Map<String, String> metadata() {
        return metadata;
    }

    /**
     * Returns true if this is a support/delegated access request.
     */
    public boolean isSupportAccess() {
        return supportAccess;
    }

    /**
     * Returns the reason for support access, if applicable.
     */
    public Optional<String> supportReason() {
        return Optional.ofNullable(supportReason);
    }

    /**
     * Returns the surface identifier for observability (e.g., "api", "agent", "workflow").
     */
    public Optional<String> surface() {
        return Optional.ofNullable(surface);
    }

    /**
     * Returns the run ID for observability tracking.
     */
    public Optional<String> runId() {
        return Optional.ofNullable(runId);
    }

    /**
     * Returns the job ID for observability tracking.
     */
    public Optional<String> jobId() {
        return Optional.ofNullable(jobId);
    }

    /**
     * Returns the agent ID for observability tracking.
     */
    public Optional<String> agentId() {
        return Optional.ofNullable(agentId);
    }

    /**
     * Returns the pipeline ID for observability tracking.
     */
    public Optional<String> pipelineId() {
        return Optional.ofNullable(pipelineId);
    }

    /**
     * Returns the artifact ID for observability tracking.
     */
    public Optional<String> artifactId() {
        return Optional.ofNullable(artifactId);
    }

    /**
     * Returns a new context with the specified workspace ID.
     */
    public RequestContext withWorkspace(String workspaceId) {
        return new Builder(this).withWorkspace(workspaceId).build();
    }

    /**
     * Returns a new context with the specified project ID.
     */
    public RequestContext withProject(String projectId) {
        return new Builder(this).withProject(projectId).build();
    }

    /**
     * Returns a new context with additional metadata.
     */
    public RequestContext withMetadata(String key, String value) {
        Builder builder = new Builder(this);
        Map<String, String> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put(key, value);
        builder.withMetadata(newMetadata);
        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestContext that = (RequestContext) o;
        return tenantId.equals(that.tenantId) &&
               Objects.equals(workspaceId, that.workspaceId) &&
               Objects.equals(projectId, that.projectId) &&
               correlationId.equals(that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, workspaceId, projectId, correlationId);
    }

    @Override
    public String toString() {
        return "RequestContext{" +
               "tenantId='" + tenantId + '\'' +
               ", workspaceId='" + workspaceId + '\'' +
               ", projectId='" + projectId + '\'' +
               ", roles=" + roles +
               ", correlationId='" + correlationId + '\'' +
               ", supportAccess=" + supportAccess +
               '}';
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RequestContext.
     */
    public static final class Builder {
        private String tenantId;
        private String workspaceId;
        private String projectId;
        private Principal principal;
        private Set<String> roles;
        private String correlationId;
        private String traceId;
        private Instant createdAt;
        private String requestPath;
        private String requestMethod;
        private Map<String, String> metadata;
        private boolean supportAccess;
        private String supportReason;

        // Unified observability identifiers
        private String surface;
        private String runId;
        private String jobId;
        private String agentId;
        private String pipelineId;
        private String artifactId;

        private Builder() {}

        private Builder(RequestContext context) {
            this.tenantId = context.tenantId;
            this.workspaceId = context.workspaceId;
            this.projectId = context.projectId;
            this.principal = context.principal;
            this.roles = new java.util.HashSet<>(context.roles);
            this.correlationId = context.correlationId;
            this.traceId = context.traceId;
            this.createdAt = context.createdAt;
            this.requestPath = context.requestPath;
            this.requestMethod = context.requestMethod;
            this.metadata = new HashMap<>(context.metadata);
            this.supportAccess = context.supportAccess;
            this.supportReason = context.supportReason;
            // Unified observability identifiers
            this.surface = context.surface;
            this.runId = context.runId;
            this.jobId = context.jobId;
            this.agentId = context.agentId;
            this.pipelineId = context.pipelineId;
            this.artifactId = context.artifactId;
        }

        public Builder withTenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder withWorkspace(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder withProject(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder withPrincipal(Principal principal) {
            this.principal = principal;
            return this;
        }

        public Builder withRoles(Set<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder withCorrelationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder withTraceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder withRequestPath(String requestPath) {
            this.requestPath = requestPath;
            return this;
        }

        public Builder withRequestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
            return this;
        }

        public Builder withMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder withSupportAccess(boolean supportAccess, String reason) {
            this.supportAccess = supportAccess;
            this.supportReason = reason;
            return this;
        }

        public Builder withSurface(String surface) {
            this.surface = surface;
            return this;
        }

        public Builder withRunId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder withJobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder withAgentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder withPipelineId(String pipelineId) {
            this.pipelineId = pipelineId;
            return this;
        }

        public Builder withArtifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public RequestContext build() {
            return new RequestContext(this);
        }
    }
}
