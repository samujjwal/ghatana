package com.ghatana.yappc.ai.canvas;

import com.ghatana.contracts.canvas.v1.*;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Canvas AI Service gRPC Implementation
 * 
 * Implements CanvasAIService gRPC interface.
 * Orchestrates validation and code generation services.
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
    
    // In-memory history storage (use Redis/DB in production)
    private final Map<String, List<ValidationReport>> validationHistory = new HashMap<>();
    private final Map<String, List<CodeGenerationResult>> generationHistory = new HashMap<>();
    
    public CanvasAIServiceImpl(CanvasValidationService validationService,
                               CanvasGenerationService generationService,
                               MetricsCollector metrics) {
        this.validationService = validationService;
        this.generationService = generationService;
        this.metrics = metrics;
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
            validationHistory.computeIfAbsent(canvasId, k -> new ArrayList<>()).add(report);
            
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
            generationHistory.computeIfAbsent(canvasId, k -> new ArrayList<>()).add(result);
            
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
            generationHistory.computeIfAbsent(canvasId, k -> new ArrayList<>()).add(result);
            
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
            
            List<ValidationReport> reports = validationHistory.getOrDefault(
                request.getCanvasId(), List.of());
            
            // Apply limit if specified
            int limit = request.getLimit() > 0 ? request.getLimit() : reports.size();
            List<ValidationReport> limited = reports.subList(
                Math.max(0, reports.size() - limit), reports.size());
            
            GetValidationHistoryResponse response = GetValidationHistoryResponse.newBuilder()
                .addAllReports(limited)
                .setTotalCount(reports.size())
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
            
            List<CodeGenerationResult> results = generationHistory.getOrDefault(
                request.getCanvasId(), List.of());
            
            // Apply limit if specified
            int limit = request.getLimit() > 0 ? request.getLimit() : results.size();
            List<CodeGenerationResult> limited = results.subList(
                Math.max(0, results.size() - limit), results.size());
            
            GetGenerationHistoryResponse response = GetGenerationHistoryResponse.newBuilder()
                .addAllResults(limited)
                .setTotalCount(results.size())
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
