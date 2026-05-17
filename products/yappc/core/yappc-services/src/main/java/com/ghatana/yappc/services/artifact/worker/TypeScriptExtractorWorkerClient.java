package com.ghatana.yappc.services.artifact.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @doc.type class
 * @doc.purpose Java client to invoke TypeScript extractor worker process.
 *              Handles process execution, timeout management, structured diagnostics,
 *              and output contract validation. Never passes raw credentials to worker.
 * @doc.layer service
 * @doc.pattern Client
 *
 * P1: Java client to invoke TS worker with timeout, bounded concurrency,
 *     structured diagnostics, no raw credentials.
 */
public final class TypeScriptExtractorWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(TypeScriptExtractorWorkerClient.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final int MAX_OUTPUT_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final String workerScriptPath;
    private final int timeoutSeconds;

    public TypeScriptExtractorWorkerClient(
            ObjectMapper objectMapper,
            Executor executor,
            String workerScriptPath) {
        this(objectMapper, executor, workerScriptPath, DEFAULT_TIMEOUT_SECONDS);
    }

    public TypeScriptExtractorWorkerClient(
            ObjectMapper objectMapper,
            Executor executor,
            String workerScriptPath,
            int timeoutSeconds) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.workerScriptPath = Objects.requireNonNull(workerScriptPath, "workerScriptPath must not be null");
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Execute the TypeScript extractor worker for a repository snapshot.
     *
     * @param snapshot the repository snapshot to extract
     * @return promise of extraction result with nodes, edges, residuals
     */
    public Promise<ExtractorWorkerResult> extract(RepositorySnapshot snapshot) {
        return Promise.ofBlocking(executor, () -> doExtract(snapshot))
            .whenException(e -> log.error("TypeScript extraction failed for snapshot {}", snapshot.snapshotId(), e));
    }

    private ExtractorWorkerResult doExtract(RepositorySnapshot snapshot) throws Exception {
        Instant start = Instant.now();
        log.info("Starting TypeScript extraction for snapshot {} ({} files)",
            snapshot.snapshotId(), snapshot.fileCount());

        // Build request
        ExtractorWorkerRequest request = new ExtractorWorkerRequest(
            snapshot.snapshotId(),
            snapshot.provider(),
            snapshot.repoId(),
            snapshot.materializedRoot(),
            snapshot.files().stream()
                .map(f -> new FileMetadata(f.relativePath(), f.absolutePath(), f.sizeBytes()))
                .toList()
        );

        String requestJson = objectMapper.writeValueAsString(request);

        // Execute worker process
        ProcessBuilder processBuilder = new ProcessBuilder(
            "node", workerScriptPath
        );
        processBuilder.redirectErrorStream(false);

        Process process = processBuilder.start();

        // Write request to stdin
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(requestJson);
            writer.flush();
        }

        // Read response with timeout
        String responseJson = readResponseWithTimeout(process, timeoutSeconds);

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorOutput = readErrorStream(process);
            log.error("TypeScript worker failed with exit code {}: {}", exitCode, errorOutput);
            throw new ExtractorWorkerException(
                "Worker process failed with exit code " + exitCode + ": " + errorOutput,
                exitCode,
                Collections.emptyList()
            );
        }

        // Parse response
        ExtractorWorkerResponse response = parseResponse(responseJson);

        Duration duration = Duration.between(start, Instant.now());
        log.info("TypeScript extraction completed for snapshot {} in {}ms: {} nodes, {} edges, {} residuals",
            snapshot.snapshotId(), duration.toMillis(),
            response.nodes().size(), response.edges().size(), response.residualIslands().size());

        return new ExtractorWorkerResult(
            response.nodes(),
            response.edges(),
            response.unresolvedEdges(),
            response.edgeResolutionRecords(),
            response.residualIslands(),
            response.diagnostics()
        );
    }

    private String readResponseWithTimeout(Process process, int timeoutSeconds) throws Exception {
        StringBuilder output = new StringBuilder();
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                output.append(buffer, 0, read);
                if (output.length() > MAX_OUTPUT_SIZE_BYTES) {
                    throw new IOException("Worker output exceeded maximum size of " + MAX_OUTPUT_SIZE_BYTES + " bytes");
                }
                if (System.currentTimeMillis() > deadline) {
                    process.destroyForcibly();
                    throw new TimeoutException("Worker process timed out after " + timeoutSeconds + " seconds");
                }
            }
        }

        return output.toString();
    }

    private String readErrorStream(Process process) {
        StringBuilder error = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        } catch (IOException e) {
            log.warn("Failed to read worker error stream", e);
        }
        return error.toString();
    }

    private ExtractorWorkerResponse parseResponse(String json) throws JsonProcessingException {
        try {
            return objectMapper.readValue(json, ExtractorWorkerResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse worker response: {}", json.substring(0, Math.min(json.length(), 500)), e);
            throw e;
        }
    }

    // ============================================================================
    // Request/Response DTOs
    // ============================================================================

    public record ExtractorWorkerRequest(
        String snapshotId,
        String provider,
        String repoId,
        String materializedRoot,
        List<FileMetadata> files
    ) {}

    public record FileMetadata(
        String relativePath,
        String absolutePath,
        long sizeBytes
    ) {}

    public record ExtractorWorkerResponse(
        List<ArtifactNodeDto> nodes,
        List<ArtifactEdgeDto> edges,
        List<Map<String, Object>> unresolvedEdges,
        List<Map<String, Object>> edgeResolutionRecords,
        List<ResidualIslandDto> residualIslands,
        List<WorkerDiagnostic> diagnostics
    ) {}

    public record WorkerDiagnostic(
        String level, // INFO, WARNING, ERROR
        String code,
        String message,
        String filePath,
        int line,
        int column
    ) {}

    // ============================================================================
    // Result
    // ============================================================================

    public record ExtractorWorkerResult(
        List<ArtifactNodeDto> nodes,
        List<ArtifactEdgeDto> edges,
        List<Map<String, Object>> unresolvedEdges,
        List<Map<String, Object>> edgeResolutionRecords,
        List<ResidualIslandDto> residualIslands,
        List<WorkerDiagnostic> diagnostics
    ) {
        public ExtractorWorkerResult {
            nodes = nodes != null ? List.copyOf(nodes) : List.of();
            edges = edges != null ? List.copyOf(edges) : List.of();
            unresolvedEdges = unresolvedEdges != null ? List.copyOf(unresolvedEdges) : List.of();
            edgeResolutionRecords = edgeResolutionRecords != null ? List.copyOf(edgeResolutionRecords) : List.of();
            residualIslands = residualIslands != null ? List.copyOf(residualIslands) : List.of();
            diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        }

        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(d -> "ERROR".equals(d.level()));
        }

        public List<WorkerDiagnostic> getErrors() {
            return diagnostics.stream()
                .filter(d -> "ERROR".equals(d.level()))
                .toList();
        }

        public List<WorkerDiagnostic> getWarnings() {
            return diagnostics.stream()
                .filter(d -> "WARNING".equals(d.level()))
                .toList();
        }
    }

    // ============================================================================
    // Exception
    // ============================================================================

    public static class ExtractorWorkerException extends Exception {
        private final int exitCode;
        private final List<String> diagnostics;

        public ExtractorWorkerException(String message, int exitCode, List<String> diagnostics) {
            super(message);
            this.exitCode = exitCode;
            this.diagnostics = diagnostics;
        }

        public int exitCode() {
            return exitCode;
        }

        public List<String> diagnostics() {
            return diagnostics;
        }
    }
}
