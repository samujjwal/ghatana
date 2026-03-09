package com.ghatana.yappc.api.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Status of a scaffolding job.
 * 
 * @doc.type record
 * @doc.purpose Real-time scaffolding progress tracking
 * @doc.layer product
 * @doc.pattern DTO
 */
public record JobStatus(
        @JsonProperty("jobId") String jobId,
        @JsonProperty("status") String status,
        @JsonProperty("progress") int progress,
        @JsonProperty("message") String message,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("completedAt") Instant completedAt,
        @JsonProperty("error") String error) {
    
    public static JobStatus running(String jobId, int progress, String message) {
        return new JobStatus(jobId, "RUNNING", progress, message, Instant.now(), null, null);
    }
    
    public static JobStatus completed(String jobId) {
        return new JobStatus(jobId, "COMPLETED", 100, "Project scaffolded successfully", null, Instant.now(), null);
    }
    
    public static JobStatus failed(String jobId, String error) {
        return new JobStatus(jobId, "FAILED", 0, "Scaffolding failed", null, Instant.now(), error);
    }
}
