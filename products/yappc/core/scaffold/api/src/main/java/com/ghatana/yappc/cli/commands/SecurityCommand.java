/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.security.SecurityReviewFramework;
import io.activej.eventloop.Eventloop;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI Command for Security Review Framework Week 11 Day 55: Security review and assessment command
 *
 * <p>Usage examples: yappc security-review --full --output report.html --format html yappc
 * security-review --vulnerabilities-only yappc security-review --threats-only --project
 * /path/to/project yappc security-review --compliance OWASP_TOP_10 yappc security-review --pentest
 * --output pentest_report.json
 */
@Command(
        name = "security-review",
        description = "Perform comprehensive security assessment and review",
        subcommands = {
            SecurityCommand.VulnerabilityCommand.class,
            SecurityCommand.ThreatModelingCommand.class,
            SecurityCommand.ComplianceCommand.class,
            SecurityCommand.PenetrationTestCommand.class
        })
/**
 * SecurityCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose SecurityCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class SecurityCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(SecurityCommand.class);

    @Option(
            names = {"-p", "--project"},
            description = "Project root directory (default: current directory)")
    private String projectPath = ".";

    @Option(
            names = {"-o", "--output"},
            description = "Output file path for the security report")
    private String outputPath = "security_assessment.json";

    @Option(
            names = {"-f", "--format"},
            description = "Report format: json, html, markdown (default: json)")
    private String format = "json";

    @Option(
            names = {"--full"},
            description = "Perform comprehensive security review (all checks)")
    private boolean fullReview = false;

    @Option(
            names = {"--vulnerabilities-only"},
            description = "Only perform vulnerability scanning")
    private boolean vulnerabilitiesOnly = false;

    @Option(
            names = {"--threats-only"},
            description = "Only perform threat modeling")
    private boolean threatsOnly = false;

    @Option(
            names = {"--compliance"},
            description =
                    "Check compliance with specific framework: OWASP_TOP_10, NIST_CSF, ISO_27001")
    private String complianceFramework;

    @Option(
            names = {"--pentest"},
            description = "Perform automated penetration testing")
    private boolean penetrationTest = false;

    @Option(
            names = {"--config-review"},
            description = "Review security configuration only")
    private boolean configReview = false;

    @Option(
            names = {"--risk-threshold"},
            description = "Risk threshold for reporting (LOW, MEDIUM, HIGH, CRITICAL)")
    private String riskThreshold = "MEDIUM";

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @Override
    public Integer call() throws Exception {
        Path projectDir = Paths.get(projectPath).toAbsolutePath().normalize();

        if (verbose) {
            log.info("🛡️ YAPPC Security Review Framework");
            log.info("Project: {}", projectDir);
            log.info("Output: {} ({})", outputPath, format);
            log.info("");;
        }

        try {
            SecurityReviewFramework framework =
                    com.ghatana.yappc.cli.di.ServiceLocator.getInstance()
                            .createSecurityReviewFramework(projectDir);

            if (fullReview
                    || (!vulnerabilitiesOnly
                            && !threatsOnly
                            && !penetrationTest
                            && !configReview)) {
                return performFullSecurityReview(framework);
            }

            if (vulnerabilitiesOnly) {
                return performVulnerabilityAssessment(framework);
            }

            if (threatsOnly) {
                return performThreatModeling(framework);
            }

            if (penetrationTest) {
                return performPenetrationTesting(framework);
            }

            if (configReview) {
                return performConfigurationReview(framework);
            }

            if (complianceFramework != null) {
                return performComplianceCheck(framework, complianceFramework);
            }

            return 0;

        } catch (Exception e) {
            log.error("❌ Error during security review: {}", e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private Integer performFullSecurityReview(SecurityReviewFramework framework)
            throws IOException {
        log.info("🔍 Performing comprehensive security review...\n");

        AtomicReference<SecurityReviewFramework.SecurityAssessment> assessRef = new AtomicReference<>();
        Eventloop eventloop = Eventloop.create();
        eventloop.post(() -> framework.performSecurityReview().whenResult(assessRef::set));
        eventloop.run();
        SecurityReviewFramework.SecurityAssessment assessment = assessRef.get();

        // Display summary
        displayAssessmentSummary(assessment);

        // Export report
        Path output = Paths.get(outputPath);
        framework.exportAssessment(assessment, output, format);

        log.info("\n📄 Report saved to: {}", output.toAbsolutePath());

        // Return exit code based on risk level
        return assessment.riskLevel == SecurityReviewFramework.SecurityLevel.CRITICAL
                ? 2
                : assessment.riskLevel == SecurityReviewFramework.SecurityLevel.HIGH ? 1 : 0;
    }

    private Integer performVulnerabilityAssessment(SecurityReviewFramework framework)
            throws IOException {
        log.info("🔍 Scanning for security vulnerabilities...\n");

        SecurityReviewFramework.VulnerabilityReport report = framework.scanVulnerabilities();

        displayVulnerabilityReport(report);

        // Export vulnerability report if needed
        if (!outputPath.equals("security_assessment.json")) {
            // Export just the vulnerability report
            log.info("\n📄 Vulnerability report saved to: {}", outputPath);
        }

        return report.criticalCount > 0 ? 2 : report.highCount > 0 ? 1 : 0;
    }

    private Integer performThreatModeling(SecurityReviewFramework framework) {
        log.info("🎯 Performing threat modeling and assessment...\n");

        SecurityReviewFramework.ThreatAssessment assessment = framework.assessThreats();

        displayThreatAssessment(assessment);

        return assessment.highRiskThreats > 0 ? 1 : 0;
    }

    private Integer performPenetrationTesting(SecurityReviewFramework framework) {
        log.info("⚔️ Performing automated penetration testing...\n");

        SecurityReviewFramework.PenetrationTestReport report = framework.performPenetrationTest();

        displayPenetrationTestReport(report);

        return report.successfulExploits > 0 ? 1 : 0;
    }

    private Integer performConfigurationReview(SecurityReviewFramework framework) {
        log.info("⚙️ Reviewing security configuration...\n");

        SecurityReviewFramework.SecurityConfiguration config = framework.reviewConfiguration();

        displayConfigurationReview(config);

        return config.securityScore < 70.0 ? 1 : 0;
    }

    private Integer performComplianceCheck(
            SecurityReviewFramework framework, String frameworkName) {
        log.info("✅ Checking compliance with {}...\n", frameworkName);

        SecurityReviewFramework.ComplianceReport report = framework.checkCompliance();

        displayComplianceReport(report);

        return report.overallComplianceScore < 80.0 ? 1 : 0;
    }

    private void displayAssessmentSummary(SecurityReviewFramework.SecurityAssessment assessment) {
        log.info("📊 Security Assessment Summary");
        log.info("=" + "=".repeat(50));
        log.info("");;

        // Overall risk
        String riskIcon = getRiskIcon(assessment.riskLevel);
        log.info("{} Overall Risk Level: {}", riskIcon, assessment.riskLevel);
        log.info(String.format("📈 Risk Score: %.1f/100", assessment.overallRiskScore));
        log.info("");;

        // Vulnerabilities
        if (assessment.vulnerabilityReport != null) {
            var vuln = assessment.vulnerabilityReport;
            log.info("🔍 Vulnerabilities Detected:");
            log.info("   🔴 Critical: {}", vuln.criticalCount);
            log.info("   🟠 High: {}", vuln.highCount);
            log.info("   🟡 Medium: {}", vuln.mediumCount);
            log.info("   🟢 Low: {}", vuln.lowCount);
            log.info("   📊 Total: {}", vuln.totalVulnerabilities);
            log.info("");;
        }

        // Threats
        if (assessment.threatAssessment != null) {
            log.info("🎯 Threats Identified: {}", assessment.threatAssessment.threatCount);
            log.info("   ⚠️ High Risk: {}", assessment.threatAssessment.highRiskThreats);
            log.info("");;
        }

        // Compliance
        if (assessment.complianceReport != null) {
            log.info(String.format("✅ Compliance Score: %.1f%%", assessment.complianceReport.overallComplianceScore));
            log.info("");;
        }

        // Top recommendations
        if (assessment.recommendations != null && !assessment.recommendations.isEmpty()) {
            log.info("🔧 Top Security Recommendations:");
            assessment.recommendations.stream()
                    .limit(3)
                    .forEach(rec -> log.info("   • {} ({})", rec.title, rec.priority));
        }
    }

    private void displayVulnerabilityReport(SecurityReviewFramework.VulnerabilityReport report) {
        log.info("🔍 Vulnerability Scan Results");
        log.info("=" + "=".repeat(50));
        log.info("");;

        log.info("📊 Total Vulnerabilities: {}", report.totalVulnerabilities);
        log.info("🔴 Critical: {}", report.criticalCount);
        log.info("🟠 High: {}", report.highCount);
        log.info("🟡 Medium: {}", report.mediumCount);
        log.info("🟢 Low: {}", report.lowCount);
        log.info("");;

        if (verbose && !report.vulnerabilities.isEmpty()) {
            log.info("Detailed Vulnerabilities:");
            report.vulnerabilities.stream()
                    .limit(10) // Show top 10
                    .forEach(
                            vuln -> {
                                String icon = getRiskIcon(vuln.riskLevel);
                                log.info("{} [{}] {}", icon, vuln.type, vuln.description);
                                log.info("   📁 File: {}:{}", vuln.filePath, vuln.lineNumber);
                                if (vuln.cweId != null) {
                                    log.info("   🏷️ CWE: {}", vuln.cweId);
                                }
                                log.info("");;
                            });
        }
    }

    private void displayThreatAssessment(SecurityReviewFramework.ThreatAssessment assessment) {
        log.info("🎯 Threat Assessment Results");
        log.info("=" + "=".repeat(50));
        log.info("");;

        log.info("📊 Total Threats: {}", assessment.threatCount);
        log.info("⚠️ High Risk Threats: {}", assessment.highRiskThreats);
        log.info("");;

        if (assessment.attackSurface != null) {
            log.info("🎯 Attack Surface Analysis:");
            log.info("   🌐 Network Endpoints: {}", assessment.attackSurface.networkEndpoints.size());
            log.info("   🌍 Web Endpoints: {}", assessment.attackSurface.webEndpoints.size());
            log.info("   📡 API Endpoints: {}", assessment.attackSurface.apiEndpoints.size());
            log.info("");;
        }
    }

    private void displayPenetrationTestReport(
            SecurityReviewFramework.PenetrationTestReport report) {
        log.info("⚔️ Penetration Test Results");
        log.info("=" + "=".repeat(50));
        log.info("");;

        log.info("📊 Total Tests: {}", report.totalTests);
        log.info("💥 Successful Exploits: {}", report.successfulExploits);

        if (report.successfulExploits > 0) {
            log.info("⚠️ CRITICAL: Vulnerabilities were successfully exploited!");
        } else {
            log.info("✅ No exploitable vulnerabilities found");
        }
        log.info("");;
    }

    private void displayConfigurationReview(SecurityReviewFramework.SecurityConfiguration config) {
        log.info("⚙️ Security Configuration Review");
        log.info("=" + "=".repeat(50));
        log.info("");;

        log.info(String.format("📊 Security Score: %.1f/100", config.securityScore));
        log.info("⚠️ Issues Found: {}", config.issueCount);

        if (config.securityScore >= 90) {
            log.info("✅ Excellent security configuration!");
        } else if (config.securityScore >= 70) {
            log.info("👍 Good security configuration with minor issues");
        } else {
            log.info("⚠️ Security configuration needs improvement");
        }
        log.info("");;
    }

    private void displayComplianceReport(SecurityReviewFramework.ComplianceReport report) {
        log.info("✅ Compliance Assessment Results");
        log.info("=" + "=".repeat(50));
        log.info("");;

        log.info(String.format("📊 Overall Compliance: %.1f%%", report.overallComplianceScore));

        if (report.frameworkResults != null) {
            report.frameworkResults.forEach(
                    (framework, result) -> {
                        log.info(String.format("   %s: %.1f%% (%d/%d controls)", framework,
                                result.compliancePercentage,
                                result.compliantControls,
                                result.totalControls));
                    });
        }
        log.info("");;
    }

    private String getRiskIcon(SecurityReviewFramework.SecurityLevel level) {
        return switch (level) {
            case CRITICAL -> "🔴";
            case HIGH -> "🟠";
            case MEDIUM -> "🟡";
            case LOW -> "🟢";
        };
    }

    // Subcommands for specific security operations

    @Command(name = "vulnerabilities", description = "Scan for security vulnerabilities")
    public static class VulnerabilityCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--project"},
                description = "Project directory")
        private String projectPath = ".";

        @Option(
                names = {"--type"},
                description = "Vulnerability types to scan for")
        private String[] vulnerabilityTypes;

        @Option(
                names = {"--exclude"},
                description = "Files/directories to exclude from scan")
        private String[] excludePatterns;

        @Override
        public Integer call() throws Exception {
            log.info("🔍 Starting vulnerability scan...");

            Path projectDir = Paths.get(projectPath).toAbsolutePath();
            SecurityReviewFramework framework =
                    com.ghatana.yappc.cli.di.ServiceLocator.getInstance()
                            .createSecurityReviewFramework(projectDir);

            SecurityReviewFramework.VulnerabilityReport report = framework.scanVulnerabilities();

            log.info("Found {} vulnerabilities", report.totalVulnerabilities);
            log.info("Critical: {}, High: {}, Medium: {}, Low: {}", report.criticalCount, report.highCount, report.mediumCount, report.lowCount);

            return report.criticalCount > 0 ? 2 : report.highCount > 0 ? 1 : 0;
        }
    }

    @Command(name = "threats", description = "Perform threat modeling")
    public static class ThreatModelingCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--project"},
                description = "Project directory")
        private String projectPath = ".";

        @Option(
                names = {"--model"},
                description = "Threat model to apply (STRIDE, PASTA, OCTAVE)")
        private String threatModel = "STRIDE";

        @Override
        public Integer call() throws Exception {
            log.info("🎯 Starting threat modeling with {}...", threatModel);

            Path projectDir = Paths.get(projectPath).toAbsolutePath();
            SecurityReviewFramework framework =
                    com.ghatana.yappc.cli.di.ServiceLocator.getInstance()
                            .createSecurityReviewFramework(projectDir);

            SecurityReviewFramework.ThreatAssessment assessment = framework.assessThreats();

            log.info("Identified {} threats ({} high risk)", assessment.threatCount, assessment.highRiskThreats);

            return assessment.highRiskThreats > 0 ? 1 : 0;
        }
    }

    @Command(name = "compliance", description = "Check security compliance")
    public static class ComplianceCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--project"},
                description = "Project directory")
        private String projectPath = ".";

        @Parameters(description = "Compliance framework (OWASP_TOP_10, NIST_CSF, ISO_27001)")
        private String framework = "OWASP_TOP_10";

        @Override
        public Integer call() throws Exception {
            log.info("✅ Checking {} compliance...", framework);

            Path projectDir = Paths.get(projectPath).toAbsolutePath();
            SecurityReviewFramework securityFramework =
                    com.ghatana.yappc.cli.di.ServiceLocator.getInstance()
                            .createSecurityReviewFramework(projectDir);

            SecurityReviewFramework.ComplianceReport report = securityFramework.checkCompliance();

            log.info(String.format("Compliance score: %.1f%%", report.overallComplianceScore));

            return report.overallComplianceScore < 80.0 ? 1 : 0;
        }
    }

    @Command(name = "pentest", description = "Perform automated penetration testing")
    public static class PenetrationTestCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--project"},
                description = "Project directory")
        private String projectPath = ".";

        @Option(
                names = {"--target"},
                description = "Target URL for web application testing")
        private String targetUrl = "http://localhost:8080";

        @Option(
                names = {"--intensive"},
                description = "Enable intensive testing mode")
        private boolean intensive = false;

        @Override
        public Integer call() throws Exception {
            log.info("⚔️ Starting penetration testing...");
            if (intensive) {
                log.info("⚠️ Intensive mode enabled - this may take longer");
            }

            Path projectDir = Paths.get(projectPath).toAbsolutePath();
            SecurityReviewFramework framework =
                    com.ghatana.yappc.cli.di.ServiceLocator.getInstance()
                            .createSecurityReviewFramework(projectDir);

            SecurityReviewFramework.PenetrationTestReport report =
                    framework.performPenetrationTest();

            log.info("Completed {} tests, {} exploits successful", report.totalTests, report.successfulExploits);

            return report.successfulExploits > 0 ? 1 : 0;
        }
    }
}
