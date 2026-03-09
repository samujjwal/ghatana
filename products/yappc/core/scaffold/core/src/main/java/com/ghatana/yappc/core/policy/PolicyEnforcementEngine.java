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
package com.ghatana.yappc.core.policy;

import com.ghatana.yappc.core.ci.CIPipelineSpec;
import com.ghatana.yappc.core.deps.DependencyGraphExtractor;
import com.ghatana.platform.domain.domain.Severity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive policy enforcement system for packages, licenses, and
 * governance.
 *
 * <p>
 * Week 9 Day 45: Policy enforcement for packages, licenses, CODEOWNERS
 * validation.
 *
 * @doc.type class
 * @doc.purpose Comprehensive policy enforcement system for packages, licenses,
 * and governance.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class PolicyEnforcementEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEnforcementEngine.class);

    private final PackagePolicyValidator packageValidator;
    private final LicensePolicyValidator licenseValidator;
    private final CodeOwnersPolicyValidator codeOwnersValidator;
    private final PolicyReportGenerator reportGenerator;

    public PolicyEnforcementEngine() {
        this.packageValidator = new PackagePolicyValidator();
        this.licenseValidator = new LicensePolicyValidator();
        this.codeOwnersValidator = new CodeOwnersPolicyValidator();
        this.reportGenerator = new PolicyReportGenerator();
    }

    private static DependencyGraphExtractor.DependencyGraph buildDependencyGraph(
            String projectPath) {
        return new DependencyGraphExtractor().extractDependencyGraph(projectPath);
    }

    private static PolicyStatus determineStatus(List<PolicyViolation> violations) {
        if (violations.isEmpty()) {
            return PolicyStatus.PASSED;
        }

        return violations.stream().anyMatch(v -> v.severity() == Severity.CRITICAL)
                ? PolicyStatus.FAILED
                : PolicyStatus.WARNING;
    }

    /**
     * Validates all policies for a project and returns comprehensive results.
     */
    public PolicyValidationResult validateAllPolicies(
            String projectPath, PolicyConfiguration config) {
        log.info("🛡️  Running comprehensive policy validation...");

        List<PolicyViolation> violations = new ArrayList<>();
        Map<String, PolicyStatus> policyStatuses = new HashMap<>();

        try {
            // Package policy validation
            if (config.enablePackagePolicy()) {
                PackageValidationResult packageResult
                        = packageValidator.validatePackages(projectPath, config.packagePolicy());
                violations.addAll(packageResult.violations());
                policyStatuses.put("package-policy", packageResult.overallStatus());
                log.info("📦 Package Policy: {} violations found", packageResult.violations().size());
            }

            // License policy validation
            if (config.enableLicensePolicy()) {
                LicenseValidationResult licenseResult
                        = licenseValidator.validateLicenses(projectPath, config.licensePolicy());
                violations.addAll(licenseResult.violations());
                policyStatuses.put("license-policy", licenseResult.overallStatus());
                log.info("📜 License Policy: {} violations found", licenseResult.violations().size());
            }

            // CODEOWNERS validation
            if (config.enableCodeOwnersPolicy()) {
                CodeOwnersValidationResult codeOwnersResult
                        = codeOwnersValidator.validateCodeOwners(
                                projectPath, config.codeOwnersPolicy());
                violations.addAll(codeOwnersResult.violations());
                policyStatuses.put("codeowners-policy", codeOwnersResult.overallStatus());
                log.info("👥 CODEOWNERS Policy: {} violations found", codeOwnersResult.violations().size());
            }

            // Calculate overall status
            PolicyStatus overallStatus = calculateOverallStatus(policyStatuses);

            return new PolicyValidationResult(
                    violations, policyStatuses, overallStatus, LocalDateTime.now(), projectPath);

        } catch (Exception e) {
            log.error("❌ Error during policy validation: {}", e.getMessage());
            violations.add(
                    new PolicyViolation(
                            "system-error",
                            Severity.CRITICAL,
                            "Policy validation failed: " + e.getMessage(),
                            "System Error",
                            List.of()));

            return new PolicyValidationResult(
                    violations,
                    policyStatuses,
                    PolicyStatus.FAILED,
                    LocalDateTime.now(),
                    projectPath);
        }
    }

    /**
     * Generates enforcement rules for CI/CD integration.
     */
    public String generateCIEnforcementRules(
            PolicyConfiguration config, CIPipelineSpec.CIPlatform platform) {
        StringBuilder rules = new StringBuilder();

        switch (platform) {
            case GITHUB_ACTIONS ->
                generateGitHubActionsRules(rules, config);
            case GITLAB_CI ->
                generateGitLabCIRules(rules, config);
            case AZURE_DEVOPS ->
                generateAzureDevOpsRules(rules, config);
            default ->
                rules.append("# Policy enforcement not implemented for platform: ")
                        .append(platform);
        }

        return rules.toString();
    }

    private void generateGitHubActionsRules(StringBuilder rules, PolicyConfiguration config) {
        rules.append("name: Policy Enforcement\\n\\n");
        rules.append("on:\\n");
        rules.append("  pull_request:\\n");
        rules.append("    branches: [main, develop]\\n");
        rules.append("  push:\\n");
        rules.append("    branches: [main, develop]\\n\\n");

        rules.append("jobs:\\n");
        rules.append("  policy-check:\\n");
        rules.append("    name: Policy Validation\\n");
        rules.append("    runs-on: ubuntu-latest\\n");
        rules.append("    steps:\\n");
        rules.append("      - name: Checkout code\\n");
        rules.append("        uses: actions/checkout@v4\\n\\n");

        if (config.enablePackagePolicy()) {
            generatePackagePolicySteps(rules);
        }

        if (config.enableLicensePolicy()) {
            generateLicensePolicySteps(rules);
        }

        if (config.enableCodeOwnersPolicy()) {
            generateCodeOwnersPolicySteps(rules);
        }

        rules.append("      - name: Policy Report\\n");
        rules.append("        run: |\\n");
        rules.append("          echo 'Policy validation completed'\\n");
        rules.append("          # Generate policy report here\\n");
    }

    private void generatePackagePolicySteps(StringBuilder rules) {
        rules.append("      - name: Package Policy Check\\n");
        rules.append("        run: |\\n");
        rules.append("          echo 'Validating package policies...'\\n");
        rules.append("          # Run yappc deps check --policy\\n");
        rules.append("          # Check for banned packages\\n");
        rules.append("          # Validate package sources\\n\\n");
    }

    private void generateLicensePolicySteps(StringBuilder rules) {
        rules.append("      - name: License Policy Check\\n");
        rules.append("        uses: fossa-contrib/fossa-action@v2\\n");
        rules.append("        with:\\n");
        rules.append("          api-key: ${{ secrets.FOSSA_API_KEY }}\\n");
        rules.append("          project-path: .\\n\\n");

        rules.append("      - name: License Compliance\\n");
        rules.append("        run: |\\n");
        rules.append("          echo 'Checking license compliance...'\\n");
        rules.append("          # Validate approved licenses\\n");
        rules.append("          # Check for license conflicts\\n\\n");
    }

    private void generateCodeOwnersPolicySteps(StringBuilder rules) {
        rules.append("      - name: CODEOWNERS Validation\\n");
        rules.append("        run: |\\n");
        rules.append("          echo 'Validating CODEOWNERS configuration...'\\n");
        rules.append("          # Check CODEOWNERS file exists\\n");
        rules.append("          # Validate path coverage\\n");
        rules.append("          # Verify team/user existence\\n\\n");
    }

    private void generateGitLabCIRules(StringBuilder rules, PolicyConfiguration config) {
        rules.append("# Policy Enforcement Pipeline\\n");
        rules.append("stages:\\n");
        rules.append("  - policy-validation\\n\\n");

        rules.append("policy-check:\\n");
        rules.append("  stage: policy-validation\\n");
        rules.append("  script:\\n");
        rules.append("    - echo 'Running policy validation'\\n");

        if (config.enablePackagePolicy()) {
            rules.append("    - echo 'Checking package policies'\\n");
        }
        if (config.enableLicensePolicy()) {
            rules.append("    - echo 'Checking license policies'\\n");
        }
        if (config.enableCodeOwnersPolicy()) {
            rules.append("    - echo 'Validating CODEOWNERS'\\n");
        }

        rules.append("  rules:\\n");
        rules.append("    - if: $CI_PIPELINE_SOURCE == 'merge_request_event'\\n");
        rules.append("    - if: $CI_COMMIT_BRANCH == 'main'\\n");
    }

    private void generateAzureDevOpsRules(StringBuilder rules, PolicyConfiguration config) {
        rules.append("# Policy Enforcement Pipeline\\n");
        rules.append("trigger:\\n");
        rules.append("  branches:\\n");
        rules.append("    include: [main, develop]\\n\\n");

        rules.append("pr:\\n");
        rules.append("  branches:\\n");
        rules.append("    include: [main, develop]\\n\\n");

        rules.append("jobs:\\n");
        rules.append("- job: PolicyValidation\\n");
        rules.append("  displayName: 'Policy Validation'\\n");
        rules.append("  steps:\\n");
        rules.append("  - checkout: self\\n");

        if (config.enablePackagePolicy()) {
            rules.append("  - script: echo 'Checking package policies'\\n");
            rules.append("    displayName: 'Package Policy Check'\\n");
        }

        rules.append("  - script: echo 'Policy validation completed'\\n");
        rules.append("    displayName: 'Policy Report'\\n");
    }

    private PolicyStatus calculateOverallStatus(Map<String, PolicyStatus> statuses) {
        if (statuses.values().contains(PolicyStatus.FAILED)) {
            return PolicyStatus.FAILED;
        }
        if (statuses.values().contains(PolicyStatus.WARNING)) {
            return PolicyStatus.WARNING;
        }
        return PolicyStatus.PASSED;
    }

    /**
     * Package policy validator for dependency restrictions and governance.
     */
    public static class PackagePolicyValidator {

        public PackageValidationResult validatePackages(String projectPath, PackagePolicy policy) {
            log.info("📦 Validating package policies...");

            List<PolicyViolation> violations = new ArrayList<>();

            try {
                // Extract dependencies
                DependencyGraphExtractor.DependencyGraph graph
                        = buildDependencyGraph(projectPath);

                // Check banned packages
                violations.addAll(checkBannedPackages(graph, policy));

                // Check required packages
                violations.addAll(checkRequiredPackages(graph, policy));

                // Check package sources
                violations.addAll(checkPackageSources(graph, policy));

                // Check version constraints
                violations.addAll(checkVersionConstraints(graph, policy));

                return new PackageValidationResult(violations, determineStatus(violations));

            } catch (Exception e) {
                violations.add(
                        new PolicyViolation(
                                "package-validation-error",
                                Severity.CRITICAL,
                                "Failed to validate package policies: " + e.getMessage(),
                                "Package Validation",
                                List.of()));
                return new PackageValidationResult(violations, PolicyStatus.FAILED);
            }
        }

        private List<PolicyViolation> checkBannedPackages(
                DependencyGraphExtractor.DependencyGraph graph, PackagePolicy policy) {
            return graph.unifiedDependencies().stream()
                    .filter(
                            dep
                            -> policy.bannedPackages().contains(dep.name())
                            || policy.bannedPackagePatterns().stream()
                                    .anyMatch(
                                            pattern
                                            -> Pattern.compile(pattern)
                                                    .matcher(dep.name())
                                                    .matches()))
                    .map(
                            dep
                            -> new PolicyViolation(
                                    "banned-package",
                                    Severity.CRITICAL,
                                    String.format(
                                            "Banned package detected: %s", dep.name()),
                                    "Package Policy",
                                    List.of(
                                            "Remove banned package",
                                            "Use approved alternative")))
                    .collect(Collectors.toList());
        }

        private List<PolicyViolation> checkRequiredPackages(
                DependencyGraphExtractor.DependencyGraph graph, PackagePolicy policy) {
            Set<String> presentPackages
                    = graph.unifiedDependencies().stream()
                            .map(DependencyGraphExtractor.Dependency::name)
                            .collect(Collectors.toSet());

            return policy.requiredPackages().stream()
                    .filter(required -> !presentPackages.contains(required))
                    .map(
                            missing
                            -> new PolicyViolation(
                                    "missing-required-package",
                                    Severity.HIGH,
                                    String.format("Required package missing: %s", missing),
                                    "Package Policy",
                                    List.of("Add required package to dependencies")))
                    .collect(Collectors.toList());
        }

        private List<PolicyViolation> checkPackageSources(
                DependencyGraphExtractor.DependencyGraph graph, PackagePolicy policy) {
            List<PolicyViolation> violations = new ArrayList<>();

            // Check if packages are from approved sources (simplified - would need registry info)
            for (var dep : graph.unifiedDependencies()) {
                boolean fromApprovedSource
                        = policy.approvedSources().isEmpty()
                        || policy.approvedSources().contains("central"); // Simplified check

                if (!fromApprovedSource) {
                    violations.add(
                            new PolicyViolation(
                                    "unapproved-source",
                                    Severity.MEDIUM,
                                    String.format("Package from unapproved source: %s", dep.name()),
                                    "Package Policy",
                                    List.of("Use package from approved registry")));
                }
            }

            return violations;
        }

        private List<PolicyViolation> checkVersionConstraints(
                DependencyGraphExtractor.DependencyGraph graph, PackagePolicy policy) {
            return graph.unifiedDependencies().stream()
                    .filter(dep -> policy.versionConstraints().containsKey(dep.name()))
                    .filter(
                            dep
                            -> !isVersionAllowed(
                                    dep.version(),
                                    policy.versionConstraints().get(dep.name())))
                    .map(
                            dep
                            -> new PolicyViolation(
                                    "version-constraint-violation",
                                    Severity.MEDIUM,
                                    String.format(
                                            "Package %s version %s violates policy"
                                            + " constraint: %s",
                                            dep.name(),
                                            dep.version(),
                                            policy.versionConstraints().get(dep.name())),
                                    "Package Policy",
                                    List.of("Update to compliant version")))
                    .collect(Collectors.toList());
        }

        private boolean isVersionAllowed(String version, String constraint) {
            // Simplified version checking - would need proper semver parsing
            return true; // Placeholder
        }
    }

    /**
     * License policy validator for compliance and governance.
     */
    public static class LicensePolicyValidator {

        public LicenseValidationResult validateLicenses(String projectPath, LicensePolicy policy) {
            log.info("📜 Validating license policies...");

            List<PolicyViolation> violations = new ArrayList<>();

            try {
                // Extract dependencies and their licenses
                DependencyGraphExtractor.DependencyGraph graph
                        = buildDependencyGraph(projectPath);

                // Check banned licenses
                violations.addAll(checkBannedLicenses(graph, policy));

                // Check required licenses
                violations.addAll(checkRequiredLicenses(graph, policy));

                // Check license compatibility
                violations.addAll(checkLicenseCompatibility(graph, policy));

                return new LicenseValidationResult(violations, determineStatus(violations));

            } catch (Exception e) {
                violations.add(
                        new PolicyViolation(
                                "license-validation-error",
                                Severity.CRITICAL,
                                "Failed to validate license policies: " + e.getMessage(),
                                "License Validation",
                                List.of()));
                return new LicenseValidationResult(violations, PolicyStatus.FAILED);
            }
        }

        private List<PolicyViolation> checkBannedLicenses(
                DependencyGraphExtractor.DependencyGraph graph, LicensePolicy policy) {
            return graph.unifiedDependencies().stream()
                    .filter(
                            dep
                            -> dep.license() != null
                            && policy.bannedLicenses().contains(dep.license()))
                    .map(
                            dep
                            -> new PolicyViolation(
                                    "banned-license",
                                    Severity.CRITICAL,
                                    String.format(
                                            "Package %s uses banned license: %s",
                                            dep.name(), dep.license()),
                                    "License Policy",
                                    List.of(
                                            "Remove package",
                                            "Find alternative with approved license")))
                    .collect(Collectors.toList());
        }

        private List<PolicyViolation> checkRequiredLicenses(
                DependencyGraphExtractor.DependencyGraph graph, LicensePolicy policy) {
            List<PolicyViolation> violations = new ArrayList<>();

            for (var dep : graph.unifiedDependencies()) {
                if (dep.license() == null) {
                    violations.add(
                            new PolicyViolation(
                                    "missing-license",
                                    Severity.MEDIUM,
                                    String.format(
                                            "Package %s has no license information", dep.name()),
                                    "License Policy",
                                    List.of(
                                            "Verify license information",
                                            "Contact package maintainer")));
                } else if (!policy.approvedLicenses().isEmpty()
                        && !policy.approvedLicenses().contains(dep.license())) {
                    violations.add(
                            new PolicyViolation(
                                    "unapproved-license",
                                    Severity.HIGH,
                                    String.format(
                                            "Package %s uses unapproved license: %s",
                                            dep.name(), dep.license()),
                                    "License Policy",
                                    List.of("Use package with approved license")));
                }
            }

            return violations;
        }

        private List<PolicyViolation> checkLicenseCompatibility(
                DependencyGraphExtractor.DependencyGraph graph, LicensePolicy policy) {
            // Simplified license compatibility checking
            List<PolicyViolation> violations = new ArrayList<>();

            Set<String> projectLicenses
                    = graph.unifiedDependencies().stream()
                            .map(DependencyGraphExtractor.Dependency::license)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

            // Check for GPL + commercial license conflicts
            if (projectLicenses.contains("GPL-3.0")
                    && projectLicenses.stream().anyMatch(l -> l.contains("Commercial"))) {
                violations.add(
                        new PolicyViolation(
                                "license-conflict",
                                Severity.HIGH,
                                "GPL and Commercial license conflict detected",
                                "License Policy",
                                List.of("Review license compatibility", "Consult legal team")));
            }

            return violations;
        }
    }

    /**
     * CODEOWNERS policy validator for governance and review requirements.
     */
    public static class CodeOwnersPolicyValidator {

        public CodeOwnersValidationResult validateCodeOwners(
                String projectPath, CodeOwnersPolicy policy) {
            log.info("👥 Validating CODEOWNERS policies...");

            List<PolicyViolation> violations = new ArrayList<>();

            try {
                Path codeOwnersPath = findCodeOwnersFile(projectPath);

                if (codeOwnersPath == null) {
                    if (policy.requireCodeOwners()) {
                        violations.add(
                                new PolicyViolation(
                                        "missing-codeowners",
                                        Severity.HIGH,
                                        "CODEOWNERS file is required but not found",
                                        "CODEOWNERS Policy",
                                        List.of(
                                                "Create CODEOWNERS file",
                                                "Define code ownership")));
                    }
                } else {
                    // Validate CODEOWNERS content
                    violations.addAll(validateCodeOwnersContent(codeOwnersPath, policy));

                    // Check path coverage
                    violations.addAll(validatePathCoverage(projectPath, codeOwnersPath, policy));

                    // Validate team/user references
                    violations.addAll(validateOwnerReferences(codeOwnersPath, policy));
                }

                return new CodeOwnersValidationResult(violations, determineStatus(violations));

            } catch (Exception e) {
                violations.add(
                        new PolicyViolation(
                                "codeowners-validation-error",
                                Severity.CRITICAL,
                                "Failed to validate CODEOWNERS: " + e.getMessage(),
                                "CODEOWNERS Validation",
                                List.of()));
                return new CodeOwnersValidationResult(violations, PolicyStatus.FAILED);
            }
        }

        private Path findCodeOwnersFile(String projectPath) {
            Path basePath = Paths.get(projectPath);

            // Check common CODEOWNERS locations
            for (String location : List.of("CODEOWNERS", ".github/CODEOWNERS", "docs/CODEOWNERS")) {
                Path codeOwnersPath = basePath.resolve(location);
                if (Files.exists(codeOwnersPath)) {
                    return codeOwnersPath;
                }
            }

            return null;
        }

        private List<PolicyViolation> validateCodeOwnersContent(
                Path codeOwnersPath, CodeOwnersPolicy policy) {
            List<PolicyViolation> violations = new ArrayList<>();

            try {
                List<String> lines = Files.readAllLines(codeOwnersPath);

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Basic syntax validation
                    if (!isValidCodeOwnersLine(line)) {
                        violations.add(
                                new PolicyViolation(
                                        "invalid-codeowners-syntax",
                                        Severity.MEDIUM,
                                        String.format(
                                                "Invalid CODEOWNERS syntax at line %d: %s",
                                                i + 1, line),
                                        "CODEOWNERS Policy",
                                        List.of("Fix CODEOWNERS syntax")));
                    }
                }

                // Check required paths are covered
                for (String requiredPath : policy.requiredPaths()) {
                    boolean pathCovered
                            = lines.stream().anyMatch(line -> line.startsWith(requiredPath + " "));

                    if (!pathCovered) {
                        violations.add(
                                new PolicyViolation(
                                        "missing-required-path",
                                        Severity.HIGH,
                                        String.format(
                                                "Required path not covered in CODEOWNERS: %s",
                                                requiredPath),
                                        "CODEOWNERS Policy",
                                        List.of("Add required path to CODEOWNERS")));
                    }
                }

            } catch (IOException e) {
                violations.add(
                        new PolicyViolation(
                                "codeowners-read-error",
                                Severity.CRITICAL,
                                "Failed to read CODEOWNERS file: " + e.getMessage(),
                                "CODEOWNERS Policy",
                                List.of("Fix file permissions", "Check file format")));
            }

            return violations;
        }

        private List<PolicyViolation> validatePathCoverage(
                String projectPath, Path codeOwnersPath, CodeOwnersPolicy policy) {
            List<PolicyViolation> violations = new ArrayList<>();

            // This would implement path coverage analysis
            // For now, just a placeholder check
            if (policy.requireFullCoverage()) {
                violations.add(
                        new PolicyViolation(
                                "incomplete-path-coverage",
                                Severity.MEDIUM,
                                "Not all paths are covered by CODEOWNERS (analysis needed)",
                                "CODEOWNERS Policy",
                                List.of("Review path coverage", "Add missing patterns")));
            }

            return violations;
        }

        private List<PolicyViolation> validateOwnerReferences(
                Path codeOwnersPath, CodeOwnersPolicy policy) {
            List<PolicyViolation> violations = new ArrayList<>();

            // This would validate that teams/users exist
            // Placeholder implementation
            if (!policy.validTeams().isEmpty()) {
                violations.add(
                        new PolicyViolation(
                                "owner-validation-needed",
                                Severity.LOW,
                                "Team/user existence validation not implemented",
                                "CODEOWNERS Policy",
                                List.of("Implement team validation", "Verify user accounts")));
            }

            return violations;
        }

        private boolean isValidCodeOwnersLine(String line) {
            // Basic validation - path followed by one or more owners
            String[] parts = line.split("\\s+");
            return parts.length >= 2 && parts[0].startsWith("/") || parts[0].equals("*");
        }
    }

    /**
     * Policy report generator for comprehensive governance reporting.
     */
    public static class PolicyReportGenerator {

        public String generateReport(PolicyValidationResult result, ReportFormat format) {
            return switch (format) {
                case CONSOLE ->
                    generateConsoleReport(result);
                case MARKDOWN ->
                    generateMarkdownReport(result);
                case JSON ->
                    generateJsonReport(result);
                case HTML ->
                    generateHtmlReport(result);
            };
        }

        private String generateConsoleReport(PolicyValidationResult result) {
            StringBuilder report = new StringBuilder();

            report.append("🛡️  POLICY VALIDATION REPORT\\n");
            report.append("═".repeat(60)).append("\\n");
            report.append(String.format("📅 Generated: %s%n", result.validationTime()));
            report.append(String.format("📁 Project: %s%n", result.projectPath()));
            report.append(String.format("🏆 Overall Status: %s%n", result.overallStatus()));
            report.append("\\n");

            if (result.violations().isEmpty()) {
                report.append("✅ No policy violations found!\\n");
            } else {
                report.append(
                        String.format(
                                "⚠️  %d policy violations found:\\n", result.violations().size()));
                report.append("\\n");

                // Group by category
                Map<String, List<PolicyViolation>> byCategory
                        = result.violations().stream()
                                .collect(Collectors.groupingBy(PolicyViolation::category));

                for (Map.Entry<String, List<PolicyViolation>> entry : byCategory.entrySet()) {
                    String category = entry.getKey();
                    List<PolicyViolation> violations = entry.getValue();

                    report.append(
                            String.format("📋 %s (%d violations):%n", category, violations.size()));

                    for (PolicyViolation violation : violations) {
                        String severity
                                = switch (violation.severity()) {
                            case CRITICAL ->
                                "🚨";
                            case HIGH ->
                                "🔴";
                            case MEDIUM ->
                                "🟡";
                            case LOW ->
                                "🟢";
                            default ->
                                "⚪";
                        };

                        report.append(
                                String.format(
                                        "  %s %s: %s%n",
                                        severity, violation.ruleId(), violation.message()));

                        if (!violation.recommendations().isEmpty()) {
                            report.append("    💡 Recommendations:\\n");
                            violation
                                    .recommendations()
                                    .forEach(
                                            rec
                                            -> report.append(
                                                    String.format("      • %s%n", rec)));
                        }
                        report.append("\\n");
                    }
                }
            }

            return report.toString();
        }

        private String generateMarkdownReport(PolicyValidationResult result) {
            StringBuilder md = new StringBuilder();

            md.append("# Policy Validation Report\\n\\n");
            md.append(String.format("**Generated:** %s\\n", result.validationTime()));
            md.append(String.format("**Project:** %s\\n", result.projectPath()));
            md.append(String.format("**Overall Status:** %s\\n\\n", result.overallStatus()));

            if (result.violations().isEmpty()) {
                md.append("## ✅ No Violations Found\\n\\n");
                md.append("All policy checks passed successfully!\\n");
            } else {
                md.append(
                        String.format(
                                "## ⚠️ Policy Violations (%d found)\\n\\n",
                                result.violations().size()));

                Map<String, List<PolicyViolation>> byCategory
                        = result.violations().stream()
                                .collect(Collectors.groupingBy(PolicyViolation::category));

                for (Map.Entry<String, List<PolicyViolation>> entry : byCategory.entrySet()) {
                    String category = entry.getKey();
                    List<PolicyViolation> violations = entry.getValue();

                    md.append(String.format("### %s\\n\\n", category));

                    for (PolicyViolation violation : violations) {
                        md.append(
                                String.format(
                                        "- **%s** (%s): %s\\n",
                                        violation.ruleId(),
                                        violation.severity(),
                                        violation.message()));

                        if (!violation.recommendations().isEmpty()) {
                            md.append("  - **Recommendations:**\\n");
                            violation
                                    .recommendations()
                                    .forEach(rec -> md.append(String.format("    - %s\\n", rec)));
                        }
                        md.append("\\n");
                    }
                }
            }

            return md.toString();
        }

        private String generateJsonReport(PolicyValidationResult result) {
            // Simplified JSON generation - would use proper JSON library in production
            StringBuilder json = new StringBuilder();
            json.append("{\\n");
            json.append(String.format("  \"validationTime\": \"%s\",\\n", result.validationTime()));
            json.append(String.format("  \"projectPath\": \"%s\",\\n", result.projectPath()));
            json.append(String.format("  \"overallStatus\": \"%s\",\\n", result.overallStatus()));
            json.append(String.format("  \"violationCount\": %d,\\n", result.violations().size()));
            json.append("  \"violations\": [\\n");

            for (int i = 0; i < result.violations().size(); i++) {
                PolicyViolation v = result.violations().get(i);
                json.append("    {\\n");
                json.append(String.format("      \"ruleId\": \"%s\",\\n", v.ruleId()));
                json.append(String.format("      \"severity\": \"%s\",\\n", v.severity()));
                json.append(String.format("      \"message\": \"%s\",\\n", v.message()));
                json.append(String.format("      \"category\": \"%s\"\\n", v.category()));
                json.append("    }");
                if (i < result.violations().size() - 1) {
                    json.append(",");
                }
                json.append("\\n");
            }

            json.append("  ]\\n");
            json.append("}\\n");
            return json.toString();
        }

        private String generateHtmlReport(PolicyValidationResult result) {
            // Simplified HTML generation
            return String.format(
                    "<html><head><title>Policy Report</title></head>"
                    + "<body><h1>Policy Validation Report</h1><p>Status: %s</p>"
                    + "<p>Violations: %d</p></body></html>",
                    result.overallStatus(), result.violations().size());
        }

        public enum ReportFormat {
            CONSOLE,
            MARKDOWN,
            JSON,
            HTML
        }
    }

    // Enums and data classes
    public enum PolicyStatus {
        PASSED,
        WARNING,
        FAILED
    }

    public record PolicyConfiguration(
            boolean enablePackagePolicy,
            boolean enableLicensePolicy,
            boolean enableCodeOwnersPolicy,
            PackagePolicy packagePolicy,
            LicensePolicy licensePolicy,
            CodeOwnersPolicy codeOwnersPolicy) {
        

    public static PolicyConfiguration defaultConfig() {
        return new PolicyConfiguration(
                true,
                true,
                true,
                PackagePolicy.defaultPolicy(),
                LicensePolicy.defaultPolicy(),
                CodeOwnersPolicy.defaultPolicy());
    }
}

public record PackagePolicy(
        List<String> bannedPackages,
        List<String> bannedPackagePatterns,
        List<String> requiredPackages,
        List<String> approvedSources,
        Map<String, String> versionConstraints) {

    public static PackagePolicy defaultPolicy() {
        return new PackagePolicy(
                List.of("left-pad", "event-stream"), // Example banned packages
                List.of(".*malicious.*", ".*backdoor.*"),
                List.of(),
                List.of("central", "jcenter"),
                Map.of());
    }
}

public record LicensePolicy(
        List<String> approvedLicenses,
        List<String> bannedLicenses,
        boolean requireLicenseInfo) {

    public static LicensePolicy defaultPolicy() {
        return new LicensePolicy(
                List.of("MIT", "Apache-2.0", "BSD-3-Clause", "ISC"),
                List.of("GPL-3.0", "AGPL-3.0"),
                true);
    }
}

public record CodeOwnersPolicy(
        boolean requireCodeOwners,
        List<String> requiredPaths,
        boolean requireFullCoverage,
        List<String> validTeams) {

    public static CodeOwnersPolicy defaultPolicy() {
        return new CodeOwnersPolicy(
                true, List.of("/src", "/docs", "/.github"), false, List.of());
    }
}

public record PolicyViolation(
        String ruleId,
        Severity severity,
        String message,
        String category,
        List<String> recommendations) {

}

public record PolicyValidationResult(
        List<PolicyViolation> violations,
        Map<String, PolicyStatus> policyStatuses,
        PolicyStatus overallStatus,
        LocalDateTime validationTime,
        String projectPath) {

}

public record PackageValidationResult(
        List<PolicyViolation> violations, PolicyStatus overallStatus) {

}

public record LicenseValidationResult(
        List<PolicyViolation> violations, PolicyStatus overallStatus) {

}

public record CodeOwnersValidationResult(
        List<PolicyViolation> violations, PolicyStatus overallStatus) {

}
}
