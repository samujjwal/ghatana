/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure Module
 */
package com.ghatana.yappc.infrastructure.datacloud.adapter;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Pattern-based static analysis scanner that detects known insecure code constructs
 * within Java, JavaScript, TypeScript, and Python source files.
 *
 * <p>Detection rules cover OWASP Top-10 categories:
 * <ul>
 *   <li>A01 — Command injection ({@code "sh", "-c"} in ProcessBuilder; {@code Runtime.exec})
 *   <li>A02 — Hardcoded secrets (password/secret/apikey literals in assignments)
 *   <li>A03 — SQL injection (string-concatenated SQL fragments)
 *   <li>A04 — Insecure deserialization ({@code ObjectInputStream.readObject} without validation)
 *   <li>A05 — Security misconfiguration ({@code System.exit} in non-main classes)
 * </ul>
 *
 * <p>Scanning is blocking I/O performed inside an {@link Executor} wrapper so the
 * ActiveJ event loop is never blocked.
 *
 * @doc.type class
 * @doc.purpose Static analysis scanner detecting insecure patterns in project source code
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class StaticAnalysisScanner implements SecurityScanner {

    private static final Logger log = LoggerFactory.getLogger(StaticAnalysisScanner.class);
    private static final String SCANNER_NAME = "StaticAnalysisScanner";

    private static final int MAX_FILE_SIZE_BYTES = 1_048_576; // 1 MB — skip very large files
    private static final int MAX_DEPTH = 15;

    private final Executor executor;

    /**
     * Ordered list of pattern-based rules applied to every scanned source line.
     * Each entry is: (ruleId, severity, compiledPattern, description template).
     */
    private static final List<ScanRule> RULES = List.of(

        // A01 — Command Injection
        new ScanRule("CI-001", SecurityReport.Severity.CRITICAL,
            Pattern.compile("\"sh\"\\s*,\\s*\"-c\""),
            "sh -c with dynamic content — potential command injection"),

        new ScanRule("CI-002", SecurityReport.Severity.HIGH,
            Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec\\("),
            "Runtime.exec() — prefer ProcessBuilder with explicit arg list"),

        new ScanRule("CI-003", SecurityReport.Severity.HIGH,
            Pattern.compile("ProcessBuilder\\s*\\(\\s*[\"']\\s*sh\\s*[\"']"),
            "ProcessBuilder with shell interpreter — potential command injection"),

        // A02 — Hardcoded Secrets
        new ScanRule("HS-001", SecurityReport.Severity.HIGH,
            Pattern.compile("(?i)(password|secret|api_key|apikey|token)\\s*=\\s*[\"'][^\"']{4,}[\"']"),
            "Hardcoded credential-like string assignment detected"),

        new ScanRule("HS-002", SecurityReport.Severity.HIGH,
            Pattern.compile("(?i)private\\s+static\\s+final\\s+String\\s+(PASSWORD|SECRET|API_KEY|TOKEN)\\s*=\\s*[\"']"),
            "Hardcoded credential constant — use environment variable or secrets manager"),

        // A03 — SQL Injection
        new ScanRule("SI-001", SecurityReport.Severity.HIGH,
            Pattern.compile("(?i)(executeQuery|executeUpdate|execute)\\s*\\(\\s*[\"'][^\"]*\\+"),
            "String-concatenated SQL statement — use PreparedStatement with parameters"),

        new ScanRule("SI-002", SecurityReport.Severity.MEDIUM,
            Pattern.compile("(?i)\"SELECT .* FROM .* WHERE .*\"\\s*\\+"),
            "SQL string built with concatenation — potential injection vector"),

        // A04 — Insecure Deserialization
        new ScanRule("ID-001", SecurityReport.Severity.HIGH,
            Pattern.compile("new\\s+ObjectInputStream"),
            "ObjectInputStream usage — validate input before deserialisation to avoid RCE"),

        // A05 — Dangerous APIs
        new ScanRule("DA-001", SecurityReport.Severity.MEDIUM,
            Pattern.compile("System\\.exit\\s*\\("),
            "System.exit() — inappropriate in library/service code; use controlled shutdown"),

        new ScanRule("DA-002", SecurityReport.Severity.LOW,
            Pattern.compile("new\\s+Random\\s*\\(\\)(?!.*secure)"),
            "java.util.Random usage — use SecureRandom for security-sensitive contexts"),

        // A08 — Integrity Failures
        new ScanRule("IF-001", SecurityReport.Severity.MEDIUM,
            Pattern.compile("(?i)\\.setHostnameVerifier\\s*\\(.*ALLOW_ALL"),
            "Hostname verification disabled — SSL/TLS verification must not be bypassed"),

        new ScanRule("IF-002", SecurityReport.Severity.HIGH,
            Pattern.compile("(?i)trustAllCerts|TrustAllManager|NullTrustManager"),
            "Trust-all certificate manager detected — bypasses TLS chain validation")
    );

    public StaticAnalysisScanner(Executor executor) {
        this.executor = executor;
    }

    @Override
    public Promise<SecurityReport> scan(Path projectPath) {
        return Promise.ofBlocking(executor, () -> doScan(projectPath));
    }

    // ─── Internal scanning logic ──────────────────────────────────────────────

    private SecurityReport doScan(Path projectPath) {
        if (!Files.isDirectory(projectPath)) {
            return SecurityReport.error(
                "Project path does not exist or is not a directory: " + projectPath,
                SCANNER_NAME);
        }

        List<SecurityReport.Finding> findings = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(projectPath, MAX_DEPTH, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(this::isSourceFile)
                .forEach(file -> scanFile(file, findings));
        } catch (IOException e) {
            log.warn("StaticAnalysisScanner: I/O error walking {}: {}", projectPath, e.getMessage());
            return SecurityReport.error("I/O error during scan: " + e.getMessage(), SCANNER_NAME);
        }

        if (findings.isEmpty()) {
            log.info("StaticAnalysisScanner: CLEAN — no issues found in {}", projectPath);
        } else {
            log.warn("StaticAnalysisScanner: {} finding(s) in {}", findings.size(), projectPath);
        }

        return SecurityReport.withFindings(findings, SCANNER_NAME);
    }

    private void scanFile(Path file, List<SecurityReport.Finding> findings) {
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_SIZE_BYTES) {
                log.debug("StaticAnalysisScanner: skipping oversized file {} ({} bytes)", file, size);
                return;
            }
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (ScanRule rule : RULES) {
                    if (rule.pattern().matcher(line).find()) {
                        String location = file + ":" + (i + 1);
                        findings.add(new SecurityReport.Finding(
                            rule.ruleId(),
                            rule.message() + " [" + location + "]",
                            rule.severity(),
                            location));
                    }
                }
            }
        } catch (IOException e) {
            log.debug("StaticAnalysisScanner: could not read {} — {}", file, e.getMessage());
        }
    }

    private boolean isSourceFile(Path path) {
        if (!Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".ts")
            || name.endsWith(".py")  || name.endsWith(".kt");
    }

    // ─── Rule model ───────────────────────────────────────────────────────────

    private record ScanRule(
            String ruleId,
            SecurityReport.Severity severity,
            Pattern pattern,
            String message) {}
}
