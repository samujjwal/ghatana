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

package com.ghatana.yappc.core.security;

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.yappc.core.ci.CIPipelineSpec;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Enhanced security scanning integration for CI/CD pipelines. Provides comprehensive Semgrep and
 * Gitleaks integration with advanced configuration.
 *
 * <p>Week 9 Day 44: Semgrep & Gitleaks CI integration with advanced security policies.
 *
 * @doc.type class
 * @doc.purpose Enhanced security scanning integration for CI/CD pipelines. Provides comprehensive Semgrep and
 * @doc.layer platform
 * @doc.pattern Integration
 */
public class SecurityScanningIntegration {

    private final SemgrepConfigGenerator semgrepGenerator;
    private final GitleaksConfigGenerator gitleaksGenerator;
    private final SecurityPolicyManager policyManager;

    public SecurityScanningIntegration() {
        this.semgrepGenerator = new SemgrepConfigGenerator();
        this.gitleaksGenerator = new GitleaksConfigGenerator();
        this.policyManager = new SecurityPolicyManager();
    }

    /**
 * Generates enhanced security workflow for GitHub Actions with Semgrep and Gitleaks. */
    public String generateEnhancedSecurityWorkflow(CIPipelineSpec spec, SecurityConfig config) {
        StringBuilder workflow = new StringBuilder();

        workflow.append("name: Enhanced Security Scan\\n\\n");

        workflow.append("on:\\n");
        workflow.append("  push:\\n");
        workflow.append("    branches: [main, develop]\\n");
        workflow.append("  pull_request:\\n");
        workflow.append("    branches: [main, develop]\\n");

        if (config.enableScheduledScans()) {
            workflow.append("  schedule:\\n");
            workflow.append("    - cron: '").append(config.scheduleCron()).append("'\\n");
        }

        workflow.append("\\n");
        workflow.append("permissions:\\n");
        workflow.append("  contents: read\\n");
        workflow.append("  security-events: write\\n");
        workflow.append("  pull-requests: write\\n");
        workflow.append("\\n");

        workflow.append("jobs:\\n");

        // Secrets scanning job
        if (config.enableSecretsScanning()) {
            generateSecretsJob(workflow, config);
        }

        // SAST scanning job
        if (config.enableSastScanning()) {
            generateSastJob(workflow, config, spec);
        }

        // Dependency scanning job
        if (config.enableDependencyScanning()) {
            generateDependencyJob(workflow, config);
        }

        // License compliance job
        if (config.enableLicenseScanning()) {
            generateLicenseJob(workflow, config);
        }

        // Security report aggregation job
        generateReportJob(workflow, config);

        return workflow.toString();
    }

    /**
 * Generates GitLab CI security configuration. */
    public String generateGitLabSecurityConfig(CIPipelineSpec spec, SecurityConfig config) {
        StringBuilder gitlab = new StringBuilder();

        gitlab.append("# Enhanced Security Scanning Configuration\\n");
        gitlab.append("\\n");

        gitlab.append("stages:\\n");
        gitlab.append("  - security-scan\\n");
        gitlab.append("  - security-report\\n");
        gitlab.append("\\n");

        gitlab.append("variables:\\n");
        gitlab.append("  SECURE_LOG_LEVEL: info\\n");
        gitlab.append("  SEMGREP_RULES: p/security-audit p/owasp-top-ten p/cwe-top-25\\n");
        gitlab.append("\\n");

        if (config.enableSecretsScanning()) {
            generateGitLabSecretsJob(gitlab, config);
        }

        if (config.enableSastScanning()) {
            generateGitLabSastJob(gitlab, config);
        }

        return gitlab.toString();
    }

    /**
 * Generates Azure DevOps security pipeline. */
    public String generateAzureSecurityPipeline(CIPipelineSpec spec, SecurityConfig config) {
        StringBuilder azure = new StringBuilder();

        azure.append("# Enhanced Security Pipeline for Azure DevOps\\n");
        azure.append("\\n");

        azure.append("trigger:\\n");
        azure.append("  branches:\\n");
        azure.append("    include: [main, develop]\\n");
        azure.append("\\n");

        azure.append("pr:\\n");
        azure.append("  branches:\\n");
        azure.append("    include: [main, develop]\\n");
        azure.append("\\n");

        azure.append("pool:\\n");
        azure.append("  vmImage: 'ubuntu-latest'\\n");
        azure.append("\\n");

        azure.append("stages:\\n");
        azure.append("- stage: SecurityScan\\n");
        azure.append("  displayName: 'Security Scanning'\\n");
        azure.append("  jobs:\\n");

        if (config.enableSecretsScanning()) {
            generateAzureSecretsJob(azure, config);
        }

        if (config.enableSastScanning()) {
            generateAzureSastJob(azure, config);
        }

        return azure.toString();
    }

    private void generateSecretsJob(StringBuilder workflow, SecurityConfig config) {
        workflow.append("  secrets-scan:\\n");
        workflow.append("    name: Secrets Detection\\n");
        workflow.append("    runs-on: ubuntu-latest\\n");
        workflow.append("    steps:\\n");
        workflow.append("      - name: Checkout code\\n");
        workflow.append("        uses: actions/checkout@v4\\n");
        workflow.append("        with:\\n");
        workflow.append("          fetch-depth: 0\\n");
        workflow.append("\\n");

        // Gitleaks scanning
        workflow.append("      - name: Run Gitleaks\\n");
        workflow.append("        uses: gitleaks/gitleaks-action@v2\\n");
        workflow.append("        env:\\n");
        workflow.append("          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}\\n");
        workflow.append("          GITLEAKS_LICENSE: ${{ secrets.GITLEAKS_LICENSE }}\\n");
        workflow.append("        with:\\n");
        workflow.append("          config-path: .gitleaks.toml\\n");

        if (config.failOnSecretsFound()) {
            workflow.append("          fail: true\\n");
        }

        workflow.append("\\n");

        // TruffleHog scanning as backup
        workflow.append("      - name: Run TruffleHog\\n");
        workflow.append("        uses: trufflesecurity/trufflehog@main\\n");
        workflow.append("        with:\\n");
        workflow.append("          path: ./\\n");
        workflow.append("          base: ${{ github.event.repository.default_branch }}\\n");
        workflow.append("          head: HEAD\\n");
        workflow.append("          extra_args: --debug --only-verified\\n");
        workflow.append("\\n");

        // Upload results
        workflow.append("      - name: Upload secrets scan results\\n");
        workflow.append("        uses: actions/upload-artifact@v4\\n");
        workflow.append("        if: always()\\n");
        workflow.append("        with:\\n");
        workflow.append("          name: secrets-scan-results\\n");
        workflow.append("          path: |\\n");
        workflow.append("            gitleaks-report.json\\n");
        workflow.append("            trufflehog-results.json\\n");
        workflow.append("\\n");
    }

    private void generateSastJob(
            StringBuilder workflow, SecurityConfig config, CIPipelineSpec spec) {
        workflow.append("  sast-scan:\\n");
        workflow.append("    name: Static Application Security Testing\\n");
        workflow.append("    runs-on: ubuntu-latest\\n");
        workflow.append("    steps:\\n");
        workflow.append("      - name: Checkout code\\n");
        workflow.append("        uses: actions/checkout@v4\\n");
        workflow.append("\\n");

        // Semgrep scanning
        workflow.append("      - name: Run Semgrep\\n");
        workflow.append("        uses: semgrep/semgrep-action@v1\\n");
        workflow.append("        with:\\n");
        workflow.append("          config: >-\\n");
        workflow.append("            p/security-audit\\n");
        workflow.append("            p/owasp-top-ten\\n");
        workflow.append("            p/cwe-top-25\\n");

        // Add language-specific rules based on project type
        String projectLanguage = detectProjectLanguage(spec);
        if (projectLanguage != null) {
            switch (projectLanguage.toLowerCase()) {
                case "java" -> {
                    workflow.append("            p/java\\n");
                    workflow.append("            p/spring\\n");
                }
                case "javascript", "typescript" -> {
                    workflow.append("            p/javascript\\n");
                    workflow.append("            p/typescript\\n");
                    workflow.append("            p/react\\n");
                }
                case "python" -> {
                    workflow.append("            p/python\\n");
                    workflow.append("            p/django\\n");
                    workflow.append("            p/flask\\n");
                }
                case "go" -> workflow.append("            p/golang\\n");
                case "rust" -> workflow.append("            p/rust\\n");
            }
        }

        workflow.append("          generateSarif: ")
                .append(config.generateSarif() ? "1" : "0")
                .append("\\n");

        if (config.semgrepTimeout() > 0) {
            workflow.append("          timeout: ").append(config.semgrepTimeout()).append("\\n");
        }

        workflow.append("        env:\\n");
        workflow.append("          SEMGREP_APP_TOKEN: ${{ secrets.SEMGREP_APP_TOKEN }}\\n");
        workflow.append("\\n");

        // CodeQL analysis for supported languages
        if (isCodeQLSupported(projectLanguage)) {
            generateCodeQLSteps(workflow, projectLanguage);
        }

        // Upload SAST results
        workflow.append("      - name: Upload SAST results\\n");
        workflow.append("        uses: github/codeql-action/upload-sarif@v3\\n");
        workflow.append("        if: always()\\n");
        workflow.append("        with:\\n");
        workflow.append("          sarif_file: semgrep.sarif\\n");
        workflow.append("\\n");
    }

    private void generateDependencyJob(StringBuilder workflow, SecurityConfig config) {
        workflow.append("  dependency-scan:\\n");
        workflow.append("    name: Dependency Security Scan\\n");
        workflow.append("    runs-on: ubuntu-latest\\n");
        workflow.append("    steps:\\n");
        workflow.append("      - name: Checkout code\\n");
        workflow.append("        uses: actions/checkout@v4\\n");
        workflow.append("\\n");

        // Trivy scanning
        workflow.append("      - name: Run Trivy vulnerability scanner\\n");
        workflow.append("        uses: aquasecurity/trivy-action@master\\n");
        workflow.append("        with:\\n");
        workflow.append("          scan-type: 'fs'\\n");
        workflow.append("          scan-ref: '.'\\n");
        workflow.append("          format: 'sarif'\\n");
        workflow.append("          output: 'trivy-results.sarif'\\n");
        workflow.append("          severity: '")
                .append(config.trivySeverityThreshold())
                .append("'\\n");
        workflow.append("\\n");

        // Snyk scanning (if token available)
        workflow.append("      - name: Run Snyk to check for vulnerabilities\\n");
        workflow.append("        uses: snyk/actions/node@master\\n");
        workflow.append("        if: env.SNYK_TOKEN != ''\\n");
        workflow.append("        env:\\n");
        workflow.append("          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}\\n");
        workflow.append("        with:\\n");
        workflow.append("          args: --severity-threshold=")
                .append(config.snykSeverityThreshold())
                .append("\\n");
        workflow.append("\\n");

        // Upload dependency scan results
        workflow.append("      - name: Upload dependency scan results\\n");
        workflow.append("        uses: github/codeql-action/upload-sarif@v3\\n");
        workflow.append("        if: always()\\n");
        workflow.append("        with:\\n");
        workflow.append("          sarif_file: trivy-results.sarif\\n");
        workflow.append("\\n");
    }

    private void generateLicenseJob(StringBuilder workflow, SecurityConfig config) {
        workflow.append("  license-scan:\\n");
        workflow.append("    name: License Compliance Check\\n");
        workflow.append("    runs-on: ubuntu-latest\\n");
        workflow.append("    steps:\\n");
        workflow.append("      - name: Checkout code\\n");
        workflow.append("        uses: actions/checkout@v4\\n");
        workflow.append("\\n");

        workflow.append("      - name: License Check\\n");
        workflow.append("        uses: fossa-contrib/fossa-action@v2\\n");
        workflow.append("        with:\\n");
        workflow.append("          api-key: ${{ secrets.FOSSA_API_KEY }}\\n");
        workflow.append("\\n");
    }

    private void generateReportJob(StringBuilder workflow, SecurityConfig config) {
        workflow.append("  security-report:\\n");
        workflow.append("    name: Security Report Generation\\n");
        workflow.append("    runs-on: ubuntu-latest\\n");
        workflow.append("    needs: [");

        List<String> jobs = new ArrayList<>();
        if (config.enableSecretsScanning()) jobs.add("secrets-scan");
        if (config.enableSastScanning()) jobs.add("sast-scan");
        if (config.enableDependencyScanning()) jobs.add("dependency-scan");
        if (config.enableLicenseScanning()) jobs.add("license-scan");

        workflow.append(String.join(", ", jobs));
        workflow.append("]\\n");
        workflow.append("    if: always()\\n");
        workflow.append("    steps:\\n");
        workflow.append("      - name: Download all scan results\\n");
        workflow.append("        uses: actions/download-artifact@v4\\n");
        workflow.append("\\n");

        workflow.append("      - name: Generate Security Report\\n");
        workflow.append("        run: |\\n");
        workflow.append("          echo '# Security Scan Report' > security-report.md\\n");
        workflow.append("          echo '\\n## Scan Summary' >> security-report.md\\n");
        workflow.append("          echo 'Generated on: $(date)' >> security-report.md\\n");
        workflow.append("          # Process and aggregate results here\\n");
        workflow.append("\\n");

        workflow.append("      - name: Comment PR with Security Report\\n");
        workflow.append("        uses: thollander/actions-comment-pull-request@v2\\n");
        workflow.append("        if: github.event_name == 'pull_request'\\n");
        workflow.append("        with:\\n");
        workflow.append("          filePath: security-report.md\\n");
        workflow.append("          comment_tag: security-report\\n");
        workflow.append("\\n");
    }

    private void generateCodeQLSteps(StringBuilder workflow, String language) {
        workflow.append("      - name: Initialize CodeQL\\n");
        workflow.append("        uses: github/codeql-action/init@v3\\n");
        workflow.append("        with:\\n");
        workflow.append("          languages: ")
                .append(mapLanguageToCodeQL(language))
                .append("\\n");
        workflow.append("\\n");

        workflow.append("      - name: Autobuild\\n");
        workflow.append("        uses: github/codeql-action/autobuild@v3\\n");
        workflow.append("\\n");

        workflow.append("      - name: Perform CodeQL Analysis\\n");
        workflow.append("        uses: github/codeql-action/analyze@v3\\n");
        workflow.append("\\n");
    }

    private void generateGitLabSecretsJob(StringBuilder gitlab, SecurityConfig config) {
        gitlab.append("secrets_scan:\\n");
        gitlab.append("  stage: security-scan\\n");
        gitlab.append("  image: zricethezav/gitleaks:latest\\n");
        gitlab.append("  script:\\n");
        gitlab.append(
                "    - gitleaks detect --report-path gitleaks-report.json --report-format json\\n");
        gitlab.append("  artifacts:\\n");
        gitlab.append("    reports:\\n");
        gitlab.append("      secret_detection: gitleaks-report.json\\n");
        gitlab.append("    paths:\\n");
        gitlab.append("      - gitleaks-report.json\\n");
        gitlab.append("    expire_in: 1 week\\n");
        gitlab.append("\\n");
    }

    private void generateGitLabSastJob(StringBuilder gitlab, SecurityConfig config) {
        gitlab.append("sast_scan:\\n");
        gitlab.append("  stage: security-scan\\n");
        gitlab.append("  image: semgrep/semgrep\\n");
        gitlab.append("  script:\\n");
        gitlab.append(
                "    - semgrep --config=$SEMGREP_RULES --json --output=semgrep-results.json\\n");
        gitlab.append("  artifacts:\\n");
        gitlab.append("    reports:\\n");
        gitlab.append("      sast: semgrep-results.json\\n");
        gitlab.append("    paths:\\n");
        gitlab.append("      - semgrep-results.json\\n");
        gitlab.append("    expire_in: 1 week\\n");
        gitlab.append("\\n");
    }

    private void generateAzureSecretsJob(StringBuilder azure, SecurityConfig config) {
        azure.append("  - job: SecretsDetection\\n");
        azure.append("    displayName: 'Detect Secrets'\\n");
        azure.append("    steps:\\n");
        azure.append("    - task: Docker@2\\n");
        azure.append("      displayName: 'Run Gitleaks'\\n");
        azure.append("      inputs:\\n");
        azure.append("        command: 'run'\\n");
        azure.append(
                "        arguments: '--rm -v $(Build.SourcesDirectory):/repo"
                        + " zricethezav/gitleaks:latest detect --source=/repo"
                        + " --report-path=/repo/gitleaks-report.json'\\n");
        azure.append("\\n");

        azure.append("    - task: PublishTestResults@2\\n");
        azure.append("      condition: always()\\n");
        azure.append("      inputs:\\n");
        azure.append("        testResultsFormat: 'JUnit'\\n");
        azure.append("        testResultsFiles: 'gitleaks-report.json'\\n");
        azure.append("\\n");
    }

    private void generateAzureSastJob(StringBuilder azure, SecurityConfig config) {
        azure.append("  - job: StaticAnalysis\\n");
        azure.append("    displayName: 'Static Security Analysis'\\n");
        azure.append("    steps:\\n");
        azure.append("    - task: Docker@2\\n");
        azure.append("      displayName: 'Run Semgrep'\\n");
        azure.append("      inputs:\\n");
        azure.append("        command: 'run'\\n");
        azure.append(
                "        arguments: '--rm -v $(Build.SourcesDirectory):/src semgrep/semgrep"
                        + " --config=p/security-audit --json --output=/src/semgrep-results.json"
                        + " /src'\\n");
        azure.append("\\n");

        azure.append("    - task: PublishTestResults@2\\n");
        azure.append("      condition: always()\\n");
        azure.append("      inputs:\\n");
        azure.append("        testResultsFormat: 'JUnit'\\n");
        azure.append("        testResultsFiles: 'semgrep-results.json'\\n");
        azure.append("\\n");
    }

    private boolean isCodeQLSupported(String language) {
        if (language == null) return false;
        return Set.of("java", "javascript", "typescript", "python", "csharp", "cpp", "go", "ruby")
                .contains(language.toLowerCase());
    }

    private String mapLanguageToCodeQL(String language) {
        return switch (language.toLowerCase()) {
            case "javascript", "typescript" -> "javascript";
            case "c++", "cpp" -> "cpp";
            case "c#", "csharp" -> "csharp";
            default -> language.toLowerCase();
        };
    }

    private String detectProjectLanguage(CIPipelineSpec spec) {
        // Try to detect language from matrix build tools or environment
        if (spec.matrix() != null
                && spec.matrix().buildTools() != null
                && !spec.matrix().buildTools().isEmpty()) {
            String firstTool = spec.matrix().buildTools().get(0);
            return switch (firstTool.toLowerCase()) {
                case "maven", "gradle" -> "java";
                case "npm", "yarn", "pnpm" -> "javascript";
                case "cargo" -> "rust";
                case "go" -> "go";
                default -> "java"; // default fallback
            };
        }

        // Try to detect from environment variables
        if (spec.environment() != null) {
            if (spec.environment().containsKey("JAVA_HOME")) return "java";
            if (spec.environment().containsKey("NODE_VERSION")) return "javascript";
            if (spec.environment().containsKey("RUST_VERSION")) return "rust";
            if (spec.environment().containsKey("GO_VERSION")) return "go";
        }

        return "java"; // default fallback
    }

    /**
 * Configuration generator for Semgrep rules and policies. */
    public static class SemgrepConfigGenerator {

        public String generateSemgrepConfig(SecurityConfig config, String language) {
            StringBuilder yaml = new StringBuilder();

            yaml.append("# Semgrep Configuration\\n");
            yaml.append("rules:\\n");

            // Base security rules
            yaml.append("  - id: security-audit\\n");
            yaml.append("    patterns:\\n");
            yaml.append("      - pattern: $X\\n");
            yaml.append("    message: Security audit rule\\n");
            yaml.append("    severity: WARNING\\n");
            yaml.append("    languages: [").append(language).append("]\\n");
            yaml.append("\\n");

            return yaml.toString();
        }

        public String generateCustomRules(List<String> customPatterns) {
            StringBuilder rules = new StringBuilder();

            rules.append("rules:\\n");

            for (int i = 0; i < customPatterns.size(); i++) {
                String pattern = customPatterns.get(i);
                rules.append("  - id: custom-rule-").append(i + 1).append("\\n");
                rules.append("    patterns:\\n");
                rules.append("      - pattern: ").append(pattern).append("\\n");
                rules.append("    message: Custom security pattern detected\\n");
                rules.append("    severity: ERROR\\n");
                rules.append("    languages: [generic]\\n");
                rules.append("\\n");
            }

            return rules.toString();
        }
    }

    /**
 * Configuration generator for Gitleaks secret detection. */
    public static class GitleaksConfigGenerator {

        public String generateGitleaksConfig(SecurityConfig config) {
            StringBuilder toml = new StringBuilder();

            toml.append("# Gitleaks Configuration\\n");
            toml.append("title = 'Gitleaks Config'\\n");
            toml.append("\\n");

            toml.append("[extend]\\n");
            toml.append("useDefault = true\\n");
            toml.append("\\n");

            // Custom allowlist
            if (!config.secretsAllowlist().isEmpty()) {
                toml.append("[[rules]]\\n");
                toml.append("id = 'custom-allowlist'\\n");
                toml.append("description = 'Custom allowlist patterns'\\n");
                toml.append("allowlist = [\\n");

                for (String pattern : config.secretsAllowlist()) {
                    toml.append("  '").append(pattern).append("',\\n");
                }

                toml.append("]\\n");
                toml.append("\\n");
            }

            return toml.toString();
        }
    }

    /**
 * Security policy manager for compliance and governance. */
    public static class SecurityPolicyManager {

        public SecurityPolicy generateSecurityPolicy(CIPipelineSpec spec, SecurityConfig config) {
            List<SecurityRule> rules = new ArrayList<>();

            // Critical security rules
            rules.add(
                    new SecurityRule(
                            "no-secrets",
                            "Secrets must not be committed to repository",
                            Severity.CRITICAL,
                            true));

            rules.add(
                    new SecurityRule(
                            "dependency-scan",
                            "All dependencies must be scanned for vulnerabilities",
                            Severity.HIGH,
                            config.enableDependencyScanning()));

            rules.add(
                    new SecurityRule(
                            "sast-scan",
                            "Static application security testing required",
                            Severity.HIGH,
                            config.enableSastScanning()));

            // License compliance rules
            if (config.enableLicenseScanning()) {
                rules.add(
                        new SecurityRule(
                                "license-compliance",
                                "All dependencies must have approved licenses",
                                Severity.MEDIUM,
                                true));
            }

            return new SecurityPolicy(
                    "Security Policy v1.0",
                    rules,
                    config.failOnPolicyViolation(),
                    LocalDateTime.now());
        }
    }

    // Configuration and data classes
    public record SecurityConfig(
            boolean enableSecretsScanning,
            boolean enableSastScanning,
            boolean enableDependencyScanning,
            boolean enableLicenseScanning,
            boolean enableScheduledScans,
            String scheduleCron,
            boolean failOnSecretsFound,
            boolean failOnPolicyViolation,
            boolean generateSarif,
            int semgrepTimeout,
            String trivySeverityThreshold,
            String snykSeverityThreshold,
            List<String> secretsAllowlist,
            List<String> customSemgrepRules) {
        public static SecurityConfig defaultConfig() {
            return new SecurityConfig(
                    true, // enableSecretsScanning
                    true, // enableSastScanning
                    true, // enableDependencyScanning
                    false, // enableLicenseScanning
                    true, // enableScheduledScans
                    "0 2 * * *", // scheduleCron (daily at 2 AM)
                    true, // failOnSecretsFound
                    false, // failOnPolicyViolation (warn mode)
                    true, // generateSarif
                    300, // semgrepTimeout (5 minutes)
                    "HIGH", // trivySeverityThreshold
                    "high", // snykSeverityThreshold
                    List.of(), // secretsAllowlist
                    List.of() // customSemgrepRules
                    );
        }
    }

    public record SecurityRule(String id, String description, Severity severity, boolean enabled) {}

    public record SecurityPolicy(
            String name,
            List<SecurityRule> rules,
            boolean enforceOnFailure,
            LocalDateTime createdAt) {}
}
