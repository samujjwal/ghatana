package com.ghatana.yappc.api.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Conflicts detected during scaffolding.
 * 
 * @doc.type record
 * @doc.purpose File and configuration conflicts requiring user decision
 * @doc.layer product
 * @doc.pattern DTO
 */
public record ConflictReport(
        @JsonProperty("jobId") String jobId,
        @JsonProperty("conflicts") List<Conflict> conflicts) {
    
    public record Conflict(
            @JsonProperty("path") String path,
            @JsonProperty("type") String type,
            @JsonProperty("existingContent") String existingContent,
            @JsonProperty("newContent") String newContent) {
    }
}
