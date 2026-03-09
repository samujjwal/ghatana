/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.ai.AIIntegrationService;
import com.ghatana.yappc.api.testing.dto.*;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Test Generation Service - AI-powered test generation.
 * 
 * <p>Features:
 * <ul>
 *   <li>Unit test generation (JUnit, Jest, pytest)</li>
 *   <li>Integration test generation</li>
 *   <li>Coverage analysis and gap detection</li>
 *   <li>Test template library</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose AI-powered test generation
 * @doc.layer product
 * @doc.pattern Service
 */
public class TestGenerationService {
    
    private static final Logger log = LoggerFactory.getLogger(TestGenerationService.class);
    
    private static final Map<String, String> TEST_FRAMEWORKS = Map.of(
            "java", "junit",
            "javascript", "jest",
            "typescript", "jest",
            "python", "pytest",
            "go", "testing"
    );
    
    private final AIIntegrationService aiService;
    private final Executor blockingExecutor;
    private final ObjectMapper objectMapper;
    
    @Inject
    public TestGenerationService(AIIntegrationService aiService) {
        this.aiService = aiService;
        this.blockingExecutor = Executors.newFixedThreadPool(4);
        this.objectMapper = JsonUtils.getDefaultMapper();
    }
    
    /**
     * Generate tests from source code using AI.
     * 
     * @param request Test generation request
     * @return Test generation result with generated tests
     */
    public Promise<TestGenerationResult> generateTests(TestGenerationRequest request) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                Path sourcePath = Path.of(request.sourcePath());
                if (!Files.exists(sourcePath)) {
                    throw new IllegalArgumentException("Source path does not exist: " + request.sourcePath());
                }
                
                String sourceCode = Files.readString(sourcePath);
                String language = detectLanguage(sourcePath.getFileName().toString());
                String framework = request.framework() != null ? 
                        request.framework() : 
                        TEST_FRAMEWORKS.getOrDefault(language, "junit");
                
                List<GeneratedTest> generatedTests = new ArrayList<>();
                
                if ("unit".equals(request.testType()) || "all".equals(request.testType())) {
                    generatedTests.addAll(generateUnitTests(sourceCode, language, framework));
                }
                
                if ("integration".equals(request.testType()) || "all".equals(request.testType())) {
                    generatedTests.addAll(generateIntegrationTests(sourceCode, language, framework));
                }
                
                TestStatistics stats = calculateStatistics(generatedTests);
                
                return new TestGenerationResult(generatedTests, stats, request.outputPath());
                
            } catch (IOException e) {
                log.error("Failed to generate tests", e);
                throw new RuntimeException("Test generation failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Analyze test coverage and identify gaps.
     * 
     * @param request Coverage analysis request
     * @return Coverage report with gaps
     */
    public Promise<CoverageReport> analyzeCoverage(CoverageAnalysisRequest request) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                Path projectPath = Path.of(request.projectPath());
                
                List<SourceFile> sourceFiles = scanSourceFiles(projectPath);
                List<TestFile> testFiles = scanTestFiles(projectPath);
                
                Map<String, TestFile> testsBySource = testFiles.stream()
                        .collect(Collectors.toMap(
                                TestFile::sourcePath,
                                tf -> tf,
                                (a, b) -> a));
                
                List<CoverageGap> gaps = new ArrayList<>();
                
                for (SourceFile sourceFile : sourceFiles) {
                    TestFile testFile = testsBySource.get(sourceFile.path());
                    
                    if (testFile == null) {
                        gaps.add(new CoverageGap(
                                sourceFile.path(),
                                "MISSING_TESTS",
                                "No test file found for source: " + sourceFile.path(),
                                "HIGH"));
                    } else {
                        gaps.addAll(analyzeCoverageGaps(sourceFile, testFile));
                    }
                }
                
                double overallCoverage = calculateOverallCoverage(sourceFiles, testFiles);
                
                return new CoverageReport(overallCoverage, gaps, 
                        sourceFiles.size(), testFiles.size());
                
            } catch (IOException e) {
                log.error("Failed to analyze coverage", e);
                throw new RuntimeException("Coverage analysis failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * List available test templates.
     * 
     * @param framework Optional framework filter
     * @return List of test templates
     */
    public Promise<List<TestTemplate>> listTemplates(String framework) {
        return Promise.of(getBuiltInTemplates(framework));
    }
    
    // ============================================================================
    // Private Helper Methods
    // ============================================================================
    
    private List<GeneratedTest> generateUnitTests(String sourceCode, String language, String framework) {
        String prompt = String.format("""
                Generate comprehensive unit tests for the following %s code using %s framework.
                
                Requirements:
                - Test all public methods
                - Include edge cases and error scenarios
                - Use proper assertions
                - Add descriptive test names
                - Include setup and teardown if needed
                
                Source Code:
                %s
                
                Generate the test code only, no explanations.
                """, language, framework, sourceCode);
        
        String generatedCode = aiService.generateCode(prompt);
        
        return parseGeneratedTests(generatedCode, "unit", framework);
    }
    
    private List<GeneratedTest> generateIntegrationTests(String sourceCode, String language, String framework) {
        String prompt = String.format("""
                Generate integration tests for the following %s code using %s framework.
                
                Requirements:
                - Test component interactions
                - Include database/API mocking where appropriate
                - Test error handling and rollback scenarios
                - Use proper test containers if applicable
                
                Source Code:
                %s
                
                Generate the test code only.
                """, language, framework, sourceCode);
        
        String generatedCode = aiService.generateCode(prompt);
        
        return parseGeneratedTests(generatedCode, "integration", framework);
    }
    
    private List<GeneratedTest> parseGeneratedTests(String code, String type, String framework) {
        List<GeneratedTest> tests = new ArrayList<>();
        
        // Extract test methods based on framework
        Pattern pattern = switch (framework) {
            case "junit" -> Pattern.compile("@Test\\s+public\\s+void\\s+(\\w+)\\s*\\(");
            case "jest" -> Pattern.compile("(?:test|it)\\s*\\(['\"]([^'\"]+)['\"]");
            case "pytest" -> Pattern.compile("def\\s+(test_\\w+)\\s*\\(");
            default -> Pattern.compile("(\\w+)");
        };
        
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            String testName = matcher.group(1);
            tests.add(new GeneratedTest(
                    testName,
                    type,
                    code,
                    framework,
                    countLines(code)));
        }
        
        if (tests.isEmpty()) {
            tests.add(new GeneratedTest(
                    "GeneratedTest",
                    type,
                    code,
                    framework,
                    countLines(code)));
        }
        
        return tests;
    }
    
    private List<SourceFile> scanSourceFiles(Path projectPath) throws IOException {
        return Files.walk(projectPath)
                .filter(path -> !path.toString().contains("/test/"))
                .filter(path -> isSourceFile(path.getFileName().toString()))
                .map(path -> new SourceFile(
                        projectPath.relativize(path).toString(),
                        detectLanguage(path.getFileName().toString()),
                        countMethods(path)))
                .collect(Collectors.toList());
    }
    
    private List<TestFile> scanTestFiles(Path projectPath) throws IOException {
        return Files.walk(projectPath)
                .filter(path -> path.toString().contains("/test/"))
                .filter(path -> isTestFile(path.getFileName().toString()))
                .map(path -> {
                    String testPath = projectPath.relativize(path).toString();
                    String sourcePath = deriveSourcePath(testPath);
                    return new TestFile(testPath, sourcePath, countTestMethods(path));
                })
                .collect(Collectors.toList());
    }
    
    private List<CoverageGap> analyzeCoverageGaps(SourceFile sourceFile, TestFile testFile) {
        List<CoverageGap> gaps = new ArrayList<>();
        
        if (testFile.testCount() == 0) {
            gaps.add(new CoverageGap(
                    sourceFile.path(),
                    "NO_TESTS",
                    "Test file exists but contains no test methods",
                    "HIGH"));
        } else if (testFile.testCount() < sourceFile.methodCount()) {
            gaps.add(new CoverageGap(
                    sourceFile.path(),
                    "INSUFFICIENT_TESTS",
                    String.format("Only %d tests for %d methods", 
                            testFile.testCount(), sourceFile.methodCount()),
                    "MEDIUM"));
        }
        
        return gaps;
    }
    
    private double calculateOverallCoverage(List<SourceFile> sourceFiles, List<TestFile> testFiles) {
        int totalMethods = sourceFiles.stream()
                .mapToInt(SourceFile::methodCount)
                .sum();
        
        int totalTests = testFiles.stream()
                .mapToInt(TestFile::testCount)
                .sum();
        
        return totalMethods > 0 ? (double) totalTests / totalMethods * 100.0 : 0.0;
    }
    
    private TestStatistics calculateStatistics(List<GeneratedTest> tests) {
        long unitTests = tests.stream().filter(t -> "unit".equals(t.type())).count();
        long integrationTests = tests.stream().filter(t -> "integration".equals(t.type())).count();
        int totalLines = tests.stream().mapToInt(GeneratedTest::lines).sum();
        
        return new TestStatistics((int) unitTests, (int) integrationTests, totalLines);
    }
    
    private List<TestTemplate> getBuiltInTemplates(String framework) {
        List<TestTemplate> templates = new ArrayList<>();
        
        if (framework == null || "junit".equals(framework)) {
            templates.add(new TestTemplate(
                    "junit-unit",
                    "JUnit 5 Unit Test",
                    "junit",
                    "Standard JUnit 5 unit test template with @BeforeEach and @Test"));
        }
        
        if (framework == null || "jest".equals(framework)) {
            templates.add(new TestTemplate(
                    "jest-unit",
                    "Jest Unit Test",
                    "jest",
                    "Standard Jest unit test with describe/it blocks"));
        }
        
        if (framework == null || "pytest".equals(framework)) {
            templates.add(new TestTemplate(
                    "pytest-unit",
                    "Pytest Unit Test",
                    "pytest",
                    "Standard pytest test with fixtures"));
        }
        
        return templates;
    }
    
    private String detectLanguage(String filename) {
        if (filename.endsWith(".java")) return "java";
        if (filename.endsWith(".js")) return "javascript";
        if (filename.endsWith(".ts")) return "typescript";
        if (filename.endsWith(".py")) return "python";
        if (filename.endsWith(".go")) return "go";
        return "unknown";
    }
    
    private boolean isSourceFile(String filename) {
        return filename.endsWith(".java") || filename.endsWith(".js") || 
               filename.endsWith(".ts") || filename.endsWith(".py") || 
               filename.endsWith(".go");
    }
    
    private boolean isTestFile(String filename) {
        return filename.contains("Test") || filename.contains("test") || 
               filename.contains("Spec") || filename.contains("spec");
    }
    
    private String deriveSourcePath(String testPath) {
        return testPath.replace("/test/", "/main/")
                .replace("Test.java", ".java")
                .replace(".test.js", ".js")
                .replace(".spec.ts", ".ts")
                .replace("test_", "");
    }
    
    private int countMethods(Path file) {
        try {
            String content = Files.readString(file);
            String filename = file.getFileName().toString();
            
            if (filename.endsWith(".java")) {
                return (int) Pattern.compile("(public|protected|private)\\s+\\w+\\s+\\w+\\s*\\(")
                        .matcher(content).results().count();
            }
            // Add patterns for other languages as needed
            return 1;
        } catch (IOException e) {
            return 0;
        }
    }
    
    private int countTestMethods(Path file) {
        try {
            String content = Files.readString(file);
            String filename = file.getFileName().toString();
            
            if (filename.endsWith(".java")) {
                return (int) Pattern.compile("@Test").matcher(content).results().count();
            } else if (filename.endsWith(".js") || filename.endsWith(".ts")) {
                return (int) Pattern.compile("(?:test|it)\\s*\\(").matcher(content).results().count();
            } else if (filename.endsWith(".py")) {
                return (int) Pattern.compile("def\\s+test_").matcher(content).results().count();
            }
            return 0;
        } catch (IOException e) {
            return 0;
        }
    }
    
    private int countLines(String code) {
        return code.split("\n").length;
    }
}

// Supporting record classes removed (using external files)

