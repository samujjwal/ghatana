package com.ghatana.pipeline.registry.migration;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Report of a pipeline migration execution.
 *
 * @doc.type class
 * @doc.purpose DTO for migration results
 * @doc.layer product
 * @doc.pattern DTO
 */
@Data
@Builder
public class MigrationReport {

    @Builder.Default
    private Instant startTime = Instant.now();

    private Instant endTime;

    @Builder.Default
    private int totalPipelines = 0;

    @Builder.Default
    private int migratedPipelines = 0;

    @Builder.Default
    private int failedPipelines = 0;

    @Builder.Default
    private int skippedPipelines = 0;

    @Builder.Default
    private List<MigrationFailure> failures = new ArrayList<>();

    @Builder.Default
    private boolean dryRun = false;

    public void addFailure(String pipelineId, String reason, Throwable error) {
        failures.add(MigrationFailure.builder()
                .pipelineId(pipelineId)
                .reason(reason)
                .error(error != null ? error.getMessage() : null)
                .build());
        failedPipelines++;
    }

    public void incrementMigrated() {
        migratedPipelines++;
    }

    public void incrementSkipped() {
        skippedPipelines++;
    }

    public void complete() {
        this.endTime = Instant.now();
    }

    @Data
    @Builder
    public static class MigrationFailure {
        private String pipelineId;
        private String reason;
        private String error;
    }
}

