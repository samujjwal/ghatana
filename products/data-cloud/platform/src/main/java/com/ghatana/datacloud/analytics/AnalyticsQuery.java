package com.ghatana.datacloud.analytics;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an analytics query submitted to {@link AnalyticsQueryEngine}.
 *
 * @doc.type model
 * @doc.purpose Analytics query state tracking
 * @doc.layer core
 */
public class AnalyticsQuery {
    private final String id;
    private final String tenantId;
    private final String queryText;
    private final Map<String, Object> parameters;
    private final Instant submittedAt;
    private String status;
    private Instant completedAt;
    private String error;

    private AnalyticsQuery(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.queryText = builder.queryText;
        this.parameters = builder.parameters;
        this.submittedAt = builder.submittedAt;
        this.status = builder.status;
        this.completedAt = builder.completedAt;
        this.error = builder.error;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getQueryText() { return queryText; }
    public Map<String, Object> getParameters() { return parameters; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String tenantId;
        private String queryText;
        private Map<String, Object> parameters;
        private Instant submittedAt;
        private String status;
        private Instant completedAt;
        private String error;

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder queryText(String text) { this.queryText = text; return this; }
        public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }
        public Builder submittedAt(Instant submittedAt) { this.submittedAt = submittedAt; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder error(String error) { this.error = error; return this; }

        public AnalyticsQuery build() { return new AnalyticsQuery(this); }
    }
}
