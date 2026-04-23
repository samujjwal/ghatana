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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private static final Logger log = LogManager.getLogger(DiagnosticPerformanceBenchmark.class); // GH-90000

    private final Path testProjectRoot;
    private final Reactor reactor;
    private final PolyfixProjectContext context;
    private final JavaLanguageService javaService;
    private final PythonLanguageService pythonService;

    public DiagnosticPerformanceBenchmark(Path testProjectRoot) { // GH-90000
        this.testProjectRoot = testProjectRoot;
        this.reactor = Eventloop.builder().build(); // GH-90000

        // Create test context
        PolyfixConfig config = createDefaultConfig(); // GH-90000
        this.context = new PolyfixProjectContext( // GH-90000
            testProjectRoot,
            config,
            List.of(), // GH-90000
            null,
            log
        );

        // Initialize services
        this.javaService = new JavaLanguageService(reactor); // GH-90000
        this.pythonService = new PythonLanguageService(reactor); // GH-90000
    }

    /**
     * Initialize the eventloop runner. Call this after construction.
     */
    public void initialize() { // GH-90000
        // Start the eventloop runner so runPromise() works when used as a helper class // GH-90000
        setUpEventloop(); // GH-90000
    }

    /** Stop the eventloop runner when this benchmark is no longer needed. */
    public void close() { // GH-90000
        tearDownEventloop(); // GH-90000
    }

    /**
     * Benchmark: Process files in parallel using Promise-based API
     */
    public BenchmarkResult benchmarkParallelProcessing(List<Path> files) { // GH-90000
        long startTime = System.nanoTime(); // GH-90000
        long startMemory = getUsedMemory(); // GH-90000

        try {
            // Process files in parallel using Promises
            List<UnifiedDiagnostic> diagnostics = runPromise(() -> // GH-90000
                processFilesParallel(files) // GH-90000
            );

            long endTime = System.nanoTime(); // GH-90000
            long endMemory = getUsedMemory(); // GH-90000

            return new BenchmarkResult( // GH-90000
                "Parallel (Promise-based)", // GH-90000
                files.size(), // GH-90000
                diagnostics.size(), // GH-90000
                (endTime - startTime) / 1_000_000, // Convert to milliseconds // GH-90000
                endMemory - startMemory,
                calculateThroughput(files.size(), endTime - startTime) // GH-90000
            );
        } catch (Exception e) { // GH-90000
            log.error("Parallel benchmark failed", e); // GH-90000
            return BenchmarkResult.error("Parallel", files.size(), e.getMessage()); // GH-90000
        }
    }

    /**
     * Benchmark: Process files sequentially (simulated baseline) // GH-90000
     */
    public BenchmarkResult benchmarkSequentialProcessing(List<Path> files) { // GH-90000
        long startTime = System.nanoTime(); // GH-90000
        long startMemory = getUsedMemory(); // GH-90000

        try {
            // Process files sequentially
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>(); // GH-90000
            for (Path file : files) { // GH-90000
                List<UnifiedDiagnostic> fileDiags = runPromise(() -> // GH-90000
                    processFile(file) // GH-90000
                );
                diagnostics.addAll(fileDiags); // GH-90000
            }

            long endTime = System.nanoTime(); // GH-90000
            long endMemory = getUsedMemory(); // GH-90000

            return new BenchmarkResult( // GH-90000
                "Sequential (Baseline)", // GH-90000
                files.size(), // GH-90000
                diagnostics.size(), // GH-90000
                (endTime - startTime) / 1_000_000, // GH-90000
                endMemory - startMemory,
                calculateThroughput(files.size(), endTime - startTime) // GH-90000
            );
        } catch (Exception e) { // GH-90000
            log.error("Sequential benchmark failed", e); // GH-90000
            return BenchmarkResult.error("Sequential", files.size(), e.getMessage()); // GH-90000
        }
    }

    /**
     * Process files in parallel using Promise.toList() // GH-90000
     */
    private Promise<List<UnifiedDiagnostic>> processFilesParallel(List<Path> files) { // GH-90000
        List<Promise<List<UnifiedDiagnostic>>> promises = files.stream() // GH-90000
            .map(this::processFile) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        return Promises.toList(promises) // GH-90000
            .map(listOfLists -> listOfLists.stream() // GH-90000
                .flatMap(List::stream) // GH-90000
                .collect(Collectors.toList())); // GH-90000
    }

    /**
     * Process a single file based on its extension
     */
    private Promise<List<UnifiedDiagnostic>> processFile(Path file) { // GH-90000
        String fileName = file.getFileName().toString(); // GH-90000
        if (fileName.endsWith(".java")) {
            return javaService.diagnose(context, List.of(file)); // GH-90000
        } else if (fileName.endsWith(".py")) {
            return pythonService.diagnose(context, List.of(file)); // GH-90000
        } else {
            return Promise.of(List.of()); // GH-90000
        }
    }

    private double calculateThroughput(int fileCount, long nanoTime) { // GH-90000
        double seconds = nanoTime / 1_000_000_000.0;
        return fileCount / seconds;
    }

    private long getUsedMemory() { // GH-90000
        Runtime runtime = Runtime.getRuntime(); // GH-90000
        return runtime.totalMemory() - runtime.freeMemory(); // GH-90000
    }

    private PolyfixConfig createDefaultConfig() { // GH-90000
        return new PolyfixConfig( // GH-90000
            List.of("java", "python"), // GH-90000
            List.of(), // GH-90000
            new PolyfixConfig.Budgets(100, 1000), // GH-90000
            new PolyfixConfig.Policies(true, true, true, true), // GH-90000
            new PolyfixConfig.Tools( // GH-90000
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
        public final String errorMessage;

        public BenchmarkResult(String name, int filesProcessed, int diagnosticsFound, // GH-90000
                             long executionTimeMs, long memoryUsedBytes, double throughputFilesPerSec) {
            this.name = name;
            this.filesProcessed = filesProcessed;
            this.diagnosticsFound = diagnosticsFound;
            this.executionTimeMs = executionTimeMs;
            this.memoryUsedBytes = memoryUsedBytes;
            this.throughputFilesPerSec = throughputFilesPerSec;
            this.errorMessage = null;
        }

        private BenchmarkResult(String name, int filesProcessed, String error) { // GH-90000
            this.name = name;
            this.filesProcessed = filesProcessed;
            this.errorMessage = error;
            this.diagnosticsFound = 0;
            this.executionTimeMs = 0;
            this.memoryUsedBytes = 0;
            this.throughputFilesPerSec = 0;
        }

        public static BenchmarkResult error(String name, int filesProcessed, String error) { // GH-90000
            return new BenchmarkResult(name, filesProcessed, error); // GH-90000
        }

        public boolean isSuccess() { // GH-90000
            return errorMessage == null;
        }

        @Override
        public String toString() { // GH-90000
            if (!isSuccess()) { // GH-90000
                return String.format("%s: ERROR - %s", name, errorMessage); // GH-90000
            }
            return String.format( // GH-90000
                "%s: %d files in %d ms (%.2f files/sec), %d diagnostics, %.2f MB memory", // GH-90000
                name, filesProcessed, executionTimeMs, throughputFilesPerSec,
                diagnosticsFound, memoryUsedBytes / (1024.0 * 1024.0) // GH-90000
            );
        }
    }
}
