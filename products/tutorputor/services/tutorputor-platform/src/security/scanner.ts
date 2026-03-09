/**
 * Security Scanning and Compliance Framework
 *
 * Provides security scanning, compliance checks,
 * and automated validation for Tutorputor platform services.
 */

import { execSync } from "node:child_process";
import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";
import { createLogger, SecurityLogger } from "../utils/logger.js";

const logger = createLogger("security-scanner");
const securityLogger = new SecurityLogger();

type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface SecurityScanResult {
  scanType: string;
  timestamp: Date;
  status: "PASS" | "FAIL" | "WARNING";
  issues: SecurityIssue[];
  metrics: SecurityMetrics;
  compliance: ComplianceResult;
}

export interface SecurityIssue {
  id: string;
  severity: Severity;
  type: string;
  description: string;
  file?: string;
  line?: number;
  recommendation: string;
  cwe?: string;
  owasp?: string;
}

export interface SecurityMetrics {
  totalIssues: number;
  criticalIssues: number;
  highIssues: number;
  mediumIssues: number;
  lowIssues: number;
  codeCoverage: number;
  dependenciesScanned: number;
  vulnerabilitiesFound: number;
}

export interface ComplianceResult {
  owaspASVS: ComplianceStatus;
  owaspTop10: ComplianceStatus;
  gdpr: ComplianceStatus;
  ferpa: ComplianceStatus;
  soc2: ComplianceStatus;
  iso27001: ComplianceStatus;
}

export interface ComplianceStatus {
  status: "COMPLIANT" | "NON_COMPLIANT" | "PARTIALLY_COMPLIANT";
  score: number;
  requirements: Requirement[];
}

export interface Requirement {
  id: string;
  name: string;
  status: "PASS" | "FAIL" | "NOT_APPLICABLE";
  description: string;
  evidence?: string;
}

type ScanSlice = Pick<SecurityScanResult, "scanType" | "timestamp" | "status" | "issues" | "metrics" | "compliance">;

interface PatternCheck {
  idPrefix: string;
  severity: Severity;
  type: string;
  regex: RegExp;
  description: string;
  recommendation: string;
  cwe?: string;
  owasp?: string;
}

/**
 * Security scanner for static source checks and dependency checks.
 */
export class SecurityScanner {
  private readonly sourceRoot: string;
  private scanResults: SecurityScanResult[] = [];

  constructor(sourceRoot = join(process.cwd(), "src")) {
    this.sourceRoot = sourceRoot;
  }

  async runFullSecurityScan(): Promise<SecurityScanResult> {
    logger.info({ operation: "full_security_scan" }, "Starting security scan");

    const startTime = Date.now();
    const [codeAnalysis, dependencyScan, configurationScan, authenticationScan, dataProtectionScan] = await Promise.all([
      this.runCodeAnalysis(),
      this.runDependencyVulnerabilityScan(),
      this.runConfigurationSecurityScan(),
      this.runAuthenticationSecurityScan(),
      this.runDataProtectionScan(),
    ]);

    const issues = [
      ...codeAnalysis.issues,
      ...dependencyScan.issues,
      ...configurationScan.issues,
      ...authenticationScan.issues,
      ...dataProtectionScan.issues,
    ];

    for (const issue of issues) {
      if (issue.severity === "CRITICAL" || issue.severity === "HIGH") {
        securityLogger.logSecurityViolation("suspicious_activity", {
          details: { issueId: issue.id, type: issue.type, file: issue.file },
          severity: issue.severity.toLowerCase() as "low" | "medium" | "high" | "critical",
        });
      }
    }

    const metrics = this.calculateMetrics(issues);
    const compliance = this.checkCompliance(issues);

    const result: SecurityScanResult = {
      scanType: "COMPREHENSIVE",
      timestamp: new Date(),
      status: this.calculateOverallStatus(issues),
      issues,
      metrics,
      compliance,
    };

    this.scanResults.push(result);
    logger.info(
      {
        operation: "full_security_scan_complete",
        durationMs: Date.now() - startTime,
        totalIssues: issues.length,
        status: result.status,
      },
      "Security scan completed",
    );

    return result;
  }

  async runCodeAnalysis(): Promise<ScanSlice> {
    logger.info({ operation: "code_analysis" }, "Running code pattern checks");

    const sourceFiles = this.listSourceFiles(this.sourceRoot);
    const patternChecks: PatternCheck[] = [
      {
        idPrefix: "SECRET",
        severity: "CRITICAL",
        type: "HARDCODED_SECRET",
        regex: /(api[_-]?key|jwt[_-]?secret|client[_-]?secret|password)\s*[:=]\s*['"][^'"\n]{8,}['"]/gi,
        description: "Potential hardcoded secret",
        recommendation: "Move secrets to environment variables or a secret manager",
        cwe: "CWE-798",
        owasp: "A02:2021-Cryptographic Failures",
      },
      {
        idPrefix: "SQLI",
        severity: "HIGH",
        type: "SQL_INJECTION_RISK",
        regex: /(queryRawUnsafe|\$queryRawUnsafe|executeRawUnsafe|\$executeRawUnsafe)\s*\(/g,
        description: "Unsafe raw SQL API usage",
        recommendation: "Use parameterized query APIs and validated input",
        cwe: "CWE-89",
        owasp: "A03:2021-Injection",
      },
      {
        idPrefix: "XSS",
        severity: "HIGH",
        type: "XSS_RISK",
        regex: /(dangerouslySetInnerHTML|innerHTML\s*=|document\.write\s*\()/g,
        description: "Potential XSS sink",
        recommendation: "Sanitize user-controlled HTML and avoid raw DOM injection APIs",
        cwe: "CWE-79",
        owasp: "A03:2021-Injection",
      },
      {
        idPrefix: "RANDOM",
        severity: "MEDIUM",
        type: "INSECURE_RANDOM",
        regex: /Math\.random\s*\(/g,
        description: "Insecure random source",
        recommendation: "Use crypto-secure random generators for security-sensitive operations",
        cwe: "CWE-338",
        owasp: "A02:2021-Cryptographic Failures",
      },
      {
        idPrefix: "CRYPTO",
        severity: "HIGH",
        type: "WEAK_CRYPTOGRAPHY",
        regex: /(md5|sha1|des-ecb|rc4)/gi,
        description: "Weak cryptographic algorithm reference",
        recommendation: "Use modern algorithms such as SHA-256/512 and AES-GCM",
        cwe: "CWE-327",
        owasp: "A02:2021-Cryptographic Failures",
      },
    ];

    const issues: SecurityIssue[] = [];
    for (const file of sourceFiles) {
      const content = readFileSync(file, "utf8");
      for (const check of patternChecks) {
        issues.push(...this.findPatternIssues(file, content, check));
      }
    }

    return {
      scanType: "CODE_ANALYSIS",
      timestamp: new Date(),
      status: this.calculateOverallStatus(issues),
      issues,
      metrics: this.calculateMetrics(issues),
      compliance: this.emptyCompliance(),
    };
  }

  async runDependencyVulnerabilityScan(): Promise<ScanSlice> {
    logger.info({ operation: "dependency_scan" }, "Running dependency audit");

    const issues: SecurityIssue[] = [];
    const auditOutput = this.tryRunAudit();

    if (!auditOutput) {
      issues.push({
        id: "DEPENDENCY_SCAN_UNAVAILABLE",
        severity: "MEDIUM",
        type: "SCAN_ERROR",
        description: "Dependency vulnerability scan unavailable in current environment",
        recommendation: "Run pnpm audit in CI with network access and fail build on high/critical issues",
        owasp: "A06:2021-Vulnerable and Outdated Components",
      });
    } else {
      const vulnerabilities = this.parseAuditVulnerabilities(auditOutput);
      for (const vulnerability of vulnerabilities) {
        issues.push(vulnerability);
      }
    }

    return {
      scanType: "DEPENDENCY_SCAN",
      timestamp: new Date(),
      status: this.calculateOverallStatus(issues),
      issues,
      metrics: this.calculateMetrics(issues),
      compliance: this.emptyCompliance(),
    };
  }

  async runConfigurationSecurityScan(): Promise<ScanSlice> {
    logger.info({ operation: "config_scan" }, "Running configuration checks");

    const sourceFiles = this.listSourceFiles(this.sourceRoot);
    const issues: SecurityIssue[] = [];

    const source = sourceFiles
      .filter((file) => file.endsWith(".ts") || file.endsWith(".js"))
      .map((file) => readFileSync(file, "utf8"))
      .join("\n");

    if (!/helmet\s*\(/.test(source)) {
      issues.push({
        id: "CONFIG-HELMET-MISSING",
        severity: "HIGH",
        type: "MISSING_SECURITY_HEADERS",
        description: "HTTP security headers middleware not detected",
        recommendation: "Enable helmet or equivalent security headers middleware",
        owasp: "A05:2021-Security Misconfiguration",
      });
    }

    if (!/(rateLimit|rate-limit)\s*\(/.test(source)) {
      issues.push({
        id: "CONFIG-RATE-LIMIT-MISSING",
        severity: "MEDIUM",
        type: "MISSING_RATE_LIMITING",
        description: "Rate limiting middleware not detected",
        recommendation: "Apply rate limits on public and auth-sensitive endpoints",
        owasp: "A04:2021-Insecure Design",
      });
    }

    if (/origin\s*:\s*['"]\*['"]/.test(source)) {
      issues.push({
        id: "CONFIG-CORS-WILDCARD",
        severity: "HIGH",
        type: "INSECURE_CORS",
        description: "Wildcard CORS origin detected",
        recommendation: "Restrict CORS origins to trusted domains",
        owasp: "A05:2021-Security Misconfiguration",
      });
    }

    return {
      scanType: "CONFIGURATION_SCAN",
      timestamp: new Date(),
      status: this.calculateOverallStatus(issues),
      issues,
      metrics: this.calculateMetrics(issues),
      compliance: this.emptyCompliance(),
    };
  }

  async runAuthenticationSecurityScan(): Promise<ScanSlice> {
    logger.info({ operation: "auth_scan" }, "Running authentication checks");

    const sourceFiles = this.listSourceFiles(this.sourceRoot);
    const issues: SecurityIssue[] = [];

    const authContent = sourceFiles
      .filter((file) => /auth|session|token|jwt/i.test(file))
      .map((file) => ({ file, content: readFileSync(file, "utf8") }));

    if (authContent.length === 0) {
      issues.push({
        id: "AUTH-NO-MODULES",
        severity: "MEDIUM",
        type: "AUTH_CHECK_GAP",
        description: "No auth-related source files found for static checks",
        recommendation: "Define explicit auth/security modules and include them in scan scope",
        owasp: "A07:2021-Identification and Authentication Failures",
      });
    }

    for (const { file, content } of authContent) {
      if (/jwt\.sign\s*\([^\)]*expiresIn\s*[:=]\s*['"]?\s*\d+\s*['"]?\)/.test(content)) {
        issues.push({
          id: `AUTH-JWT-NO-UNIT-${this.idFromPath(file)}`,
          severity: "MEDIUM",
          type: "JWT_EXPIRY_RISK",
          description: "JWT expiresIn appears to be numeric without explicit unit",
          file: relative(process.cwd(), file),
          recommendation: "Use explicit expiry units (e.g., 15m, 1h) and short token TTL",
          owasp: "A07:2021-Identification and Authentication Failures",
        });
      }

      if (/password\s*[=!]==\s*['"][^'"]+['"]/.test(content)) {
        issues.push({
          id: `AUTH-PLAIN-CHECK-${this.idFromPath(file)}`,
          severity: "HIGH",
          type: "PLAINTEXT_PASSWORD_CHECK",
          description: "Potential plaintext password comparison detected",
          file: relative(process.cwd(), file),
          recommendation: "Use secure password hashing (argon2/bcrypt) and constant-time comparison",
          cwe: "CWE-256",
          owasp: "A02:2021-Cryptographic Failures",
        });
      }
    }

    return {
      scanType: "AUTHENTICATION_SCAN",
      timestamp: new Date(),
      status: this.calculateOverallStatus(issues),
      issues,
      metrics: this.calculateMetrics(issues),
      compliance: this.emptyCompliance(),
    };
  }

  async runDataProtectionScan(): Promise<ScanSlice> {
    logger.info({ operation: "data_protection_scan" }, "Running data-protection checks");

    const sourceFiles = this.listSourceFiles(this.sourceRoot);
    const issues: SecurityIssue[] = [];

    for (const file of sourceFiles) {
      const content = readFileSync(file, "utf8");

      if (/console\.log\s*\([^\)]*(token|password|secret|authorization)/i.test(content)) {
        issues.push({
          id: `DATA-PII-LOG-${this.idFromPath(file)}`,
          severity: "HIGH",
          type: "SENSITIVE_LOGGING",
          description: "Potential sensitive data logged to console",
          file: relative(process.cwd(), file),
          recommendation: "Remove sensitive values from logs or apply structured redaction",
          cwe: "CWE-532",
          owasp: "A09:2021-Security Logging and Monitoring Failures",
        });
      }

      if (/SELECT\s+\*\s+FROM\s+users/i.test(content)) {
        issues.push({
          id: `DATA-BROAD-QUERY-${this.idFromPath(file)}`,
          severity: "MEDIUM",
          type: "BROAD_DATA_ACCESS",
          description: "Potential broad user-data selection detected",
          file: relative(process.cwd(), file),
          recommendation: "Select only required columns and enforce least-privilege access",
          owasp: "A01:2021-Broken Access Control",
        });
      }
    }

    return {
      scanType: "DATA_PROTECTION_SCAN",
      timestamp: new Date(),
      status: this.calculateOverallStatus(issues),
      issues,
      metrics: this.calculateMetrics(issues),
      compliance: this.emptyCompliance(),
    };
  }

  checkCompliance(issues: SecurityIssue[]): ComplianceResult {
    const highOrCritical = issues.filter((issue) => issue.severity === "HIGH" || issue.severity === "CRITICAL").length;
    const medium = issues.filter((issue) => issue.severity === "MEDIUM").length;

    const complianceFromScore = (score: number): ComplianceStatus["status"] => {
      if (score >= 90) {
        return "COMPLIANT";
      }
      if (score >= 70) {
        return "PARTIALLY_COMPLIANT";
      }
      return "NON_COMPLIANT";
    };

    const baseScore = Math.max(0, 100 - highOrCritical * 15 - medium * 5);

    return {
      owaspASVS: this.buildComplianceStatus(
        "ASVS",
        Math.max(0, baseScore - 5),
        complianceFromScore(Math.max(0, baseScore - 5)),
        "Application security verification requirements",
      ),
      owaspTop10: this.buildComplianceStatus(
        "OWASP-TOP10",
        baseScore,
        complianceFromScore(baseScore),
        "Top 10 web application risk categories",
      ),
      gdpr: this.buildComplianceStatus(
        "GDPR",
        Math.max(0, baseScore - 10),
        complianceFromScore(Math.max(0, baseScore - 10)),
        "Personal data handling and privacy controls",
      ),
      ferpa: this.buildComplianceStatus(
        "FERPA",
        Math.max(0, baseScore - 8),
        complianceFromScore(Math.max(0, baseScore - 8)),
        "Education records access and protection",
      ),
      soc2: this.buildComplianceStatus(
        "SOC2",
        Math.max(0, baseScore - 12),
        complianceFromScore(Math.max(0, baseScore - 12)),
        "Security and availability control objectives",
      ),
      iso27001: this.buildComplianceStatus(
        "ISO27001",
        Math.max(0, baseScore - 15),
        complianceFromScore(Math.max(0, baseScore - 15)),
        "Information security management controls",
      ),
    };
  }

  getScanResults(): SecurityScanResult[] {
    return this.scanResults;
  }

  generateSecurityReport(): string {
    const latest = this.scanResults[this.scanResults.length - 1];
    if (!latest) {
      return "No security scan results available";
    }

    const issueLines = latest.issues
      .map((issue) => {
        const location = issue.file ? ` (${issue.file}${issue.line ? `:${issue.line}` : ""})` : "";
        return `- [${issue.severity}] ${issue.id}: ${issue.description}${location}`;
      })
      .join("\n");

    return [
      "# Security Scan Report",
      "",
      `Scan Date: ${latest.timestamp.toISOString()}`,
      `Overall Status: ${latest.status}`,
      `Total Issues: ${latest.metrics.totalIssues}`,
      `Critical: ${latest.metrics.criticalIssues}`,
      `High: ${latest.metrics.highIssues}`,
      `Medium: ${latest.metrics.mediumIssues}`,
      `Low: ${latest.metrics.lowIssues}`,
      "",
      "## Compliance",
      `- OWASP ASVS: ${latest.compliance.owaspASVS.status} (${latest.compliance.owaspASVS.score}%)`,
      `- OWASP Top 10: ${latest.compliance.owaspTop10.status} (${latest.compliance.owaspTop10.score}%)`,
      `- GDPR: ${latest.compliance.gdpr.status} (${latest.compliance.gdpr.score}%)`,
      `- FERPA: ${latest.compliance.ferpa.status} (${latest.compliance.ferpa.score}%)`,
      `- SOC2: ${latest.compliance.soc2.status} (${latest.compliance.soc2.score}%)`,
      `- ISO27001: ${latest.compliance.iso27001.status} (${latest.compliance.iso27001.score}%)`,
      "",
      "## Issues",
      issueLines || "- None",
    ].join("\n");
  }

  private calculateOverallStatus(issues: SecurityIssue[]): "PASS" | "FAIL" | "WARNING" {
    const critical = issues.filter((issue) => issue.severity === "CRITICAL").length;
    const high = issues.filter((issue) => issue.severity === "HIGH").length;

    if (critical > 0 || high > 3) {
      return "FAIL";
    }

    if (high > 0 || issues.length > 0) {
      return "WARNING";
    }

    return "PASS";
  }

  private calculateMetrics(issues: SecurityIssue[]): SecurityMetrics {
    return {
      totalIssues: issues.length,
      criticalIssues: issues.filter((issue) => issue.severity === "CRITICAL").length,
      highIssues: issues.filter((issue) => issue.severity === "HIGH").length,
      mediumIssues: issues.filter((issue) => issue.severity === "MEDIUM").length,
      lowIssues: issues.filter((issue) => issue.severity === "LOW").length,
      codeCoverage: 0,
      dependenciesScanned: 0,
      vulnerabilitiesFound: issues.filter((issue) => issue.type === "DEPENDENCY_VULNERABILITY").length,
    };
  }

  private listSourceFiles(root: string): string[] {
    let files: string[] = [];

    let entries;
    try {
      entries = readdirSync(root, { withFileTypes: true });
    } catch {
      return files;
    }

    for (const entry of entries) {
      const fullPath = join(root, entry.name);

      if (entry.isDirectory()) {
        if (["node_modules", "dist", "build", ".git"].includes(entry.name)) {
          continue;
        }
        files = files.concat(this.listSourceFiles(fullPath));
        continue;
      }

      if (/\.(ts|tsx|js|mjs|cjs)$/.test(entry.name)) {
        files.push(fullPath);
      }
    }

    return files;
  }

  private findPatternIssues(filePath: string, content: string, check: PatternCheck): SecurityIssue[] {
    const issues: SecurityIssue[] = [];
    const matches = content.matchAll(check.regex);

    for (const match of matches) {
      const index = match.index ?? 0;
      const line = content.slice(0, index).split("\n").length;
      issues.push({
        id: `${check.idPrefix}-${this.idFromPath(filePath)}-${line}`,
        severity: check.severity,
        type: check.type,
        description: check.description,
        file: relative(process.cwd(), filePath),
        line,
        recommendation: check.recommendation,
        cwe: check.cwe,
        owasp: check.owasp,
      });
    }

    return issues;
  }

  private tryRunAudit(): string | null {
    try {
      return execSync("pnpm audit --json", {
        encoding: "utf8",
        stdio: ["ignore", "pipe", "pipe"],
      });
    } catch (error) {
      const stdout = error instanceof Error && "stdout" in error ? String((error as { stdout?: unknown }).stdout ?? "") : "";
      const stderr = error instanceof Error && "stderr" in error ? String((error as { stderr?: unknown }).stderr ?? "") : "";
      const candidate = stdout.trim() || stderr.trim();

      if (candidate.startsWith("{") && candidate.endsWith("}")) {
        return candidate;
      }

      logger.warn({ error: candidate || "unknown" }, "pnpm audit failed in current environment");
      return null;
    }
  }

  private parseAuditVulnerabilities(raw: string): SecurityIssue[] {
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return [
        {
          id: "DEPENDENCY_SCAN_PARSE_ERROR",
          severity: "MEDIUM",
          type: "SCAN_ERROR",
          description: "Could not parse dependency audit output",
          recommendation: "Verify audit output format and rerun scan",
          owasp: "A06:2021-Vulnerable and Outdated Components",
        },
      ];
    }

    const vulnerabilities = (parsed.vulnerabilities ?? {}) as Record<string, { severity?: string; title?: string; cwe?: string | string[] }>;
    const issues: SecurityIssue[] = [];

    for (const [packageName, vulnerability] of Object.entries(vulnerabilities)) {
      const severity = this.mapAuditSeverity(vulnerability.severity);
      issues.push({
        id: `DEP-${packageName}`,
        severity,
        type: "DEPENDENCY_VULNERABILITY",
        description: vulnerability.title ? `${vulnerability.title} in ${packageName}` : `Vulnerability found in ${packageName}`,
        recommendation: `Upgrade ${packageName} to a non-vulnerable version`,
        cwe: Array.isArray(vulnerability.cwe) ? vulnerability.cwe.join(",") : vulnerability.cwe,
        owasp: "A06:2021-Vulnerable and Outdated Components",
      });
    }

    return issues;
  }

  private mapAuditSeverity(input?: string): Severity {
    const value = (input ?? "").toLowerCase();
    if (value === "critical") {
      return "CRITICAL";
    }
    if (value === "high") {
      return "HIGH";
    }
    if (value === "moderate" || value === "medium") {
      return "MEDIUM";
    }
    return "LOW";
  }

  private buildComplianceStatus(id: string, score: number, status: ComplianceStatus["status"], description: string): ComplianceStatus {
    const requirementStatus: Requirement["status"] = status === "COMPLIANT" ? "PASS" : status === "PARTIALLY_COMPLIANT" ? "FAIL" : "FAIL";
    return {
      status,
      score,
      requirements: [
        {
          id: `${id}-CORE`,
          name: `${id} core controls`,
          status: requirementStatus,
          description,
        },
      ],
    };
  }

  private emptyCompliance(): ComplianceResult {
    const empty: ComplianceStatus = {
      status: "PARTIALLY_COMPLIANT",
      score: 0,
      requirements: [],
    };

    return {
      owaspASVS: empty,
      owaspTop10: empty,
      gdpr: empty,
      ferpa: empty,
      soc2: empty,
      iso27001: empty,
    };
  }

  private idFromPath(filePath: string): string {
    return relative(this.sourceRoot, filePath)
      .replace(/[^a-zA-Z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .toUpperCase();
  }
}

export const securityScanner = new SecurityScanner();
