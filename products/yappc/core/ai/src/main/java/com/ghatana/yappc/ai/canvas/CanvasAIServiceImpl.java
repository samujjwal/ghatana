package com.ghatana.yappc.ai.canvas;

import com.ghatana.contracts.canvas.v1.*;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.ghatana.agent.memory.security.MemoryRedactionFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

/**
 * Canvas AI Service gRPC Implementation
 *
 * Implements CanvasAIService gRPC interface.
 * Orchestrates validation and code generation services.
 * History is persisted via JDBC (PostgreSQL) so data survives restarts.
 *
 * @doc.type class
 * @doc.purpose gRPC service implementation for Canvas AI operations
 * @doc.layer platform
 * @doc.pattern Service
 */
public class CanvasAIServiceImpl extends CanvasAIServiceGrpc.CanvasAIServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(CanvasAIServiceImpl.class);

    private final CanvasValidationService validationService;
    private final CanvasGenerationService generationService;
    private final MetricsCollector metrics;
    private final DataSource dataSource;
    private final MemoryRedactionFilter redactionFilter;

    public CanvasAIServiceImpl(CanvasValidationService validationService,
                               CanvasGenerationService generationService,
                               MetricsCollector metrics,
                               DataSource dataSource,
                               MemoryRedactionFilter redactionFilter) {
        this.validationService = validationService;
        this.generationService = generationService;
        this.metrics = metrics;
        this.dataSource = dataSource;
        this.redactionFilter = redactionFilter;
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void saveValidationReport(String canvasId, ValidationReport report) {
        ValidationReport redacted = redactReport(report);
        String sql = "INSERT INTO canvas_validation_history (canvas_id, report_bytes, created_at) VALUES (?, ?, NOW())";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, canvasId);
            ps.setBytes(2, redacted.toByteArray());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to persist validation report for canvas {}: {}", canvasId, e.getMessage());
        }
    }

    private List<ValidationReport> loadValidationReports(String canvasId, int limit) {
        List<ValidationReport> reports = new ArrayList<>();
        String sql = "SELECT report_bytes FROM canvas_validation_history WHERE canvas_id = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, canvasId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reports.add(redactReport(ValidationReport.parseFrom(rs.getBytes("report_bytes"))));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load validation history for canvas {}: {}", canvasId, e.getMessage());
        }
        return reports;
    }

    private int countValidationReports(String canvasId) {
        String sql = "SELECT COUNT(*) FROM canvas_validation_history WHERE canvas_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, canvasId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to count validation history for canvas {}: {}", canvasId, e.getMessage());
        }
        return 0;
    }

    private void saveGenerationResult(String canvasId, CodeGenerationResult result) {
        CodeGenerationResult redacted = redactResult(result);
        String sql = "INSERT INTO canvas_generation_history (canvas_id, result_bytes, created_at) VALUES (?, ?, NOW())";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, canvasId);
            ps.setBytes(2, redacted.toByteArray());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to persist generation result for canvas {}: {}", canvasId, e.getMessage());
        }
    }

    private List<CodeGenerationResult> loadGenerationResults(String canvasId, int limit) {
        List<CodeGenerationResult> results = new ArrayList<>();
        String sql = "SELECT result_bytes FROM canvas_generation_history WHERE canvas_id = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, canvasId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(redactResult(CodeGenerationResult.parseFrom(rs.getBytes("result_bytes"))));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load generation history for canvas {}: {}", canvasId, e.getMessage());
        }
        return results;
    }

    private int countGenerationResults(String canvasId) {
        String sql = "SELECT COUNT(*) FROM canvas_generation_history WHERE canvas_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, canvasId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to count generation history for canvas {}: {}", canvasId, e.getMessage());
        }
        return 0;
    }

    /**
     * Redacts sensitive content from a ValidationReport before persistence or after retrieval.
     */
    private ValidationReport redactReport(ValidationReport report) {
        List<ValidationIssue> redactedIssues = report.getIssuesList().stream()
            .map(i -> ValidationIssue.newBuilder(i)
                .setTitle(redactionFilter.redact(i.getTitle()))
                .setDescription(redactionFilter.redact(i.getDescription()))
                .setSuggestion(redactionFilter.redact(i.getSuggestion()))
                .build())
            .collect(Collectors.toList());

        List<RiskAssessment> redactedRisks = report.getRisksList().stream()
            .map(r -> RiskAssessment.newBuilder(r)
                .setTitle(redactionFilter.redact(r.getTitle()))
                .setDescription(redactionFilter.redact(r.getDescription()))
                .setImpact(redactionFilter.redact(r.getImpact()))
                .setMitigation(redactionFilter.redact(r.getMitigation()))
                .build())
            .collect(Collectors.toList());

        List<String> redactedGaps = report.getGapsList().stream()
            .map(redactionFilter::redact)
            .collect(Collectors.toList());

        return ValidationReport.newBuilder(report)
            .clearIssues()
            .addAllIssues(redactedIssues)
            .clearRisks()
            .addAllRisks(redactedRisks)
            .clearGaps()
            .addAllGaps(redactedGaps)
            .build();
    }

    /**
     * Redacts sensitive content from a CodeGenerationResult before persistence or after retrieval.
     */
    private CodeGenerationResult redactResult(CodeGenerationResult result) {
        List<GeneratedArtifact> redactedArtifacts = result.getArtifactsList().stream()
            .map(a -> GeneratedArtifact.newBuilder(a)
                .setPath(redactionFilter.redact(a.getPath()))
                .setContent(redactionFilter.redact(a.getContent()))
                .setLanguage(redactionFilter.redact(a.getLanguage()))
                .setFramework(redactionFilter.redact(a.getFramework()))
                .build())
            .collect(Collectors.toList());

        List<String> redactedErrors = result.getErrorsList().stream()
            .map(redactionFilter::redact)
            .collect(Collectors.toList());

        List<String> redactedWarnings = result.getWarningsList().stream()
            .map(redactionFilter::redact)
            .collect(Collectors.toList());

        return CodeGenerationResult.newBuilder(result)
            .clearArtifacts()
            .addAllArtifacts(redactedArtifacts)
            .setSummary(redactionFilter.redact(result.getSummary()))
            .clearErrors()
            .addAllErrors(redactedErrors)
            .clearWarnings()
            .addAllWarnings(redactedWarnings)
            .build();
    }

    /**
     * Await a Promise result in a blocking context (gRPC service method).
     * Uses CountDownLatch to properly bridge async Promise to sync result.
     */
    private <T> T awaitPromise(Promise<T> promise) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        promise.whenComplete((result, error) -> {
            if (error != null) {
                errorRef.set(error);
            } else {
                resultRef.set(result);
            }
            latch.countDown();
        });

        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Promise timed out after 30 seconds");
        }

        if (errorRef.get() != null) {
            throw new Exception(errorRef.get());
        }

        return resultRef.get();
    }

    /**
     * Validate canvas design
     */
    @Override
    public void validateCanvas(ValidateCanvasRequest request,
                               StreamObserver<ValidationReport> responseObserver) {
        logger.info("Received validation request for canvas: {}",
            request.getCanvasState().getCanvasId());

        try {
            metrics.incrementCounter("grpc.canvas.validate.requests");

            // Execute validation
            ValidationReport report = awaitPromise(validationService.validate(request));

            // Store in history
            String canvasId = request.getCanvasState().getCanvasId();
            saveValidationReport(canvasId, report);

            // Send response
            responseObserver.onNext(report);
            responseObserver.onCompleted();

            metrics.incrementCounter("grpc.canvas.validate.success");

        } catch (Exception e) {
            logger.error("Validation failed", e);
            metrics.incrementCounter("grpc.canvas.validate.errors");
            responseObserver.onError(e);
        }
    }

    /**
     * Generate code from canvas (non-streaming)
     */
    @Override
    public void generateCode(GenerateCodeRequest request,
                            StreamObserver<CodeGenerationResult> responseObserver) {
        logger.info("Received code generation request for canvas: {}",
            request.getCanvasState().getCanvasId());

        try {
            metrics.incrementCounter("grpc.canvas.generate.requests");

            // Execute generation
            CodeGenerationResult result = awaitPromise(generationService.generate(request));

            // Store in history
            String canvasId = request.getCanvasState().getCanvasId();
            saveGenerationResult(canvasId, result);

            // Send response
            responseObserver.onNext(result);
            responseObserver.onCompleted();

            metrics.incrementCounter("grpc.canvas.generate.success");

        } catch (Exception e) {
            logger.error("Code generation failed", e);
            metrics.incrementCounter("grpc.canvas.generate.errors");
            responseObserver.onError(e);
        }
    }

    /**
     * Generate code from canvas (streaming progress)
     */
    @Override
    public void generateCodeStream(GenerateCodeRequest request,
                                   StreamObserver<GenerationProgress> responseObserver) {
        logger.info("Received streaming code generation request for canvas: {}",
            request.getCanvasState().getCanvasId());

        try {
            metrics.incrementCounter("grpc.canvas.generate.stream.requests");

            String canvasId = request.getCanvasState().getCanvasId();

            // Send initial progress
            responseObserver.onNext(GenerationProgress.newBuilder()
                .setStage("initializing")
                .setPercent(0)
                .setMessage("Starting code generation...")
                .build());

            // Execute generation (with progress tracking)
            // NOTE: Implement streaming progress updates during generation

            // For now, execute generation and send completion
            CodeGenerationResult result = awaitPromise(generationService.generate(request));

            // Store in history
            saveGenerationResult(canvasId, result);

            // Send progress updates
            responseObserver.onNext(GenerationProgress.newBuilder()
                .setStage("generating_backend")
                .setPercent(25)
                .setMessage("Generating backend API...")
                .build());

            responseObserver.onNext(GenerationProgress.newBuilder()
                .setStage("generating_database")
                .setPercent(50)
                .setMessage("Generating database schema...")
                .build());

            responseObserver.onNext(GenerationProgress.newBuilder()
                .setStage("generating_frontend")
                .setPercent(50)
                .setMessage("Generating frontend components...")
                .build());

            // Send final progress
            responseObserver.onNext(GenerationProgress.newBuilder()
                .setStage("complete")
                .setPercent(100)
                .setMessage("Code generation complete!")
                .build());

            responseObserver.onCompleted();

            metrics.incrementCounter("grpc.canvas.generate.stream.success");

        } catch (Exception e) {
            logger.error("Streaming code generation failed", e);
            metrics.incrementCounter("grpc.canvas.generate.stream.errors");
            responseObserver.onError(e);
        }
    }

    /**
     * Get validation history for canvas
     */
    @Override
    public void getValidationHistory(GetValidationHistoryRequest request,
                                     StreamObserver<GetValidationHistoryResponse> responseObserver) {
        logger.info("Received validation history request for canvas: {}",
            request.getCanvasId());

        try {
            metrics.incrementCounter("grpc.canvas.validation.history.requests");

            int limit = request.getLimit() > 0 ? request.getLimit() : Integer.MAX_VALUE;
            List<ValidationReport> reports = loadValidationReports(request.getCanvasId(), limit);
            int total = countValidationReports(request.getCanvasId());

            GetValidationHistoryResponse response = GetValidationHistoryResponse.newBuilder()
                .addAllReports(reports)
                .setTotalCount(total)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            metrics.incrementCounter("grpc.canvas.validation.history.success");

        } catch (Exception e) {
            logger.error("Failed to retrieve validation history", e);
            metrics.incrementCounter("grpc.canvas.validation.history.errors");
            responseObserver.onError(e);
        }
    }

    /**
     * Get generation history for canvas
     */
    @Override
    public void getGenerationHistory(GetGenerationHistoryRequest request,
                                    StreamObserver<GetGenerationHistoryResponse> responseObserver) {
        logger.info("Received generation history request for canvas: {}",
            request.getCanvasId());

        try {
            metrics.incrementCounter("grpc.canvas.generation.history.requests");

            int limit = request.getLimit() > 0 ? request.getLimit() : Integer.MAX_VALUE;
            List<CodeGenerationResult> results = loadGenerationResults(request.getCanvasId(), limit);
            int total = countGenerationResults(request.getCanvasId());

            GetGenerationHistoryResponse response = GetGenerationHistoryResponse.newBuilder()
                .addAllResults(results)
                .setTotalCount(total)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            metrics.incrementCounter("grpc.canvas.generation.history.success");

        } catch (Exception e) {
            logger.error("Failed to retrieve generation history", e);
            metrics.incrementCounter("grpc.canvas.generation.history.errors");
            responseObserver.onError(e);
        }
    }
}
