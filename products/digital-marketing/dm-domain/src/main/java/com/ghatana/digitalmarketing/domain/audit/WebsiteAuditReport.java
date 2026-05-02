package com.ghatana.digitalmarketing.domain.audit;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable website audit report with source-backed findings.
 *
 * @doc.type class
 * @doc.purpose DMOS website audit report aggregate for F1-010 diagnostics
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class WebsiteAuditReport {

    private final String reportId;
    private final DmWorkspaceId workspaceId;
    private final String websiteUrl;
    private final List<WebsiteAuditFinding> findings;
    private final Instant generatedAt;
    private final String generatedBy;

    private WebsiteAuditReport(Builder builder) {
        this.reportId = Objects.requireNonNull(builder.reportId, "reportId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.websiteUrl = Objects.requireNonNull(builder.websiteUrl, "websiteUrl must not be null");
        this.findings = builder.findings != null ? List.copyOf(builder.findings) : List.of();
        this.generatedAt = Objects.requireNonNull(builder.generatedAt, "generatedAt must not be null");
        this.generatedBy = Objects.requireNonNull(builder.generatedBy, "generatedBy must not be null");

        if (reportId.isBlank()) {
            throw new IllegalArgumentException("reportId must not be blank");
        }
        if (websiteUrl.isBlank()) {
            throw new IllegalArgumentException("websiteUrl must not be blank");
        }
        if (generatedBy.isBlank()) {
            throw new IllegalArgumentException("generatedBy must not be blank");
        }
    }

    public String getReportId() {
        return reportId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public List<WebsiteAuditFinding> getFindings() {
        return findings;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String reportId;
        private DmWorkspaceId workspaceId;
        private String websiteUrl;
        private List<WebsiteAuditFinding> findings;
        private Instant generatedAt;
        private String generatedBy;

        private Builder() {
        }

        public Builder reportId(String reportId) {
            this.reportId = reportId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder websiteUrl(String websiteUrl) {
            this.websiteUrl = websiteUrl;
            return this;
        }

        public Builder findings(List<WebsiteAuditFinding> findings) {
            this.findings = findings;
            return this;
        }

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder generatedBy(String generatedBy) {
            this.generatedBy = generatedBy;
            return this;
        }

        public WebsiteAuditReport build() {
            return new WebsiteAuditReport(this);
        }
    }
}
