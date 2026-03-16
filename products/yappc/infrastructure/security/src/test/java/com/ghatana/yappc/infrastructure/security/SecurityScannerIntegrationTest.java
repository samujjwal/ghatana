/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure Integration Tests
 */
package com.ghatana.yappc.infrastructure.security;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityReport;
import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityScanner;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.test.rules.EventloopRule;
import org.junit.ClassRule;
import org.junit.DisplayName;
import org.junit.Test;

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
public class SecurityScannerIntegrationTest {

    @ClassRule
    public static EventloopRule eventloopRule = new EventloopRule();

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
                return Promise.of(SecurityReport.builder()
                    .scannerName("Scanner1")
                    .status(SecurityReport.Status.CLEAN)
                    .findings(List.of(
                        new SecurityReport.Finding(
                            "S1-001", SecurityReport.Severity.MEDIUM, "Finding from Scanner 1")
                    ))
                    .build());
            }
        };

        SecurityScanner scanner2 = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                scanner2ExecutionCount.incrementAndGet();
                return Promise.of(SecurityReport.builder()
                    .scannerName("Scanner2")
                    .status(SecurityReport.Status.CLEAN)
                    .findings(List.of(
                        new SecurityReport.Finding(
                            "S2-001", SecurityReport.Severity.HIGH, "Finding from Scanner 2")
                    ))
                    .build());
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(List.of(scanner1, scanner2));
        Path testPath = Paths.get(".");

        // WHEN
        SecurityReport result = eventloopRule.getEventloop().run(() ->
            composite.scan(testPath)
        ).getResult();

        // THEN
        assertThat(scanner1ExecutionCount).hasValue(1);
        assertThat(scanner2ExecutionCount).hasValue(1);
        assertThat(result).isNotNull();
        assertThat(result.getFindings()).hasSize(2);
        assertThat(result.getScannerName()).contains("Scanner1").contains("Scanner2");
    }

    @Test
    @DisplayName("CompositeScanner#scan - deduplicates identical findings")
    public void testCompositeDeduplicatesFinding() {
        // GIVEN
        SecurityReport finding = new SecurityReport.Finding(
            "SHARED-ID", SecurityReport.Severity.HIGH, "Shared vulnerability");

        SecurityScanner duplicateScanner1 = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.builder()
                    .scannerName("Scanner1")
                    .status(SecurityReport.Status.VULNERABLE)
                    .findings(List.of(finding))
                    .build());
            }
        };

        SecurityScanner duplicateScanner2 = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.builder()
                    .scannerName("Scanner2")
                    .status(SecurityReport.Status.VULNERABLE)
                    .findings(List.of(finding))
                    .build());
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(
            List.of(duplicateScanner1, duplicateScanner2)
        );

        // WHEN
        SecurityReport result = eventloopRule.getEventloop().run(() ->
            composite.scan(Paths.get("."))
        ).getResult();

        // THEN
        assertThat(result.getFindings()).hasSize(1); // Deduplicated
        assertThat(result.getStatus()).isEqualTo(SecurityReport.Status.VULNERABLE);
    }

    @Test
    @DisplayName("CompositeScanner#scan - reflects worst status (VULNERABLE > CLEAN)")
    public void testCompositeReflectsWorstStatus() {
        // GIVEN
        SecurityScanner cleanScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.builder()
                    .scannerName("CleanScanner")
                    .status(SecurityReport.Status.CLEAN)
                    .findings(List.of())
                    .build());
            }
        };

        SecurityScanner vulnerableScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.builder()
                    .scannerName("VulnerableScanner")
                    .status(SecurityReport.Status.VULNERABLE)
                    .findings(List.of(
                        new SecurityReport.Finding("V-001", SecurityReport.Severity.CRITICAL, "Critical issue")
                    ))
                    .build());
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(List.of(cleanScanner, vulnerableScanner));

        // WHEN
        SecurityReport result = eventloopRule.getEventloop().run(() ->
            composite.scan(Paths.get("."))
        ).getResult();

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
                return Promise.of(SecurityReport.builder()
                    .scannerName("SuccessScanner")
                    .status(SecurityReport.Status.CLEAN)
                    .findings(List.of(
                        new SecurityReport.Finding("S-001", SecurityReport.Severity.LOW, "Low severity")
                    ))
                    .build());
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(List.of(failingScanner, successScanner));

        // WHEN
        SecurityReport result = eventloopRule.getEventloop().run(() ->
            composite.scan(Paths.get("."))
        ).getResult();

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getFindings()).hasSize(1); // Only from success scanner
        assertThat(result.getScannerName()).contains("SuccessScanner");
    }

    @Test
    @DisplayName("CompositeScanner - combined with OSV and SAST provides comprehensive coverage")
    public void testCompositeWithOsvAndSastCoverage() {
        // GIVEN - Simplified mock of what real implementation would do
        SecurityScanner satMockScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.builder()
                    .scannerName("StaticAnalysisScanner(SAST)")
                    .status(SecurityReport.Status.CLEAN)
                    .findings(List.of(
                        new SecurityReport.Finding(
                            "SAST-CMD-001",
                            SecurityReport.Severity.CRITICAL,
                            "Command injection in generated code"
                        )
                    ))
                    .build());
            }
        };

        SecurityScanner osvMockScanner = new SecurityScanner() {
            @Override
            public Promise<SecurityReport> scan(Path projectPath) {
                return Promise.of(SecurityReport.builder()
                    .scannerName("OsvScannerAdapter")
                    .status(SecurityReport.Status.VULNERABLE)
                    .findings(List.of(
                        new SecurityReport.Finding(
                            "CVE-2024-12345",
                            SecurityReport.Severity.HIGH,
                            "Remote code execution in dependency org.example:lib:1.0.0"
                        )
                    ))
                    .build());
            }
        };

        CompositeSecurityScanner composite = new CompositeSecurityScanner(List.of(satMockScanner, osvMockScanner));

        // WHEN
        SecurityReport result = eventloopRule.getEventloop().run(() ->
            composite.scan(Paths.get("products/yappc"))
        ).getResult();

        // THEN
        assertThat(result.getStatus()).isEqualTo(SecurityReport.Status.VULNERABLE); // OSV found vulnerability
        assertThat(result.getFindings()).hasSize(2); // Both SAST and OSV findings
        assertThat(result.findingsByCategory(SecurityReport.Severity.CRITICAL)).hasSize(1); // SAST finding
        assertThat(result.findingsByCategory(SecurityReport.Severity.HIGH)).hasSize(1); // OSV finding
    }
}
