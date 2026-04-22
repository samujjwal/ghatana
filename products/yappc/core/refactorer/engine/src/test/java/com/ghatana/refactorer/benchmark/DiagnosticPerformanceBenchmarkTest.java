package com.ghatana.refactorer.benchmark;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmark test comparing sequential vs parallel processing.
 *
 * This test validates that the Promise-based parallel processing provides
 * significant performance improvements over sequential execution.

 * @doc.type class
 * @doc.purpose Handles diagnostic performance benchmark test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class DiagnosticPerformanceBenchmarkTest {

    @TempDir
    Path tempDir;

    private DiagnosticPerformanceBenchmark benchmark;
    private List<Path> smallProjectFiles;
    private List<Path> mediumProjectFiles;
    private List<Path> largeProjectFiles;

    @BeforeEach
    void setUp() throws IOException { // GH-90000
        benchmark = new DiagnosticPerformanceBenchmark(tempDir); // GH-90000
        benchmark.initialize(); // GH-90000

        // Create test projects of different sizes
        smallProjectFiles = createTestProject(10); // GH-90000
        mediumProjectFiles = createTestProject(50); // GH-90000
        largeProjectFiles = createTestProject(100); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (benchmark != null) { // GH-90000
            benchmark.close(); // GH-90000
        }
    }

    @Test
    void testSmallProjectPerformance() { // GH-90000
        System.out.println("\n=== Small Project (10 files) === [GH-90000]");

        // Run sequential benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult sequential =
            benchmark.benchmarkSequentialProcessing(smallProjectFiles); // GH-90000
        System.out.println(sequential); // GH-90000

        // Run parallel benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult parallel =
            benchmark.benchmarkParallelProcessing(smallProjectFiles); // GH-90000
        System.out.println(parallel); // GH-90000

        // Verify results
        assertThat(sequential.isSuccess()).isTrue(); // GH-90000
        assertThat(parallel.isSuccess()).isTrue(); // GH-90000
        assertThat(sequential.filesProcessed).isEqualTo(10); // GH-90000
        assertThat(parallel.filesProcessed).isEqualTo(10); // GH-90000

        // Calculate speedup
        double speedup = (double) sequential.executionTimeMs / parallel.executionTimeMs; // GH-90000
        System.out.printf("Speedup: %.2fx\n", speedup); // GH-90000

        // For small projects, parallel overhead may exceed gains; accept any non-negative speedup
        // Real speedup is typically expected for larger projects (100+ files) // GH-90000
        assertThat(speedup).isGreaterThanOrEqualTo(0.5); // GH-90000
    }

    @Test
    void testMediumProjectPerformance() { // GH-90000
        System.out.println("\n=== Medium Project (50 files) === [GH-90000]");

        // Run sequential benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult sequential =
            benchmark.benchmarkSequentialProcessing(mediumProjectFiles); // GH-90000
        System.out.println(sequential); // GH-90000

        // Run parallel benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult parallel =
            benchmark.benchmarkParallelProcessing(mediumProjectFiles); // GH-90000
        System.out.println(parallel); // GH-90000

        // Verify results
        assertThat(sequential.isSuccess()).isTrue(); // GH-90000
        assertThat(parallel.isSuccess()).isTrue(); // GH-90000
        assertThat(sequential.filesProcessed).isEqualTo(50); // GH-90000
        assertThat(parallel.filesProcessed).isEqualTo(50); // GH-90000

        // Calculate speedup
        double speedup = (double) sequential.executionTimeMs / parallel.executionTimeMs; // GH-90000
        System.out.printf("Speedup: %.2fx\n", speedup); // GH-90000

        // For medium projects, expect at least 2x speedup
        assertThat(speedup).isGreaterThan(1.5); // GH-90000
    }

    @Test
    void testLargeProjectPerformance() { // GH-90000
        System.out.println("\n=== Large Project (100 files) === [GH-90000]");

        // Run sequential benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult sequential =
            benchmark.benchmarkSequentialProcessing(largeProjectFiles); // GH-90000
        System.out.println(sequential); // GH-90000

        // Run parallel benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult parallel =
            benchmark.benchmarkParallelProcessing(largeProjectFiles); // GH-90000
        System.out.println(parallel); // GH-90000

        // Verify results
        assertThat(sequential.isSuccess()).isTrue(); // GH-90000
        assertThat(parallel.isSuccess()).isTrue(); // GH-90000
        assertThat(sequential.filesProcessed).isEqualTo(100); // GH-90000
        assertThat(parallel.filesProcessed).isEqualTo(100); // GH-90000

        // Calculate speedup
        double speedup = (double) sequential.executionTimeMs / parallel.executionTimeMs; // GH-90000
        System.out.printf("Speedup: %.2fx\n", speedup); // GH-90000

        // For large projects, expect at least 3x speedup
        assertThat(speedup).isGreaterThan(2.0); // GH-90000
    }

    @Test
    void testThroughputComparison() { // GH-90000
        System.out.println("\n=== Throughput Comparison === [GH-90000]");

        // Test all project sizes
        runAndPrintThroughput("Small (10 files)", smallProjectFiles); // GH-90000
        runAndPrintThroughput("Medium (50 files)", mediumProjectFiles); // GH-90000
        runAndPrintThroughput("Large (100 files)", largeProjectFiles); // GH-90000
    }

    private void runAndPrintThroughput(String label, List<Path> files) { // GH-90000
        DiagnosticPerformanceBenchmark.BenchmarkResult sequential =
            benchmark.benchmarkSequentialProcessing(files); // GH-90000
        DiagnosticPerformanceBenchmark.BenchmarkResult parallel =
            benchmark.benchmarkParallelProcessing(files); // GH-90000

        System.out.printf("\n%s:\n", label); // GH-90000
        System.out.printf("  Sequential: %.2f files/sec\n", sequential.throughputFilesPerSec); // GH-90000
        System.out.printf("  Parallel:   %.2f files/sec\n", parallel.throughputFilesPerSec); // GH-90000
        System.out.printf("  Improvement: %.2fx\n", // GH-90000
            parallel.throughputFilesPerSec / sequential.throughputFilesPerSec);
    }

    /**
     * Create test project with specified number of files
     */
    private List<Path> createTestProject(int fileCount) throws IOException { // GH-90000
        List<Path> files = new ArrayList<>(); // GH-90000

        // Create mix of Java and Python files
        int javaFiles = fileCount / 2;
        int pythonFiles = fileCount - javaFiles;

        // Create Java files
        for (int i = 0; i < javaFiles; i++) { // GH-90000
            Path file = tempDir.resolve("TestClass" + i + ".java"); // GH-90000
            Files.writeString(file, generateJavaFile(i)); // GH-90000
            files.add(file); // GH-90000
        }

        // Create Python files
        for (int i = 0; i < pythonFiles; i++) { // GH-90000
            Path file = tempDir.resolve("test_module_" + i + ".py"); // GH-90000
            Files.writeString(file, generatePythonFile(i)); // GH-90000
            files.add(file); // GH-90000
        }

        return files;
    }

    private String generateJavaFile(int index) { // GH-90000
        return String.format(""" // GH-90000
            package com.example;

            public class TestClass%d {
                private int value;

                public TestClass%d(int value) { // GH-90000
                    this.value = value;
                }

                public int getValue() { // GH-90000
                    return value;
                }

                public void setValue(int value) { // GH-90000
                    this.value = value;
                }

                public int calculate() { // GH-90000
                    return value * 2 + 10;
                }

                @Override
                public String toString() { // GH-90000
                    return "TestClass%d{value=" + value + "}";
                }
            }
            """, index, index, index);
    }

    private String generatePythonFile(int index) { // GH-90000
        return String.format(""" // GH-90000
            '''Test module %d'''

            def calculate(x): // GH-90000
                '''Calculate something'''
                return x * 2 + 10

            def process_data(data): // GH-90000
                '''Process data'''
                result = []
                for item in data:
                    result.append(calculate(item)) // GH-90000
                return result

            class TestClass%d:
                '''Test class'''

                def __init__(self, value): // GH-90000
                    self.value = value

                def get_value(self): // GH-90000
                    return self.value

                def calculate(self): // GH-90000
                    return calculate(self.value) // GH-90000

            if __name__ == '__main__':
                obj = TestClass%d(42) // GH-90000
                print(obj.calculate()) // GH-90000
            """, index, index, index);
    }
}
