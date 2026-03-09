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

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.yappc.core.ci.CIPipelineSpec;
import com.ghatana.yappc.core.policy.PolicyEnforcementEngine;
import com.ghatana.yappc.core.policy.PolicyEnforcementEngine.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI command for policy enforcement and governance validation.
 *
 * <p>Week 9 Day 45: Policy enforcement for packages, licenses, CODEOWNERS validation CLI.
 */
@Command(
        name = "policy",
        description = "Policy enforcement and governance validation",
        mixinStandardHelpOptions = true,
        subcommands = {
            PolicyCommand.ValidateCommand.class,
            PolicyCommand.EnforceCommand.class,
            PolicyCommand.ReportCommand.class
        })
/**
 * PolicyCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose PolicyCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class PolicyCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(PolicyCommand.class);

    @Override
    public Integer call() throws Exception {
        picocli.CommandLine.usage(this, System.out);
        return 0;
    }

    /**
 * Validate policies in a project. */
    @Command(
            name = "validate",
            description = "Validate project policies",
            mixinStandardHelpOptions = true)
    public static class ValidateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project directory to validate", defaultValue = ".")
        private File projectDir;

        @Option(
                names = {"--packages"},
                description = "Enable package policy validation",
                defaultValue = "true")
        private boolean enablePackages;

        @Option(
                names = {"--licenses"},
                description = "Enable license policy validation",
                defaultValue = "true")
        private boolean enableLicenses;

        @Option(
                names = {"--codeowners"},
                description = "Enable CODEOWNERS policy validation",
                defaultValue = "true")
        private boolean enableCodeOwners;

        @Option(
                names = {"-f", "--format"},
                description = "Output format: ${COMPLETION-CANDIDATES}",
                defaultValue = "CONSOLE")
        private PolicyReportGenerator.ReportFormat format;

        @Option(
                names = {"-o", "--output"},
                description = "Output file (for non-console formats)")
        private File outputFile;

        @Option(
                names = {"--fail-on-violations"},
                description = "Exit with non-zero code if violations found",
                defaultValue = "false")
        private boolean failOnViolations;

        private final PolicyEnforcementEngine policyEngine;

        public ValidateCommand() {
            this.policyEngine = new PolicyEnforcementEngine();
        }

        @Override
        public Integer call() throws Exception {
            try {
                log.info("🛡️  Policy Validation");
                log.info("📁 Project: {}", projectDir.getAbsolutePath());
                log.info("");;

                // Create policy configuration
                PolicyConfiguration config =
                        new PolicyConfiguration(
                                enablePackages,
                                enableLicenses,
                                enableCodeOwners,
                                PackagePolicy.defaultPolicy(),
                                LicensePolicy.defaultPolicy(),
                                CodeOwnersPolicy.defaultPolicy());

                // Run validation
                PolicyValidationResult result =
                        policyEngine.validateAllPolicies(projectDir.getAbsolutePath(), config);

                // Generate report
                PolicyReportGenerator.ReportFormat reportFormat = format;
                PolicyReportGenerator reportGen = new PolicyReportGenerator();
                String report = reportGen.generateReport(result, reportFormat);

                // Output report
                if (outputFile != null) {
                    try (FileWriter writer = new FileWriter(outputFile)) {
                        writer.write(report);
                    }
                    log.info("✅ Report saved to: {}", outputFile.getAbsolutePath());
                } else {
                    log.info("{}", report);
                }

                // Display summary
                displayValidationSummary(result);

                // Return appropriate exit code
                if (failOnViolations && !result.violations().isEmpty()) {
                    log.info("❌ Exiting with error due to policy violations");
                    return 1;
                }

                return 0;

            } catch (Exception e) {
                log.error("❌ Error during policy validation: {}", e.getMessage());
                if (outputFile != null) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private void displayValidationSummary(PolicyValidationResult result) {
            log.info("");;
            log.info("📊 VALIDATION SUMMARY");
            log.info("═".repeat(50));
            log.info("🏆 Overall Status: {}", result.overallStatus());
            log.info("📋 Total Violations: {}", result.violations().size());

            if (!result.violations().isEmpty()) {
                log.info("🔍 Violations by Severity:");

                long critical =
                        result.violations().stream()
                                .filter(v -> v.severity() == Severity.CRITICAL)
                                .count();
                long high =
                        result.violations().stream()
                                .filter(v -> v.severity() == Severity.HIGH)
                                .count();
                long medium =
                        result.violations().stream()
                                .filter(v -> v.severity() == Severity.MEDIUM)
                                .count();
                long low =
                        result.violations().stream()
                                .filter(v -> v.severity() == Severity.LOW)
                                .count();

                if (critical > 0) log.info("  🚨 Critical: {}", critical);
                if (high > 0) log.info("  🔴 High: {}", high);
                if (medium > 0) log.info("  🟡 Medium: {}", medium);
                if (low > 0) log.info("  🟢 Low: {}", low);
            }

            log.info("");;
            log.info("💡 RECOMMENDATIONS:");
            if (result.violations().isEmpty()) {
                log.info("  ✅ All policies passed - great job!");
            } else {
                log.info("  1. Review and address policy violations above");
                log.info("  2. Update project configuration to comply with policies");
                log.info("  3. Run validation again to verify fixes");
                log.info("  4. Consider adding policy enforcement to CI/CD pipeline");
            }
        }

    }

    /**
 * Set up policy enforcement in CI/CD pipelines. */
    @Command(
            name = "enforce",
            description = "Set up policy enforcement in CI/CD pipelines",
            mixinStandardHelpOptions = true)
    public static class EnforceCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project directory", defaultValue = ".")
        private File projectDir;

        @Option(
                names = {"-p", "--platform"},
                description = "CI platform: ${COMPLETION-CANDIDATES}",
                defaultValue = "GITHUB_ACTIONS")
        private Platform platform;

        @Option(
                names = {"--output-dir"},
                description = "Output directory for generated files",
                defaultValue = ".github/workflows")
        private String outputDir;

        @Option(
                names = {"--fail-on-violations"},
                description = "Configure CI to fail on policy violations",
                defaultValue = "false")
        private boolean failOnViolations;

        private final PolicyEnforcementEngine policyEngine;

        public EnforceCommand() {
            this.policyEngine = new PolicyEnforcementEngine();
        }

        @Override
        public Integer call() throws Exception {
            try {
                log.info("⚖️  Setting up Policy Enforcement");
                log.info("📁 Project: {}", projectDir.getAbsolutePath());
                log.info("🏗️  Platform: {}", platform);
                log.info("");;

                // Create policy configuration
                PolicyConfiguration config = PolicyConfiguration.defaultConfig();

                // Generate CI enforcement rules
                CIPipelineSpec.CIPlatform ciPlatform = mapPlatform(platform);
                String enforcementRules =
                        policyEngine.generateCIEnforcementRules(config, ciPlatform);

                // Create output directory and write file
                File outputDirectory = new File(projectDir, outputDir);
                outputDirectory.mkdirs();

                String filename = getEnforcementFilename(platform);
                File enforcementFile = new File(outputDirectory, filename);

                try (FileWriter writer = new FileWriter(enforcementFile)) {
                    writer.write(enforcementRules);
                }

                log.info("✅ Policy enforcement configured: {}", enforcementFile.getAbsolutePath());

                // Generate policy configuration files
                generatePolicyConfigFiles(config, projectDir);

                // Display setup summary
                displayEnforcementSummary(config, enforcementFile);

                return 0;

            } catch (Exception e) {
                log.error("❌ Error setting up policy enforcement: {}", e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }

        private void generatePolicyConfigFiles(PolicyConfiguration config, File projectDir)
                throws IOException {
            // Generate package policy file
            if (config.enablePackagePolicy()) {
                File packagePolicyFile = new File(projectDir, ".yappc/package-policy.json");
                packagePolicyFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(packagePolicyFile)) {
                    writer.write(generatePackagePolicyJson(config.packagePolicy()));
                }
                log.info("✅ Package policy config: {}", packagePolicyFile.getName());
            }

            // Generate license policy file
            if (config.enableLicensePolicy()) {
                File licensePolicyFile = new File(projectDir, ".yappc/license-policy.json");
                licensePolicyFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(licensePolicyFile)) {
                    writer.write(generateLicensePolicyJson(config.licensePolicy()));
                }
                log.info("✅ License policy config: {}", licensePolicyFile.getName());
            }

            // Create sample CODEOWNERS file if needed
            if (config.enableCodeOwnersPolicy() && config.codeOwnersPolicy().requireCodeOwners()) {
                File codeOwnersFile = new File(projectDir, ".github/CODEOWNERS");
                if (!codeOwnersFile.exists()) {
                    codeOwnersFile.getParentFile().mkdirs();
                    try (FileWriter writer = new FileWriter(codeOwnersFile)) {
                        writer.write(generateSampleCodeOwners());
                    }
                    log.info("✅ Sample CODEOWNERS file: {}", codeOwnersFile.getName());
                }
            }
        }

        private String generatePackagePolicyJson(PackagePolicy policy) {
            return String.format(
                    """
                {
                  "bannedPackages": %s,
                  "bannedPackagePatterns": %s,
                  "requiredPackages": %s,
                  "approvedSources": %s,
                  "versionConstraints": {}
                }
                """,
                    policy.bannedPackages(),
                    policy.bannedPackagePatterns(),
                    policy.requiredPackages(),
                    policy.approvedSources());
        }

        private String generateLicensePolicyJson(LicensePolicy policy) {
            return String.format(
                    """
                {
                  "approvedLicenses": %s,
                  "bannedLicenses": %s,
                  "requireLicenseInfo": %s
                }
                """,
                    policy.approvedLicenses(),
                    policy.bannedLicenses(),
                    policy.requireLicenseInfo());
        }

        private String generateSampleCodeOwners() {
            return """
                # Global owners
                * @team/maintainers

                # Source code owners
                /src/ @team/developers

                # Documentation owners
                /docs/ @team/documentation

                # CI/CD configuration owners
                /.github/ @team/devops
                /azure-pipelines.yml @team/devops
                /.gitlab-ci.yml @team/devops

                # Security-sensitive files
                /security/ @team/security
                /.github/workflows/security*.yml @team/security
                """;
        }

        private void displayEnforcementSummary(PolicyConfiguration config, File enforcementFile) {
            log.info("");;
            log.info("📊 POLICY ENFORCEMENT SETUP SUMMARY");
            log.info("═".repeat(50));
            log.info("📦 Package Policy: {}", (config.enablePackagePolicy() ? "✅ Enabled" : "❌ Disabled"));
            log.info("📜 License Policy: {}", (config.enableLicensePolicy() ? "✅ Enabled" : "❌ Disabled"));
            log.info("👥 CODEOWNERS Policy: {}", (config.enableCodeOwnersPolicy() ? "✅ Enabled" : "❌ Disabled"));
            log.info("🚨 Fail on Violations: {}", (failOnViolations ? "✅ Yes" : "❌ No"));
            log.info("");;
            log.info("📋 NEXT STEPS:");
            log.info("1. Review generated policy configuration files");
            log.info("2. Customize policies according to your organization's requirements");
            log.info("3. Commit the enforcement workflow: {}", enforcementFile.getName());
            log.info("4. Test policy enforcement by creating a pull request");
            log.info("5. Monitor policy compliance in your CI dashboard");
        }

        private CIPipelineSpec.CIPlatform mapPlatform(Platform platform) {
            return switch (platform) {
                case GITHUB_ACTIONS -> CIPipelineSpec.CIPlatform.GITHUB_ACTIONS;
                case GITLAB_CI -> CIPipelineSpec.CIPlatform.GITLAB_CI;
                case AZURE_DEVOPS -> CIPipelineSpec.CIPlatform.AZURE_DEVOPS;
            };
        }

        private String getEnforcementFilename(Platform platform) {
            return switch (platform) {
                case GITHUB_ACTIONS -> "policy-enforcement.yml";
                case GITLAB_CI -> ".gitlab-ci-policy.yml";
                case AZURE_DEVOPS -> "azure-pipelines-policy.yml";
            };
        }

        public enum Platform {
            GITHUB_ACTIONS,
            GITLAB_CI,
            AZURE_DEVOPS
        }
    }

    /**
 * Generate comprehensive policy reports. */
    @Command(
            name = "report",
            description = "Generate comprehensive policy compliance reports",
            mixinStandardHelpOptions = true)
    public static class ReportCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project directory to analyze", defaultValue = ".")
        private File projectDir;

        @Option(
                names = {"-f", "--format"},
                description = "Report format: ${COMPLETION-CANDIDATES}",
                defaultValue = "MARKDOWN")
        private PolicyReportGenerator.ReportFormat format;

        @Option(
                names = {"-o", "--output"},
                description = "Output file",
                defaultValue = "policy-report.md")
        private File outputFile;

        @Option(
                names = {"--detailed"},
                description = "Generate detailed report with recommendations")
        private boolean detailed;

        private final PolicyEnforcementEngine policyEngine;

        public ReportCommand() {
            this.policyEngine = new PolicyEnforcementEngine();
        }

        @Override
        public Integer call() throws Exception {
            try {
                log.info("📊 Generating Policy Compliance Report");
                log.info("📁 Project: {}", projectDir.getAbsolutePath());
                log.info("📄 Format: {}", format);
                log.info("");;

                // Run comprehensive policy validation
                PolicyConfiguration config = PolicyConfiguration.defaultConfig();
                PolicyValidationResult result =
                        policyEngine.validateAllPolicies(projectDir.getAbsolutePath(), config);

                // Generate detailed report
                PolicyReportGenerator reportGen = new PolicyReportGenerator();
                PolicyReportGenerator.ReportFormat reportFormat = format;
                String report = reportGen.generateReport(result, reportFormat);

                // Write report to file
                try (FileWriter writer = new FileWriter(outputFile)) {
                    writer.write(report);
                }

                log.info("✅ Policy compliance report generated: {}", outputFile.getAbsolutePath());

                // Display key metrics
                displayReportMetrics(result);

                return 0;

            } catch (Exception e) {
                log.error("❌ Error generating policy report: {}", e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }

        private void displayReportMetrics(PolicyValidationResult result) {
            log.info("");;
            log.info("📈 REPORT METRICS");
            log.info("═".repeat(50));
            log.info("🏆 Overall Status: {}", result.overallStatus());
            log.info("📋 Total Violations: {}", result.violations().size());
            log.info("📊 Policy Areas Checked: {}", result.policyStatuses().size());
            log.info("📅 Report Generated: {}", result.validationTime());

            if (result.overallStatus() == PolicyStatus.PASSED) {
                log.info("🎉 Congratulations! All policies are compliant.");
            } else {
                log.info("⚠️  Action required to achieve full compliance.");
            }
        }

    }
}
