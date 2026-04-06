package com.ghatana.yappc.platform.ai.model;

import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Defines background analysis events consumed by the implicit AI pipeline.
 * @doc.layer product
 * @doc.pattern Model
 */
public sealed interface AnalysisEvent permits AnalysisEvent.CodeChangedEvent {

  String tenantId();

  String projectId();

  String correlationKey();

  String sourceRef();

  record CodeChangedEvent(String tenantId, String projectId, String filePath, String diff)
      implements AnalysisEvent {

    public CodeChangedEvent {
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      projectId = Objects.requireNonNullElse(projectId, "unknown-project");
      filePath = Objects.requireNonNullElse(filePath, "unknown-file");
      diff = Objects.requireNonNullElse(diff, "");
    }

    @Override
    public String correlationKey() {
      return tenantId + ":" + projectId + ":" + filePath;
    }

    @Override
    public String sourceRef() {
      return filePath;
    }
  }
}