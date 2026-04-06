package com.ghatana.yappc.platform.ai.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Represents a tenant-scoped AI-generated insight surfaced to users or automation.
 * @doc.layer product
 * @doc.pattern Model
 */
public record AIInsight(
    String insightId,
    String tenantId,
    String projectId,
    InsightType type,
    InsightSeverity severity,
    String title,
    String description,
    String suggestion,
    double confidence,
    String sourceRef,
    int lineNumber,
    List<String> tags,
    Instant generatedAt,
    boolean dismissed) {

  public AIInsight {
    insightId = Objects.requireNonNullElse(insightId, "unknown-insight");
    tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
    projectId = Objects.requireNonNullElse(projectId, "unknown-project");
    type = type == null ? InsightType.CODE_QUALITY : type;
    severity = severity == null ? InsightSeverity.INFO : severity;
    title = Objects.requireNonNullElse(title, "Untitled insight");
    description = Objects.requireNonNullElse(description, "");
    suggestion = Objects.requireNonNullElse(suggestion, "");
    confidence = Math.max(0.0, Math.min(1.0, confidence));
    sourceRef = Objects.requireNonNullElse(sourceRef, "");
    lineNumber = Math.max(0, lineNumber);
    tags = tags == null ? List.of() : List.copyOf(tags);
    generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
  }

  public enum InsightType {
    CODE_QUALITY,
    SECURITY,
    TEST_GAP,
    PERFORMANCE,
    REQUIREMENT,
    ARCHITECTURE
  }

  public enum InsightSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
  }
}