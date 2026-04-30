/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Anti-Theatre Java Test Audit
 *
 * Audit script to identify and report anti-theatre patterns in Java tests.
 * These are tests that use disabled tests, empty methods, or hardcoded assertions.
 *
 * Usage: gradle :runAntiTheatreJavaAudit
 *
 * @doc.type class
 * @doc.purpose Anti-theatre audit for Java tests
 * @doc.layer quality-engineering
 */
package com.ghatana.yappc.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anti-theatre patterns to detect in Java tests
 */
record AntiTheatrePattern(
    Pattern pattern,
    String type,
    String description,
    String severity
) {}

/**
 * Issue found during audit
 */
record TestIssue(
    String file,
    int line,
    String type,
    String description,
    String severity
) {}

/**
 * Audit result summary
 */
record AuditResult(
    int totalFiles,
    List<TestIssue> issues,
    Summary summary
) {}

record Summary(int errors, int warnings, int info) {}

/**
 * Anti-Theatre Java Test Audit
 *
 * Scans Java test files for anti-theatre patterns:
 * - @Disabled or @Ignore annotations
 * - Empty test methods
 * - Hardcoded assertions
 * - Mock-only tests without real assertions
 */
public class AntiTheatreJavaAudit {

    private static final List<AntiTheatrePattern> ANTI_THEATRE_PATTERNS = List.of(
        new AntiTheatrePattern(
            Pattern.compile("@Disabled|@Ignore|@Disabled\\(.*?\\)"),
            "disabled-test",
            "Test is disabled - verify this is intentional",
            "warning"
        ),
        new AntiTheatrePattern(
            Pattern.compile("@Test\\s*\\n\\s*public\\s+void\\s+\\w+\\(\\)\\s*\\{\\s*\\}"),
            "empty-test",
            "Empty test method with no assertions",
            "error"
        ),
        new AntiTheatrePattern(
            Pattern.compile("assertEquals\\([^,]+,\\s*\"[^\"]*\"\\)"),
            "hardcoded-assertion",
            "Hardcoded string in assertion - verify this tests actual behavior",
            "info"
        ),
        new AntiTheatrePattern(
            Pattern.compile("assertTrue\\(true\\)|assertFalse\\(false\\)"),
            "tautology-assertion",
            "Tautological assertion that always passes",
            "error"
        ),
        new AntiTheatrePattern(
            Pattern.compile("when\\([^)]+\\)\\.thenReturn\\(null\\)"),
            "null-mock-return",
            "Mock returning null - may not test real integration",
            "warning"
        )
    );

    /**
     * Audit a single Java test file
     */
    public List<TestIssue> auditFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String[] lines = content.split("\n");
        List<TestIssue> issues = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (AntiTheatrePattern atp : ANTI_THEATRE_PATTERNS) {
                Matcher matcher = atp.pattern().matcher(line);
                while (matcher.find()) {
                    issues.add(new TestIssue(
                        filePath.toString(),
                        i + 1,
                        atp.type(),
                        atp.description(),
                        atp.severity()
                    ));
                }
            }
        }

        return issues;
    }

    /**
     * Run audit on all Java test files in a directory
     */
    public AuditResult runAudit(Path testDir) throws IOException {
        List<Path> testFiles = new ArrayList<>();
        
        Files.walk(testDir)
            .filter(path -> path.toString().endsWith("Test.java"))
            .forEach(testFiles::add);

        List<TestIssue> allIssues = new ArrayList<>();
        for (Path file : testFiles) {
            try {
                allIssues.addAll(auditFile(file));
            } catch (IOException e) {
                System.err.println("Failed to audit file: " + file + " - " + e.getMessage());
            }
        }

        Summary summary = new Summary(
            (int) allIssues.stream().filter(i -> i.severity().equals("error")).count(),
            (int) allIssues.stream().filter(i -> i.severity().equals("warning")).count(),
            (int) allIssues.stream().filter(i -> i.severity().equals("info")).count()
        );

        return new AuditResult(testFiles.size(), allIssues, summary);
    }

    /**
     * Generate audit report
     */
    public String generateReport(AuditResult result) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Anti-Theatre Java Test Audit Report\n\n");
        sb.append("## Summary\n");
        sb.append("- Total test files scanned: ").append(result.totalFiles()).append("\n");
        sb.append("- Total issues found: ").append(result.issues().size()).append("\n");
        sb.append("  - Errors: ").append(result.summary().errors()).append("\n");
        sb.append("  - Warnings: ").append(result.summary().warnings()).append("\n");
        sb.append("  - Info: ").append(result.summary().info()).append("\n\n");

        if (!result.issues().isEmpty()) {
            sb.append("## Issues\n\n");
            
            // Group by file
            result.issues().stream()
                .collect(java.util.stream.Collectors.groupingBy(TestIssue::file))
                .forEach((file, issues) -> {
                    sb.append("### ").append(file).append("\n\n");
                    for (TestIssue issue : issues) {
                        sb.append("- Line ").append(issue.line())
                          .append(": [").append(issue.severity().toUpperCase()).append("] ")
                          .append(issue.type()).append("\n");
                        sb.append("  ").append(issue.description()).append("\n");
                    }
                    sb.append("\n");
                });
        } else {
            sb.append("✅ No anti-theatre test issues found!\n\n");
        }

        sb.append("## Recommendations\n\n");
        sb.append("1. Review all warnings and errors manually\n");
        sb.append("2. Re-enable disabled tests or add justification comments\n");
        sb.append("3. Add meaningful assertions to empty test methods\n");
        sb.append("4. Replace hardcoded assertions with actual test data\n");
        sb.append("5. Ensure mocks return realistic data that matches production\n");

        return sb.toString();
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) throws IOException {
        Path testDir = args.length > 0 
            ? Paths.get(args[0]) 
            : Paths.get(System.getProperty("user.dir")).resolve("src/test/java");

        System.out.println("Running anti-theatre audit on: " + testDir);
        
        AntiTheatreJavaAudit auditor = new AntiTheatreJavaAudit();
        AuditResult result = auditor.runAudit(testDir);
        String report = auditor.generateReport(result);

        System.out.println(report);

        // Exit with error code if errors found
        if (result.summary().errors() > 0) {
            System.exit(1);
        }
    }
}
