package com.ghatana.yappc.api.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response when scaffolding job is initiated.
 * 
 * @doc.type record
 * @doc.purpose Job tracking information for async scaffolding
 * @doc.layer product
 * @doc.pattern DTO
 */
public record ScaffoldResult(
        @JsonProperty("jobId") String jobId,
        @JsonProperty("status") String status,
        @JsonProperty("message") String message) {
    
    public static ScaffoldResult started(String jobId) {
        return new ScaffoldResult(jobId, "STARTED", "Project scaffolding initiated");
    }
}
