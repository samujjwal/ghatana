/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.version.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for version comparison (diff).
 *
 * @doc.type record
 * @doc.purpose Version diff response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record CompareVersionsResponse(
    @JsonProperty("entityId") String entityId,
    @JsonProperty("v1") int v1,
    @JsonProperty("v2") int v2,
    @JsonProperty("changes") List<DiffEntry> changes,
    @JsonProperty("summary") DiffSummary summary) {
  /** Represents a single diff entry. */
  public record DiffEntry(
      @JsonProperty("field") String field,
      @JsonProperty("changeType") ChangeType changeType,
      @JsonProperty("oldValue") Object oldValue,
      @JsonProperty("newValue") Object newValue,
      @JsonProperty("path") String path) {}

  /** Change type enumeration. */
  public enum ChangeType {
    ADDED,
    REMOVED,
    MODIFIED,
    UNCHANGED
  }

  /** Summary of diff statistics. */
  public record DiffSummary(
      @JsonProperty("added") int added,
      @JsonProperty("removed") int removed,
      @JsonProperty("modified") int modified,
      @JsonProperty("unchanged") int unchanged,
      @JsonProperty("totalChanges") int totalChanges) {
    public static DiffSummary from(List<DiffEntry> changes) {
      int added = 0, removed = 0, modified = 0, unchanged = 0;
      for (DiffEntry entry : changes) {
        switch (entry.changeType()) {
          case ADDED -> added++;
          case REMOVED -> removed++;
          case MODIFIED -> modified++;
          case UNCHANGED -> unchanged++;
        }
      }
      return new DiffSummary(added, removed, modified, unchanged, added + removed + modified);
    }
  }
}
