/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.architecture.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for architecture impact analysis.
 *
 * @doc.type record
 * @doc.purpose Architecture impact response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record ArchitectureImpactResponse(
    @JsonProperty("entityId") String entityId,
    @JsonProperty("entityType") String entityType,
    @JsonProperty("entityName") String entityName,
    @JsonProperty("overallRisk") RiskLevel overallRisk,
    @JsonProperty("blastRadius") BlastRadius blastRadius,
    @JsonProperty("impactedComponents") List<ImpactedComponent> impactedComponents,
    @JsonProperty("patternWarnings") List<PatternWarning> patternWarnings,
    @JsonProperty("techDebt") TechDebtSummary techDebt,
    @JsonProperty("recommendations") List<Recommendation> recommendations) {
  /** Risk level enumeration. */
  public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
  }

  /** Blast radius summary. */
  public record BlastRadius(
      @JsonProperty("level") RiskLevel level,
      @JsonProperty("directImpacts") int directImpacts,
      @JsonProperty("transitiveImpacts") int transitiveImpacts,
      @JsonProperty("totalImpacts") int totalImpacts,
      @JsonProperty("affectedTeams") List<String> affectedTeams,
      @JsonProperty("affectedServices") List<String> affectedServices) {}

  /** Impacted component. */
  public record ImpactedComponent(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("impactType") ImpactType impactType,
      @JsonProperty("severity") RiskLevel severity,
      @JsonProperty("distance") int distance,
      @JsonProperty("path") List<String> path,
      @JsonProperty("description") String description) {}

  /** Impact type. */
  public enum ImpactType {
    DIRECT, // Direct dependency
    TRANSITIVE, // Transitive dependency
    SHARED_DATA, // Shares data model
    API_CONSUMER, // Consumes API
    EVENT_LISTENER // Listens to events
  }

  /** Pattern warning. */
  public record PatternWarning(
      @JsonProperty("id") String id,
      @JsonProperty("type") String type,
      @JsonProperty("severity") RiskLevel severity,
      @JsonProperty("title") String title,
      @JsonProperty("description") String description,
      @JsonProperty("location") String location,
      @JsonProperty("suggestion") String suggestion) {}

  /** Tech debt summary. */
  public record TechDebtSummary(
      @JsonProperty("score") int score,
      @JsonProperty("trend") String trend,
      @JsonProperty("items") List<TechDebtItem> items,
      @JsonProperty("totalEstimatedHours") double totalEstimatedHours) {}

  /** Individual tech debt item. */
  public record TechDebtItem(
      @JsonProperty("id") String id,
      @JsonProperty("type") String type,
      @JsonProperty("severity") RiskLevel severity,
      @JsonProperty("title") String title,
      @JsonProperty("description") String description,
      @JsonProperty("estimatedHours") double estimatedHours,
      @JsonProperty("createdAt") String createdAt) {}

  /** Recommendation. */
  public record Recommendation(
      @JsonProperty("id") String id,
      @JsonProperty("priority") RiskLevel priority,
      @JsonProperty("type") String type,
      @JsonProperty("title") String title,
      @JsonProperty("description") String description,
      @JsonProperty("estimatedEffort") String estimatedEffort,
      @JsonProperty("relatedItems") List<String> relatedItems) {}
}
