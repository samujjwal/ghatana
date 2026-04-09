package com.ghatana.yappc.platform.ai.model;

import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Defines background analysis events consumed by the implicit AI pipeline.
 * @doc.layer product
 * @doc.pattern Model
 */
public sealed interface AnalysisEvent
  permits AnalysisEvent.CodeChangedEvent,
    AnalysisEvent.RequirementChangedEvent,
    AnalysisEvent.ArchitectureChangedEvent {

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

  record RequirementChangedEvent(
      String tenantId,
      String projectId,
      String requirementId,
      String title,
      String requirementText,
      List<String> relatedRequirementSummaries)
      implements AnalysisEvent {

    public RequirementChangedEvent {
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      projectId = Objects.requireNonNullElse(projectId, "unknown-project");
      requirementId = Objects.requireNonNullElse(requirementId, "unknown-requirement");
      title = Objects.requireNonNullElse(title, "Untitled requirement");
      requirementText = Objects.requireNonNullElse(requirementText, "");
      relatedRequirementSummaries =
          relatedRequirementSummaries == null ? List.of() : List.copyOf(relatedRequirementSummaries);
    }

    @Override
    public String correlationKey() {
      return tenantId + ":" + projectId + ":requirement:" + requirementId;
    }

    @Override
    public String sourceRef() {
      return requirementId;
    }
  }

  record ArchitectureChangedEvent(
      String tenantId,
      String projectId,
      String componentName,
      String changeSummary,
      List<String> affectedModules,
      boolean crossBoundaryChange)
      implements AnalysisEvent {

    public ArchitectureChangedEvent {
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      projectId = Objects.requireNonNullElse(projectId, "unknown-project");
      componentName = Objects.requireNonNullElse(componentName, "unknown-component");
      changeSummary = Objects.requireNonNullElse(changeSummary, "");
      affectedModules = affectedModules == null ? List.of() : List.copyOf(affectedModules);
    }

    @Override
    public String correlationKey() {
      return tenantId + ":" + projectId + ":architecture:" + componentName;
    }

    @Override
    public String sourceRef() {
      return componentName;
    }
  }
}
