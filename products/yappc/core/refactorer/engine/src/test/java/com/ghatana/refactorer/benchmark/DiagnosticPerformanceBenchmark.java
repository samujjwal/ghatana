package com.ghatana.refactorer.benchmark;

import com.ghatana.refactorer.languages.JavaLanguageService;
import com.ghatana.refactorer.languages.PythonLanguageService;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.reactor.Reactor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Performance benchmark comparing sequential vs parallel file processing.
 * 
 * This benchmark measures the performance improvements achieved through
 * ActiveJ Promise-based parallel processing vs sequential execution.
 
 * @doc.type class
 * @doc.purpose Handles diagnostic performance benchmark operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class DiagnosticPerformanceBenchmark extends EventloopTestBase {
    private static final Logger log = LogManager.getLogger(DiagnosticPerformanceBenchmark.class);
    
    private final Path testProjectRoot;
    private final Reactor reactor;
    private final PolyfixProjectContext context;
    private final JavaLanguageService javaService;
    private final PythonLanguageService pythonService;
    
    public DiagnosticPerformanceBenchmark(Path testProjectRoot) {
        this.testProjectRoot = testProjectRoot;
        this.reactor = Eventloop.builder().build();
        
        // Create test context
        PolyfixConfig config = createDefaultConfig();
        this.context = new PolyfixProjectContext(
            testProjectRoot,
            config,
            List.of(),
            null,
            log
        );
        
        // Initialize services
        this.javaService = new JavaLanguageService(reactor);
        this.pythonService = new PythonLanguageService(reactor);
    }
    
    /**
     * Benchmark: Process files in parallel using Promise-based API
     */
    public BenchmarkResult benchmarkParallelProcessing(List<Path> files) {
        long startTime = System.nanoTime();
        long startMemory = getUsedMemory();
        
        try {
            // Process files in parallel using Promises
            List<UnifiedDiagnostic> diagnostics = runPromise(() -> 
                processFilesParallel(files)
            );
            
            long endTime = System.nanoTime();
            long endMemory = getUsedMemory();
            
            return new BenchmarkResult(
                "Parallel (Promise-based)",
                files.size(),
                diagnostics.size(),
                (endTime - startTime) / 1_000_000, // Convert to milliseconds
                endMemory - startMemory,
                calculateThroughput(files.size(), endTime - startTime)
            );
        } catch (Exception e) {
            log.error("Parallel benchmark failed", e);
            return BenchmarkResult.error("Parallel", files.size(), e.getMessage());
        }
    }
    
    /**
     * Benchmark: Process files sequentially (simulated baseline)
     */
    public BenchmarkResult benchmarkSequentialProcessing(List<Path> files) {
        long startTime = System.nanoTime();
        long startMemory = getUsedMemory();
        
        try {
            // Process files sequentially
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>();
            for (Path file : files) {
                List<UnifiedDiagnostic> fileDiags = runPromise(() -> 
                    processFile(file)
                );
                diagnostics.addAll(fileDiags);
            }
            
            long endTime = System.nanoTime();
            long endMemory = getUsedMemory();
            
            return new BenchmarkResult(
                "Sequential (Baseline)",
                files.size(),
                diagnostics.size(),
                (endTime - startTime) / 1_000_000,
                endMemory - startMemory,
                calculateThroughput(files.size(), endTime - startTime)
            );
        } catch (Exception e) {
            log.error("Sequential benchmark failed", e);
            return BenchmarkResult.error("Sequential", files.size(), e.getMessage());
        }
    }
    
    /**
     * Process files in parallel using Promise.toList()
     */
    private Promise<List<UnifiedDiagnostic>> processFilesParallel(List<Path> files) {
        List<Promise<List<UnifiedDiagnostic>>> promises = files.stream()
            .map(this::processFile)
            .collect(Collectors.toList());
            
        return Promises.toList(promises)
            .map(listOfLists -> listOfLists.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList()));
    }
    
    /**
     * Process a single file based on its extension
     */
    private Promise<List<UnifiedDiagnostic>> processFile(Path file) {
        String fileName = file.getFileName().toString();
        if (fileName.endsWith(".java")) {
            return javaService.diagnose(context, List.of(file));
        } else if (fileName.endsWith(".py")) {
            return pythonService.diagnose(context, List.of(file));
        } else {
            return Promise.of(List.of());
        }
    }
    
    /**
     * Helper to run Promise in test context
     */
    private <T> T runPromise(java.util.function.Supplier<Promise<T>> promiseSupplier) {
        try {
            CompletableFuture<T> future = new CompletableFuture<>();
            reactor.submit(() -> {
                promiseSupplier.get()
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            future.complete(result);
                        }
                    });
            });
            ((Eventloop) reactor).run();
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Promise execution failed", e);
        }
    }
    
    private double calculateThroughput(int fileCount, long nanoTime) {
        double seconds = nanoTime / 1_000_000_000.0;
        return fileCount / seconds;
    }
    
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    private PolyfixConfig createDefaultConfig() {
        return new PolyfixConfig(
            List.of("java", "python"),
            List.of(),
            new PolyfixConfig.Budgets(100, 1000),
            new PolyfixConfig.Policies(true, true, true, true),
            new PolyfixConfig.Tools(
                "node", "eslint", "tsc", "prettier",
                "ruff", "black", "mypy",
                "shellcheck", "shfmt",
                "cargo", "rustfmt",
                "semgrep"
            )
        );
    }
    
    /**
     * Result of a benchmark run
     */
    public static class BenchmarkResult {
        public final String name;
        public final int filesProcessed;
        public final int diagnosticsFound;
        public final long executionTimeMs;
        public final long memoryUsedBytes;
        public final double throughputFilesPerSec;
        public final String error;
        
        public BenchmarkResult(String name, int filesProcessed, int diagnosticsFound,
                             long executionTimeMs, long memoryUsedBytes, double throughputFilesPerSec) {
            this.name = name;
            this.filesProcessed = filesProcessed;
            this.diagnosticsFound = diagnosticsFound;
            this.executionTimeMs = executionTimeMs;
            this.memoryUsedBytes = memoryUsedBytes;
            this.throughputFilesPerSec = throughputFilesPerSec;
            this.error = null;
        }
        
        private BenchmarkResult(String name, int filesProcessed, String error) {
            this.name = name;
            this.filesProcessed = filesProcessed;
            this.error = error;
            this.diagnosticsFound = 0;
            this.executionTimeMs = 0;
            this.memoryUsedBytes = 0;
            this.throughputFilesPerSec = 0;
        }
        
        public static BenchmarkResult error(String name, int filesProcessed, String error) {
            return new BenchmarkResult(name, filesProcessed, error);
        }
        
        public boolean isSuccess() {
            return error == null;
        }
        
        @Override
        public String toString() {
            if (!isSuccess()) {
                return String.format("%s: ERROR - %s", name, error);
            }
            return String.format(
                "%s: %d files in %d ms (%.2f files/sec), %d diagnostics, %.2f MB memory",
                name, filesProcessed, executionTimeMs, throughputFilesPerSec,
                diagnosticsFound, memoryUsedBytes / (1024.0 * 1024.0)
            );
        }
    }
}
