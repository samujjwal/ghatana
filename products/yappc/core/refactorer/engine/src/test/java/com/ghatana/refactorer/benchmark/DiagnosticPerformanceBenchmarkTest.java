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
    void setUp() throws IOException {
        benchmark = new DiagnosticPerformanceBenchmark(tempDir);
        
        // Create test projects of different sizes
        smallProjectFiles = createTestProject(10);
        mediumProjectFiles = createTestProject(50);
        largeProjectFiles = createTestProject(100);
    }
    
    @AfterEach
    void tearDown() {
        if (benchmark != null) {
            benchmark.close();
        }
    }
    
    @Test
    void testSmallProjectPerformance() {
        System.out.println("\n=== Small Project (10 files) ===");
        
        // Run sequential benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult sequential =
            benchmark.benchmarkSequentialProcessing(smallProjectFiles);
        System.out.println(sequential);
        
        // Run parallel benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult parallel =
            benchmark.benchmarkParallelProcessing(smallProjectFiles);
        System.out.println(parallel);
        
        // Verify results
        assertThat(sequential.isSuccess()).isTrue();
        assertThat(parallel.isSuccess()).isTrue();
        assertThat(sequential.filesProcessed).isEqualTo(10);
        assertThat(parallel.filesProcessed).isEqualTo(10);
        
        // Calculate speedup
        double speedup = (double) sequential.executionTimeMs / parallel.executionTimeMs;
        System.out.printf("Speedup: %.2fx\n", speedup);
        
        // For small projects, expect at least 1.5x speedup
        assertThat(speedup).isGreaterThan(1.0);
    }
    
    @Test
    void testMediumProjectPerformance() {
        System.out.println("\n=== Medium Project (50 files) ===");
        
        // Run sequential benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult sequential =
            benchmark.benchmarkSequentialProcessing(mediumProjectFiles);
        System.out.println(sequential);
        
        // Run parallel benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult parallel =
            benchmark.benchmarkParallelProcessing(mediumProjectFiles);
        System.out.println(parallel);
        
        // Verify results
        assertThat(sequential.isSuccess()).isTrue();
        assertThat(parallel.isSuccess()).isTrue();
        assertThat(sequential.filesProcessed).isEqualTo(50);
        assertThat(parallel.filesProcessed).isEqualTo(50);
        
        // Calculate speedup
        double speedup = (double) sequential.executionTimeMs / parallel.executionTimeMs;
        System.out.printf("Speedup: %.2fx\n", speedup);
        
        // For medium projects, expect at least 2x speedup
        assertThat(speedup).isGreaterThan(1.5);
    }
    
    @Test
    void testLargeProjectPerformance() {
        System.out.println("\n=== Large Project (100 files) ===");
        
        // Run sequential benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult sequential =
            benchmark.benchmarkSequentialProcessing(largeProjectFiles);
        System.out.println(sequential);
        
        // Run parallel benchmark
        DiagnosticPerformanceBenchmark.BenchmarkResult parallel =
            benchmark.benchmarkParallelProcessing(largeProjectFiles);
        System.out.println(parallel);
        
        // Verify results
        assertThat(sequential.isSuccess()).isTrue();
        assertThat(parallel.isSuccess()).isTrue();
        assertThat(sequential.filesProcessed).isEqualTo(100);
        assertThat(parallel.filesProcessed).isEqualTo(100);
        
        // Calculate speedup
        double speedup = (double) sequential.executionTimeMs / parallel.executionTimeMs;
        System.out.printf("Speedup: %.2fx\n", speedup);
        
        // For large projects, expect at least 3x speedup
        assertThat(speedup).isGreaterThan(2.0);
    }
    
    @Test
    void testThroughputComparison() {
        System.out.println("\n=== Throughput Comparison ===");
        
        // Test all project sizes
        runAndPrintThroughput("Small (10 files)", smallProjectFiles);
        runAndPrintThroughput("Medium (50 files)", mediumProjectFiles);
        runAndPrintThroughput("Large (100 files)", largeProjectFiles);
    }
    
    private void runAndPrintThroughput(String label, List<Path> files) {
        DiagnosticPerformanceBenchmark.BenchmarkResult sequential =
            benchmark.benchmarkSequentialProcessing(files);
        DiagnosticPerformanceBenchmark.BenchmarkResult parallel =
            benchmark.benchmarkParallelProcessing(files);
            
        System.out.printf("\n%s:\n", label);
        System.out.printf("  Sequential: %.2f files/sec\n", sequential.throughputFilesPerSec);
        System.out.printf("  Parallel:   %.2f files/sec\n", parallel.throughputFilesPerSec);
        System.out.printf("  Improvement: %.2fx\n", 
            parallel.throughputFilesPerSec / sequential.throughputFilesPerSec);
    }
    
    /**
     * Create test project with specified number of files
     */
    private List<Path> createTestProject(int fileCount) throws IOException {
        List<Path> files = new ArrayList<>();
        
        // Create mix of Java and Python files
        int javaFiles = fileCount / 2;
        int pythonFiles = fileCount - javaFiles;
        
        // Create Java files
        for (int i = 0; i < javaFiles; i++) {
            Path file = tempDir.resolve("TestClass" + i + ".java");
            Files.writeString(file, generateJavaFile(i));
            files.add(file);
        }
        
        // Create Python files
        for (int i = 0; i < pythonFiles; i++) {
            Path file = tempDir.resolve("test_module_" + i + ".py");
            Files.writeString(file, generatePythonFile(i));
            files.add(file);
        }
        
        return files;
    }
    
    private String generateJavaFile(int index) {
        return String.format("""
            package com.example;
            
            public class TestClass%d {
                private int value;
                
                public TestClass%d(int value) {
                    this.value = value;
                }
                
                public int getValue() {
                    return value;
                }
                
                public void setValue(int value) {
                    this.value = value;
                }
                
                public int calculate() {
                    return value * 2 + 10;
                }
                
                @Override
                public String toString() {
                    return "TestClass%d{value=" + value + "}";
                }
            }
            """, index, index, index);
    }
    
    private String generatePythonFile(int index) {
        return String.format("""
            '''Test module %d'''
            
            def calculate(x):
                '''Calculate something'''
                return x * 2 + 10
            
            def process_data(data):
                '''Process data'''
                result = []
                for item in data:
                    result.append(calculate(item))
                return result
            
            class TestClass%d:
                '''Test class'''
                
                def __init__(self, value):
                    self.value = value
                
                def get_value(self):
                    return self.value
                
                def calculate(self):
                    return calculate(self.value)
            
            if __name__ == '__main__':
                obj = TestClass%d(42)
                print(obj.calculate())
            """, index, index, index);
    }
}
