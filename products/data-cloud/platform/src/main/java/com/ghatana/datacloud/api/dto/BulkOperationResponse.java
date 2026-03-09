package com.ghatana.datacloud.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for bulk operation responses returned by BulkController.
 
 *
 * @doc.type class
 * @doc.purpose Bulk operation response
 * @doc.layer platform
 * @doc.pattern DataTransfer
*/
public class BulkOperationResponse {

    @JsonProperty("operation")
    private final String operation;

    @JsonProperty("successCount")
    private final long successCount;

    @JsonProperty("failureCount")
    private final long failureCount;

    @JsonProperty("durationMs")
    private final long durationMs;

    public BulkOperationResponse(String operation, long successCount, long failureCount, long durationMs) {
        this.operation = operation;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.durationMs = durationMs;
    }

    public String getOperation() {
        return operation;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
