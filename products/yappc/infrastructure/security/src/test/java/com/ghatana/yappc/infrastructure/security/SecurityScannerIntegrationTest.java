/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure Integration Tests
 */
package com.ghatana.yappc.infrastructure.security;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityReport;
import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityScanner;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for security scanning infrastructure.
 *
 * <p><b>Scope:</b> Validates that CompositeSecurityScanner properly composes
 * multiple scanner strategies and merges results without duplication.</p>
 *
 * <p><b>Tests:</b>
 * <ul>
 *   <li>Composite scanner executes all strategies in parallel
 *   <li>Findings are deduplicated across scanners
 *   <li>Overall status reflects worst outcome (VULNERABLE > CLEAN)
 *   <li>Individual scanner failures don't crash the aggregate
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration tests for security scanning
 * @doc.layer infrastructure
 * @doc.pattern Test
 */
@DisplayName("Security Scanning Integration Tests")
public class SecurityScannerIntegrationTest extends EventloopTestBase {

    
    

    @Test
    @DisplayName("CompositeScanner#scan - executes all strategies in parallel")
    public void testCompositeExecutesStrategiesInParallel() {
        // GIVEN
        AtomicInteger scanner1ExecutionCount = new AtomicInteger(0);
        AtomicInteger scanner2ExecutionCount = new AtomicInteger(0);

        SecurityScanner scanner1 = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                scanner1ExecutionCount.incrementAndGet();
                return Promise.of(SecurityReport.withFindings(List.of(
                        new SecurityReport.Finding("S1-001", "Finding from Scanner 1", SecurityReport.Severity.MEDIUM, "unknown")
                    ), "Scanner1"));
            }
        };

        SecurityScanner scanner2 = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                scanner2ExecutionCount.incrementAndGet();
                return Promise.of(SecurityReport.withFindings(List.of(
                        new SecurityReport.Finding("S2-001", "Finding from Scanner 2", SecurityReport.Severity.HIGH, "unknown")
                    ), "Scanner2"));
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(List.of(scanner1, scanner2));
        Path testPath = Paths.get(".");

        // WHEN
        SecurityReport result = runPromise(() -> composite.scan(testPath));

        // THEN
        assertThat(scanner1ExecutionCount).hasValue(1);
        assertThat(scanner2ExecutionCount).hasValue(1);
        assertThat(result).isNotNull();
        assertThat(result.findings()).hasSize(2);
        assertThat(result.getScannerName()).contains("Scanner1").contains("Scanner2");
    }

    @Test
    @DisplayName("CompositeScanner#scan - deduplicates identical findings")
    public void testCompositeDeduplicatesFinding() {
        // GIVEN
        SecurityReport.Finding finding = new SecurityReport.Finding("SHARED-ID", "Shared vulnerability", SecurityReport.Severity.HIGH, "unknown");

        SecurityScanner duplicateScanner1 = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.withFindings(List.of(finding), "Scanner1"));
            }
        };

        SecurityScanner duplicateScanner2 = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.withFindings(List.of(finding), "Scanner2"));
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(
            List.of(duplicateScanner1, duplicateScanner2)
        );

        // WHEN
        SecurityReport result = runPromise(() -> composite.scan(Paths.get(".")));

        // THEN
        assertThat(result.findings()).hasSize(1); // Deduplicated
        assertThat(result.getStatus()).isEqualTo(SecurityReport.Status.VULNERABLE);
    }

    @Test
    @DisplayName("CompositeScanner#scan - reflects worst status (VULNERABLE > CLEAN)")
    public void testCompositeReflectsWorstStatus() {
        // GIVEN
        SecurityScanner cleanScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.clean("CleanScanner"));
            }
        };

        SecurityScanner vulnerableScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.withFindings(List.of(
                        new SecurityReport.Finding("V-001", "Critical issue", SecurityReport.Severity.CRITICAL, "unknown")
                    ), "VulnerableScanner"));
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(List.of(cleanScanner, vulnerableScanner));

        // WHEN
        SecurityReport result = runPromise(() -> composite.scan(Paths.get(".")));

        // THEN
        assertThat(result.getStatus()).isEqualTo(SecurityReport.Status.VULNERABLE);
    }

    @Test
    @DisplayName("CompositeScanner#scan - handles individual scanner failure gracefully")
    public void testCompositeHandlesScannerFailure() {
        // GIVEN
        SecurityScanner failingScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.ofException(new RuntimeException("Scanner error"));
            }
        };

        SecurityScanner successScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.withFindings(List.of(
                        new SecurityReport.Finding("S-001", "Low severity", SecurityReport.Severity.LOW, "unknown")
                    ), "SuccessScanner"));
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(List.of(failingScanner, successScanner));

        // WHEN
        SecurityReport result = runPromise(() -> composite.scan(Paths.get(".")));

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.findings()).hasSize(1); // Only from success scanner
        assertThat(result.getScannerName()).contains("SuccessScanner");
    }

    @Test
    @DisplayName("CompositeScanner - combined with OSV and SAST provides comprehensive coverage")
    public void testCompositeWithOsvAndSastCoverage() {
        // GIVEN - Simplified mock of what real implementation would do
        SecurityScanner satMockScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.withFindings(List.of(
                        new SecurityReport.Finding("SAST-CMD-001", "Command injection in generated code", SecurityReport.Severity.CRITICAL, "unknown")
                    ), "StaticAnalysisScanner(SAST)"));
            }
        };

        SecurityScanner osvMockScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.withFindings(List.of(
                        new SecurityReport.Finding("CVE-2024-12345", "Remote code execution in dependency org.example:lib:1.0.0", SecurityReport.Severity.HIGH, "unknown")
                    ), "OsvScannerAdapter"));
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(List.of(satMockScanner, osvMockScanner));

        // WHEN
        SecurityReport result = runPromise(() -> composite.scan(Paths.get("products/yappc")));

        // THEN
        assertThat(result.getStatus()).isEqualTo(SecurityReport.Status.VULNERABLE); // OSV found vulnerability
        assertThat(result.findings()).hasSize(2); // Both SAST and OSV findings
        assertThat(result.findings().stream().filter(f -> f.severity() == SecurityReport.Severity.CRITICAL).toList()).hasSize(1); // SAST finding
        assertThat(result.findings().stream().filter(f -> f.severity() == SecurityReport.Severity.HIGH).toList()).hasSize(1); // OSV finding
    }
}
