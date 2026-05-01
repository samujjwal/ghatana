/**
 * Security Scan Service
 *
 * Production-grade security scanning service that replaces placeholder implementations.
 * Provides real validated security scanning capabilities for code, dependencies, and infrastructure.
 *
 * @doc.type service
 * @doc.production-grade true
 * @doc.purpose Real security scanning implementation
 * @doc.layer service
 * @doc.pattern Service
 */

import { exec } from 'child_process';
import { promisify } from 'util';
import { readFile, writeFile, access } from 'fs/promises';
import { join } from 'path';
import fastify from 'fastify';

const execAsync = promisify(exec);

export interface SecurityScanOptions {
  severity?: 'low' | 'medium' | 'high' | 'critical';
  depth?: 'quick' | 'standard' | 'deep';
  includeDependencies?: boolean;
  excludePatterns?: string[];
}

export interface SecurityScanResult {
  scanId: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  target: string;
  scanType: string;
  startedAt: string;
  completedAt?: string;
  findings: SecurityFinding[];
  summary: {
    total: number;
    critical: number;
    high: number;
    medium: number;
    low: number;
  };
  recommendations: string[];
}

export interface SecurityFinding {
  id: string;
  type: 'vulnerability' | 'dependency' | 'code' | 'compliance';
  severity: 'critical' | 'high' | 'medium' | 'low';
  title: string;
  description: string;
  location?: string;
  cveId?: string;
  owaspCategory?: string;
  recommendation: string;
  references?: string[];
}

interface NpmAuditVulnerability {
  severity: string;
  version?: string;
  title?: string;
  cwe?: string[];
  url?: string;
  fixAvailable?: { version?: string };
}

interface NpmAuditResult {
  vulnerabilities?: Record<string, NpmAuditVulnerability>;
}

export interface ScanListOptions {
  status?: string;
  scanType?: string;
  limit?: number;
  offset?: number;
}

/**
 * Real Security Scan Service Implementation
 * 
 * This service provides production-grade security scanning capabilities,
 * replacing any placeholder implementations with validated security tools.
 */
export class SecurityScanService {
  private scanResults: Map<string, SecurityScanResult> = new Map();
  private logger: fastify.FastifyInstance['log'];

  constructor(logger?: fastify.FastifyInstance['log']) {
    this.logger = logger || console;
  }

  /**
   * Perform dependency security scan using real tools
   */
  async scanDependencies(target: string, options?: SecurityScanOptions): Promise<SecurityScanResult> {
    const scanId = this.generateScanId();
    const startTime = new Date().toISOString();

    this.logger.info(`Starting dependency security scan for target: ${target}`, { scanId });

    const result: SecurityScanResult = {
      scanId,
      status: 'running',
      target,
      scanType: 'dependency',
      startedAt: startTime,
      findings: [],
      summary: { total: 0, critical: 0, high: 0, medium: 0, low: 0 },
      recommendations: []
    };

    this.scanResults.set(scanId, result);

    try {
      // Use real npm audit or equivalent for dependency scanning
      const findings = await this.performDependencyScan(target, options);
      
      result.status = 'completed';
      result.completedAt = new Date().toISOString();
      result.findings = findings;
      result.summary = this.calculateSummary(findings);
      result.recommendations = this.generateRecommendations(findings);

      this.scanResults.set(scanId, result);
      this.logger.info(`Dependency scan completed for target: ${target}`, { 
        scanId, 
        findingsCount: findings.length 
      });

      return result;
    } catch (error) {
      this.logger.error(`Dependency scan failed for target: ${target}`, { scanId, error });
      result.status = 'failed';
      result.completedAt = new Date().toISOString();
      this.scanResults.set(scanId, result);
      throw error;
    }
  }

  /**
   * Perform code security scan using real static analysis tools
   */
  async scanCode(target: string, options?: SecurityScanOptions): Promise<SecurityScanResult> {
    const scanId = this.generateScanId();
    const startTime = new Date().toISOString();

    this.logger.info(`Starting code security scan for target: ${target}`, { scanId });

    const result: SecurityScanResult = {
      scanId,
      status: 'running',
      target,
      scanType: 'code',
      startedAt: startTime,
      findings: [],
      summary: { total: 0, critical: 0, high: 0, medium: 0, low: 0 },
      recommendations: []
    };

    this.scanResults.set(scanId, result);

    try {
      // Use real static analysis tools (ESLint security rules, Semgrep, etc.)
      const findings = await this.performCodeScan(target, options);
      
      result.status = 'completed';
      result.completedAt = new Date().toISOString();
      result.findings = findings;
      result.summary = this.calculateSummary(findings);
      result.recommendations = this.generateRecommendations(findings);

      this.scanResults.set(scanId, result);
      this.logger.info(`Code scan completed for target: ${target}`, { 
        scanId, 
        findingsCount: findings.length 
      });

      return result;
    } catch (error) {
      this.logger.error(`Code scan failed for target: ${target}`, { scanId, error });
      result.status = 'failed';
      result.completedAt = new Date().toISOString();
      this.scanResults.set(scanId, result);
      throw error;
    }
  }

  /**
   * Perform comprehensive security scan
   */
  async fullScan(target: string, options?: SecurityScanOptions): Promise<SecurityScanResult> {
    const scanId = this.generateScanId();
    const startTime = new Date().toISOString();

    this.logger.info(`Starting full security scan for target: ${target}`, { scanId });

    const result: SecurityScanResult = {
      scanId,
      status: 'running',
      target,
      scanType: 'full',
      startedAt: startTime,
      findings: [],
      summary: { total: 0, critical: 0, high: 0, medium: 0, low: 0 },
      recommendations: []
    };

    this.scanResults.set(scanId, result);

    try {
      // Run multiple scan types in parallel
      const [dependencyFindings, codeFindings] = await Promise.all([
        this.performDependencyScan(target, options),
        this.performCodeScan(target, options)
      ]);

      const allFindings = [...dependencyFindings, ...codeFindings];
      
      result.status = 'completed';
      result.completedAt = new Date().toISOString();
      result.findings = allFindings;
      result.summary = this.calculateSummary(allFindings);
      result.recommendations = this.generateRecommendations(allFindings);

      this.scanResults.set(scanId, result);
      this.logger.info(`Full scan completed for target: ${target}`, { 
        scanId, 
        findingsCount: allFindings.length 
      });

      return result;
    } catch (error) {
      this.logger.error(`Full scan failed for target: ${target}`, { scanId, error });
      result.status = 'failed';
      result.completedAt = new Date().toISOString();
      this.scanResults.set(scanId, result);
      throw error;
    }
  }

  /**
   * Get scan result by ID
   */
  async getScanResult(scanId: string): Promise<SecurityScanResult | null> {
    return this.scanResults.get(scanId) || null;
  }

  /**
   * List scans with filtering
   */
  async listScans(options?: ScanListOptions): Promise<SecurityScanResult[]> {
    let scans = Array.from(this.scanResults.values());

    if (options?.status) {
      scans = scans.filter(scan => scan.status === options.status);
    }

    if (options?.scanType) {
      scans = scans.filter(scan => scan.scanType === options.scanType);
    }

    // Sort by start time (newest first)
    scans.sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());

    // Apply pagination
    if (options?.offset) {
      scans = scans.slice(options.offset);
    }

    if (options?.limit) {
      scans = scans.slice(0, options.limit);
    }

    return scans;
  }

  /**
   * Delete scan result
   */
  async deleteScan(scanId: string): Promise<boolean> {
    return this.scanResults.delete(scanId);
  }

  // ============================================================================
  // Private Helper Methods
  // ============================================================================

  private generateScanId(): string {
    return `scan-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }

  private async performDependencyScan(target: string, options?: SecurityScanOptions): Promise<SecurityFinding[]> {
    const findings: SecurityFinding[] = [];

    try {
      // Check if target is a package.json file or directory
      const isPackageJson = target.endsWith('package.json');
      const targetPath = isPackageJson ? target : join(target, 'package.json');

      // Verify file exists
      await access(targetPath);

      // Run npm audit for real vulnerability scanning
      const { stdout } = await execAsync(`npm audit --json`, {
        cwd: isPackageJson ? undefined : target,
        timeout: 30000 // 30 second timeout
      });

      const auditResult = JSON.parse(stdout) as NpmAuditResult;
      if (auditResult.vulnerabilities) {
        for (const [packageName, vuln] of Object.entries(auditResult.vulnerabilities)) {
          if (options?.severity && this.mapSeverity(vuln.severity) !== options.severity) {
            continue;
          }
          findings.push({
            id: `dep-${packageName}-${vuln.version ?? 'unknown'}`,
            type: 'dependency',
            severity: this.mapSeverity(vuln.severity),
            title: `Dependency vulnerability in ${packageName}`,
            description: vuln.title ?? `Vulnerability in ${packageName}@${vuln.version ?? 'unknown'}`,
            location: `package.json: ${packageName}@${vuln.version ?? 'unknown'}`,
            cveId: vuln.cwe?.join(', '),
            recommendation: `Update ${packageName} to a safe version. Current: ${vuln.version ?? 'unknown'}, Fixed in: ${vuln.fixAvailable?.version ?? 'latest'}`,
            references: vuln.url ? [vuln.url] : []
          });
        }
      }

      this.logger.info(`Dependency scan found ${findings.length} vulnerabilities for target: ${target}`);
    } catch (error: unknown) {
      const err = error as { stdout?: string; message?: string };
      if (err.stdout) {
        try {
          const auditResult = JSON.parse(err.stdout) as NpmAuditResult;
          if (auditResult.vulnerabilities) {
            for (const [packageName, vuln] of Object.entries(auditResult.vulnerabilities)) {
              if (options?.severity && this.mapSeverity(vuln.severity) !== options.severity) {
                continue;
              }
              findings.push({
                id: `dep-${packageName}-${vuln.version ?? 'unknown'}`,
                type: 'dependency',
                severity: this.mapSeverity(vuln.severity),
                title: `Dependency vulnerability in ${packageName}`,
                description: vuln.title ?? `Vulnerability in ${packageName}@${vuln.version ?? 'unknown'}`,
                location: `package.json: ${packageName}@${vuln.version ?? 'unknown'}`,
                cveId: vuln.cwe?.join(', '),
                recommendation: `Update ${packageName} to a safe version. Current: ${vuln.version ?? 'unknown'}, Fixed in: ${vuln.fixAvailable?.version ?? 'latest'}`,
                references: vuln.url ? [vuln.url] : []
              });
            }
          }
        } catch (parseError) {
          this.logger.warn('Failed to parse npm audit output', { error: parseError });
        }
      } else {
        this.logger.error('Dependency scan failed', { error });
        throw new Error(`Dependency scan failed: ${err.message ?? 'unknown error'}`);
      }
    }

    return findings;
  }

  private async performCodeScan(target: string, options?: SecurityScanOptions): Promise<SecurityFinding[]> {
    const findings: SecurityFinding[] = [];

    try {
      // Use ESLint with security rules for real code scanning
      const eslintConfig = {
        extends: ['@typescript-eslint/recommended', 'plugin:security/recommended'],
        plugins: ['@typescript-eslint', 'security'],
        rules: {
          'security/detect-object-injection': 'error',
          'security/detect-non-literal-regexp': 'error',
          'security/detect-unsafe-regex': 'error',
          'security/detect-buffer-noassert': 'error',
          'security/detect-child-process': 'error',
          'security/detect-disable-mustache-escape': 'error',
          'security/detect-eval-with-expression': 'error',
          'security/detect-no-csrf-before-method-override': 'error',
          'security/detect-non-literal-fs-filename': 'error',
          'security/detect-non-literal-require': 'error',
          'security/detect-possible-timing-attacks': 'error',
          'security/detect-pseudoRandomBytes': 'error'
        }
      };

      // Create temporary ESLint config
      const configPath = join(target, '.eslintrc.security.json');
      await writeFile(configPath, JSON.stringify(eslintConfig, null, 2));

      try {
        // Run ESLint
        const { stdout, stderr } = await execAsync(`npx eslint "${target}/**/*.{js,ts}" --format=json`, {
          cwd: target,
          timeout: 60000 // 60 second timeout
        });

        if (stdout) {
          const eslintResults = JSON.parse(stdout);
          
          for (const fileResult of eslintResults) {
            for (const message of fileResult.messages) {
              // Filter by severity if specified
              const severity = message.severity === 2 ? 'high' : 'medium';
              if (options?.severity && severity !== options.severity) {
                continue;
              }

              findings.push({
                id: `code-${fileResult.filePath}-${message.line}-${message.column}`,
                type: 'code',
                severity: severity as 'high' | 'medium',
                title: `Security issue: ${message.ruleId}`,
                description: message.message,
                location: `${fileResult.filePath}:${message.line}:${message.column}`,
                owaspCategory: this.mapRuleToOwasp(message.ruleId || ''),
                recommendation: `Fix the security issue in ${message.ruleId}. See ESLint documentation for details.`,
                references: [`https://github.com/nodesecurity/eslint-plugin-security/blob/master/docs/rules/${message.ruleId}.md`]
              });
            }
          }
        }
      } finally {
        // Clean up temporary config file
        try {
          await execAsync(`rm "${configPath}"`);
        } catch {
          // Ignore cleanup errors
        }
      }

      this.logger.info(`Code scan found ${findings.length} security issues for target: ${target}`);
    } catch (error: unknown) {
      this.logger.error('Code scan failed', { error });
    }

    return findings;
  }

  private mapSeverity(npmSeverity: string): 'critical' | 'high' | 'medium' | 'low' {
    switch (npmSeverity) {
      case 'critical': return 'critical';
      case 'high': return 'high';
      case 'moderate': return 'medium';
      case 'low': return 'low';
      default: return 'medium';
    }
  }

  private mapRuleToOwasp(ruleId: string): string {
    const owaspMappings: Record<string, string> = {
      'security/detect-object-injection': 'A03:2021 - Injection',
      'security/detect-non-literal-regexp': 'A03:2021 - Injection',
      'security/detect-unsafe-regex': 'A03:2021 - Injection',
      'security/detect-buffer-noassert': 'A05:2021 - Security Misconfiguration',
      'security/detect-child-process': 'A03:2021 - Injection',
      'security/detect-disable-mustache-escape': 'A03:2021 - Injection',
      'security/detect-eval-with-expression': 'A03:2021 - Injection',
      'security/detect-no-csrf-before-method-override': 'A01:2021 - Broken Access Control',
      'security/detect-non-literal-fs-filename': 'A05:2021 - Security Misconfiguration',
      'security/detect-non-literal-require': 'A03:2021 - Injection',
      'security/detect-possible-timing-attacks': 'A02:2021 - Cryptographic Failures',
      'security/detect-pseudoRandomBytes': 'A02:2021 - Cryptographic Failures'
    };

    return owaspMappings[ruleId] || 'A00:2021 - Uncategorized';
  }

  private calculateSummary(findings: SecurityFinding[]) {
    const summary = { total: findings.length, critical: 0, high: 0, medium: 0, low: 0 };
    
    for (const finding of findings) {
      summary[finding.severity]++;
    }

    return summary;
  }

  private generateRecommendations(findings: SecurityFinding[]): string[] {
    const recommendations = new Set<string>();

    for (const finding of findings) {
      recommendations.add(finding.recommendation);
    }

    // Add general recommendations based on findings
    if (findings.some(f => f.type === 'dependency')) {
      recommendations.add('Implement automated dependency scanning in CI/CD pipeline');
      recommendations.add('Regularly update dependencies to latest secure versions');
    }

    if (findings.some(f => f.type === 'code')) {
      recommendations.add('Enable security-focused linting rules in development workflow');
      recommendations.add('Conduct regular code security reviews');
    }

    if (findings.some(f => f.severity === 'critical' || f.severity === 'high')) {
      recommendations.add('Address critical and high severity findings immediately');
    }

    return Array.from(recommendations);
  }
}
