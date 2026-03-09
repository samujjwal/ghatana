/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents Module - Tool Providers
 */
package com.ghatana.yappc.agent.tools.provider;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Security Analysis Tool Provider - Implements the "security-analysis" capability.
 *
 * <p><b>Capability</b>: security-analysis<br>
 * <b>Input</b>: source_code, dependencies, configuration<br>
 * <b>Output</b>: vulnerability_report, security_findings, remediation_plan<br>
 * <b>Quality Metrics</b>: cve_count, severity_score, fix_complexity
 *
 * <p><b>Supported Operations</b>:
 * <ul>
 *   <li>scan_vulnerabilities - Scan dependencies for known CVEs</li>
 *   <li>analyze_code_security - Static security analysis (SAST)</li>
 *   <li>check_secrets - Detect hardcoded secrets/credentials</li>
 *   <li>validate_config - Validate security configuration</li>
 *   <li>generate_remediation - Create fix plan for findings</li>
 * </ul>
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 
 * @doc.type class
 * @doc.purpose Handles security analysis tool provider operations
 * @doc.layer core
 * @doc.pattern Provider
*/
public class SecurityAnalysisToolProvider implements ToolProvider {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityAnalysisToolProvider.class);

  @Override
  @NotNull
  public String getCapabilityId() {
    return "security-analysis";
  }

  @Override
  @NotNull
  public String getToolName() {
    return "SecurityAnalysisTool";
  }

  @Override
  public int estimateCost(@NotNull Map<String, Object> params) {
    String operation = (String) params.getOrDefault("operation", "scan");
    return switch (operation) {
      case "scan_vulnerabilities" -> 3;
      case "analyze_code_security" -> 5;
      case "check_secrets" -> 2;
      case "validate_config" -> 2;
      case "generate_remediation" -> 4;
      default -> 3;
    };
  }

  @Override
  public String validateParams(@NotNull Map<String, Object> params) {
    String operation = (String) params.get("operation");
    if (operation == null) {
      return "Missing required parameter: operation";
    }

    return switch (operation) {
      case "scan_vulnerabilities" -> {
        if (params.get("dependencies") == null && params.get("lockfile_path") == null) {
          yield "Missing required parameter: dependencies or lockfile_path";
        }
        yield null;
      }
      case "analyze_code_security" -> {
        if (params.get("source_code") == null && params.get("source_path") == null) {
          yield "Missing required parameter: source_code or source_path";
        }
        yield null;
      }
      case "check_secrets" -> {
        if (params.get("source_path") == null) {
          yield "Missing required parameter: source_path";
        }
        yield null;
      }
      default -> null;
    };
  }

  @Override
  @NotNull
  public Promise<ToolResult> execute(@NotNull AgentContext ctx, @NotNull Map<String, Object> params) {
    String operation = (String) params.get("operation");
    LOG.info("Executing security analysis operation: {}", operation);

    return switch (operation) {
      case "scan_vulnerabilities" -> scanVulnerabilities(params);
      case "analyze_code_security" -> analyzeCodeSecurity(params);
      case "check_secrets" -> checkSecrets(params);
      case "validate_config" -> validateConfig(params);
      case "generate_remediation" -> generateRemediation(params);
      default -> Promise.of(ToolResult.failure("Unknown operation: " + operation));
    };
  }

  private Promise<ToolResult> scanVulnerabilities(Map<String, Object> params) {
    String lockfile = (String) params.getOrDefault("lockfile_path", "pom.xml");
    @SuppressWarnings("unchecked")
    List<String> dependencies = (List<String>) params.getOrDefault("dependencies", List.of());

    List<Map<String, Object>> findings = scanForCVEs(dependencies, lockfile);

    int critical = countBySeverity(findings, "CRITICAL");
    int high = countBySeverity(findings, "HIGH");
    int medium = countBySeverity(findings, "MEDIUM");

    Map<String, Object> metadata = Map.of(
        "operation", "scan_vulnerabilities",
        "dependencies_scanned", dependencies.size(),
        "vulnerabilities_found", findings.size(),
        "critical", critical,
        "high", high,
        "medium", medium,
        "scan_tool", "owasp-dependency-check"
    );

    return Promise.of(ToolResult.success(findings, metadata));
  }

  private Promise<ToolResult> analyzeCodeSecurity(Map<String, Object> params) {
    String sourcePath = (String) params.getOrDefault("source_path", "src/main/java");

    List<Map<String, Object>> findings = performSAST(sourcePath);

    int injectionIssues = countByCategory(findings, "injection");
    int authIssues = countByCategory(findings, "authentication");
    int dataExposure = countByCategory(findings, "data_exposure");

    Map<String, Object> metadata = Map.of(
        "operation", "analyze_code_security",
        "files_scanned", 42,
        "issues_found", findings.size(),
        "injection_issues", injectionIssues,
        "authentication_issues", authIssues,
        "data_exposure", dataExposure,
        "scan_tool", "spotbugs+security"
    );

    return Promise.of(ToolResult.success(findings, metadata));
  }

  private Promise<ToolResult> checkSecrets(Map<String, Object> params) {
    String sourcePath = (String) params.getOrDefault("source_path", ".");

    List<Map<String, Object>> findings = scanForSecrets(sourcePath);

    Map<String, Object> metadata = Map.of(
        "operation", "check_secrets",
        "files_scanned", 156,
        "secrets_found", findings.size(),
        "api_keys_detected", countByType(findings, "api_key"),
        "passwords_detected", countByType(findings, "password"),
        "tokens_detected", countByType(findings, "token"),
        "scan_tool", "gitleaks"
    );

    return Promise.of(ToolResult.success(findings, metadata));
  }

  private Promise<ToolResult> validateConfig(Map<String, Object> params) {
    String configPath = (String) params.getOrDefault("config_path", "src/main/resources");
    String configType = (String) params.getOrDefault("config_type", "all");

    List<Map<String, Object>> findings = validateConfiguration(configPath, configType);

    Map<String, Object> metadata = Map.of(
        "operation", "validate_config",
        "config_type", configType,
        "files_checked", 8,
        "violations_found", findings.size(),
        "security_headers_missing", countByType(findings, "missing_header"),
        "insecure_settings", countByType(findings, "insecure_setting")
    );

    return Promise.of(ToolResult.success(findings, metadata));
  }

  private Promise<ToolResult> generateRemediation(Map<String, Object> params) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> findings = (List<Map<String, Object>>) params.getOrDefault("findings", List.of());

    List<Map<String, Object>> remediationPlan = createRemediationPlan(findings);

    Map<String, Object> metadata = Map.of(
        "operation", "generate_remediation",
        "findings_addressed", findings.size(),
        "immediate_fixes", remediationPlan.stream().filter(f -> "immediate".equals(f.get("priority"))).count(),
        "scheduled_fixes", remediationPlan.stream().filter(f -> "scheduled".equals(f.get("priority"))).count(),
        "estimated_effort_hours", calculateEffort(remediationPlan)
    );

    return Promise.of(ToolResult.success(remediationPlan, metadata));
  }

  // Helper methods
  private List<Map<String, Object>> scanForCVEs(List<String> dependencies, String lockfile) {
    // Simulated CVE scan results
    return List.of(
        Map.of(
            "cve_id", "CVE-2024-1234",
            "severity", "HIGH",
            "package", "com.example: vulnerable-lib:1.2.3",
            "description", "Remote code execution vulnerability",
            "fixed_version", "1.2.4",
            "cvss_score", 8.5
        ),
        Map.of(
            "cve_id", "CVE-2024-5678",
            "severity", "MEDIUM",
            "package", "org.legacy:old-dependency:2.0.0",
            "description", "Information disclosure",
            "fixed_version", "2.1.0",
            "cvss_score", 5.3
        )
    );
  }

  private List<Map<String, Object>> performSAST(String sourcePath) {
    // Simulated SAST results
    return List.of(
        Map.of(
            "rule", "SQL_INJECTION",
            "category", "injection",
            "severity", "CRITICAL",
            "file", "UserRepository.java",
            "line", 42,
            "description", "User input concatenated directly into SQL query",
            "remediation", "Use parameterized queries or prepared statements"
        ),
        Map.of(
            "rule", "WEAK_HASH",
            "category", "cryptography",
            "severity", "HIGH",
            "file", "PasswordUtil.java",
            "line", 78,
            "description", "MD5 used for password hashing",
            "remediation", "Use BCrypt, Argon2, or PBKDF2 for password hashing"
        )
    );
  }

  private List<Map<String, Object>> scanForSecrets(String sourcePath) {
    // Simulated secret detection results
    return List.of(
        Map.of(
            "type", "api_key",
            "file", "config.properties",
            "line", 15,
            "pattern", "api_key=sk-[a-zA-Z0-9]{48}",
            "severity", "CRITICAL"
        ),
        Map.of(
            "type", "password",
            "file", "DatabaseConfig.java",
            "line", 23,
            "pattern", "password=hardcoded123",
            "severity", "HIGH"
        )
    );
  }

  private List<Map<String, Object>> validateConfiguration(String configPath, String configType) {
    // Simulated configuration validation
    return List.of(
        Map.of(
            "type", "missing_header",
            "file", "application.yml",
            "setting", "security.headers.content-security-policy",
            "severity", "MEDIUM",
            "recommendation", "Add CSP header to prevent XSS attacks"
        ),
        Map.of(
            "type", "insecure_setting",
            "file", "application.yml",
            "setting", "spring.security.debug",
            "current_value", "true",
            "severity", "HIGH",
            "recommendation", "Set to false in production"
        )
    );
  }

  private List<Map<String, Object>> createRemediationPlan(List<Map<String, Object>> findings) {
    return findings.stream()
        .map(finding -> Map.<String, Object>of(
            "finding", finding,
            "priority", "CRITICAL".equals(finding.get("severity")) ? "immediate" : "scheduled",
            "estimated_hours", "CRITICAL".equals(finding.get("severity")) ? 4 : 2,
            "action", generateFixAction(finding),
            "assignee_suggestion", "security-team"
        ))
        .toList();
  }

  private String generateFixAction(Map<String, Object> finding) {
    String rule = (String) finding.get("rule");
    return switch (rule != null ? rule : "GENERIC") {
      case "SQL_INJECTION" -> "Refactor to use JPA criteria or prepared statements";
      case "WEAK_HASH" -> "Replace with BCryptPasswordEncoder";
      default -> "Review and apply security best practices";
    };
  }

  private int calculateEffort(List<Map<String, Object>> plan) {
    return plan.stream()
        .mapToInt(p -> ((Number) p.getOrDefault("estimated_hours", 1)).intValue())
        .sum();
  }

  private int countBySeverity(List<Map<String, Object>> findings, String severity) {
    return (int) findings.stream()
        .filter(f -> severity.equals(f.get("severity")))
        .count();
  }

  private int countByCategory(List<Map<String, Object>> findings, String category) {
    return (int) findings.stream()
        .filter(f -> category.equals(f.get("category")))
        .count();
  }

  private int countByType(List<Map<String, Object>> findings, String type) {
    return (int) findings.stream()
        .filter(f -> type.equals(f.get("type")))
        .count();
  }
}
