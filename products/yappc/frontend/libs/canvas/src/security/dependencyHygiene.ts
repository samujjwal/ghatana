/**
 * Dependency Hygiene & Supply Chain Security
 *
 * Provides comprehensive dependency management with:
 * - Vulnerability detection and severity assessment
 * - License validation against allowlists
 * - SBOM (Software Bill of Materials) generation
 * - Patch policy management with SLA tracking
 * - Dependency risk scoring
 *
 * @module libs/canvas/src/security/dependencyHygiene
 */

/**
 * Vulnerability severity levels
 */
export type VulnerabilitySeverity = 'low' | 'medium' | 'high' | 'critical';

/**
 * License types
 */
export type LicenseType =
  | 'permissive' // MIT, Apache, BSD
  | 'copyleft' // GPL, LGPL, AGPL
  | 'proprietary' // Commercial/private
  | 'unknown'; // Cannot determine

/**
 * Dependency risk level
 */
export type RiskLevel = 'low' | 'medium' | 'high' | 'critical';

/**
 * Vulnerability information
 */
export interface Vulnerability {
  /** Vulnerability ID (e.g., CVE-2024-1234) */
  id: string;
  /** Vulnerability severity */
  severity: VulnerabilitySeverity;
  /** Description */
  description: string;
  /** Affected versions */
  affectedVersions: string[];
  /** Fixed in version */
  fixedIn?: string;
  /** CVSS score (0-10) */
  cvssScore?: number;
  /** Published date */
  publishedAt: number;
  /** Discovery source (e.g., NVD, GitHub Advisory) */
  source: string;
  /** References/links */
  references?: string[];
}

/**
 * License information
 */
export interface License {
  /** License name (e.g., MIT, Apache-2.0) */
  name: string;
  /** License type classification */
  type: LicenseType;
  /** License text/URL */
  text?: string;
  /** Whether license is OSI approved */
  osiApproved?: boolean;
}

/**
 * Dependency information
 */
export interface Dependency {
  /** Package name */
  name: string;
  /** Version */
  version: string;
  /** License */
  license: License;
  /** Direct vs transitive dependency */
  isDirect: boolean;
  /** Known vulnerabilities */
  vulnerabilities: Vulnerability[];
  /** Last updated timestamp */
  lastUpdated: number;
  /** Package homepage/repository */
  homepage?: string;
  /** Nested dependencies */
  dependencies?: string[];
}

/**
 * SBOM (Software Bill of Materials) entry
 */
export interface SBOMEntry {
  /** Component name */
  name: string;
  /** Version */
  version: string;
  /** License */
  license: string;
  /** Direct/transitive */
  scope: 'direct' | 'transitive';
  /** Hash for integrity verification */
  hash?: string;
  /** Dependencies */
  dependencies: string[];
}

/**
 * SBOM document
 */
export interface SBOM {
  /** SBOM format version */
  version: string;
  /** Project/application name */
  project: string;
  /** Generation timestamp */
  generatedAt: number;
  /** Components list */
  components: SBOMEntry[];
  /** Metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Patch policy rule
 */
export interface PatchPolicy {
  /** Severity level this policy applies to */
  severity: VulnerabilitySeverity;
  /** SLA in days */
  slaDays: number;
  /** Whether to auto-patch */
  autoPatch: boolean;
  /** Require manual approval for patch */
  requireApproval: boolean;
}

/**
 * Dependency scan result
 */
export interface ScanResult {
  /** Scan timestamp */
  timestamp: number;
  /** Total dependencies scanned */
  totalDependencies: number;
  /** Dependencies with vulnerabilities */
  vulnerableDependencies: number;
  /** Total vulnerabilities found */
  totalVulnerabilities: number;
  /** Vulnerabilities by severity */
  vulnerabilitiesBySeverity: Record<VulnerabilitySeverity, number>;
  /** License violations (non-compliant licenses) */
  licenseViolations: string[];
  /** Overall risk level */
  riskLevel: RiskLevel;
  /** Dependencies list */
  dependencies: Dependency[];
}

/**
 * Patch recommendation
 */
export interface PatchRecommendation {
  /** Dependency name */
  dependency: string;
  /** Current version */
  currentVersion: string;
  /** Recommended version */
  recommendedVersion: string;
  /** Vulnerabilities fixed */
  fixedVulnerabilities: Vulnerability[];
  /** Priority (1-5, 5 highest) */
  priority: number;
  /** SLA deadline */
  deadline?: number;
}

/**
 * Dependency hygiene configuration
 */
export interface DependencyHygieneConfig {
  /** Enable scanning */
  enabled: boolean;
  /** Allowed license types */
  allowedLicenses: LicenseType[];
  /** Specific allowed licenses by name */
  allowedLicenseNames: string[];
  /** Blocked license names */
  blockedLicenseNames: string[];
  /** Patch policies by severity */
  patchPolicies: Map<VulnerabilitySeverity, PatchPolicy>;
  /** Fail on license violations */
  failOnLicenseViolation: boolean;
  /** Fail on vulnerabilities */
  failOnVulnerabilities: boolean;
  /** Minimum vulnerability severity to fail */
  minimumFailSeverity: VulnerabilitySeverity;
}

/**
 * Dependency scanner state
 */
interface DependencyHygieneState {
  /** Known dependencies */
  dependencies: Map<string, Dependency>;
  /** Scan history */
  scanHistory: ScanResult[];
  /** Configuration */
  config: DependencyHygieneConfig;
}

/**
 * Default patch policies
 */
const DEFAULT_PATCH_POLICIES: Map<VulnerabilitySeverity, PatchPolicy> = new Map([
  ['critical', { severity: 'critical', slaDays: 1, autoPatch: false, requireApproval: true }],
  ['high', { severity: 'high', slaDays: 7, autoPatch: false, requireApproval: true }],
  ['medium', { severity: 'medium', slaDays: 30, autoPatch: false, requireApproval: false }],
  ['low', { severity: 'low', slaDays: 90, autoPatch: false, requireApproval: false }],
]);

/**
 * Default configuration
 */
const DEFAULT_CONFIG: DependencyHygieneConfig = {
  enabled: true,
  allowedLicenses: ['permissive'],
  allowedLicenseNames: ['MIT', 'Apache-2.0', 'BSD-2-Clause', 'BSD-3-Clause', 'ISC'],
  blockedLicenseNames: ['AGPL-3.0', 'GPL-3.0'],
  patchPolicies: DEFAULT_PATCH_POLICIES,
  failOnLicenseViolation: true,
  failOnVulnerabilities: false,
  minimumFailSeverity: 'high',
};

/**
 * License type classification
 */
const LICENSE_TYPE_MAP: Record<string, LicenseType> = {
  MIT: 'permissive',
  'Apache-2.0': 'permissive',
  'BSD-2-Clause': 'permissive',
  'BSD-3-Clause': 'permissive',
  ISC: 'permissive',
  'GPL-2.0': 'copyleft',
  'GPL-3.0': 'copyleft',
  'LGPL-2.1': 'copyleft',
  'LGPL-3.0': 'copyleft',
  'AGPL-3.0': 'copyleft',
  Proprietary: 'proprietary',
  UNLICENSED: 'proprietary',
};

/**
 * Dependency Hygiene Scanner
 *
 * Provides dependency scanning, vulnerability detection, license validation,
 * and patch policy management.
 */
export class DependencyHygieneScanner {
  private state: DependencyHygieneState;

  /**
   *
   */
  constructor(config: Partial<DependencyHygieneConfig> = {}) {
    this.state = {
      dependencies: new Map(),
      scanHistory: [],
      config: {
        ...DEFAULT_CONFIG,
        ...config,
        patchPolicies: config.patchPolicies || DEFAULT_PATCH_POLICIES,
      },
    };
  }

  // ==================== Dependency Management ====================

  /**
   * Register dependency
   */
  registerDependency(
    name: string,
    version: string,
    license: License,
    isDirect: boolean,
    homepage?: string,
    dependencies?: string[]
  ): Dependency {
    const key = `${name}@${version}`;
    const dep: Dependency = {
      name,
      version,
      license,
      isDirect,
      vulnerabilities: [],
      lastUpdated: Date.now(),
      homepage,
      dependencies,
    };

    this.state.dependencies.set(key, dep);
    return dep;
  }

  /**
   * Get dependency
   */
  getDependency(name: string, version: string): Dependency | undefined {
    const key = `${name}@${version}`;
    return this.state.dependencies.get(key);
  }

  /**
   * Get all dependencies
   */
  getAllDependencies(): Dependency[] {
    return Array.from(this.state.dependencies.values());
  }

  /**
   * Add vulnerability to dependency
   */
  addVulnerability(name: string, version: string, vulnerability: Vulnerability): void {
    const key = `${name}@${version}`;
    const dep = this.state.dependencies.get(key);

    if (!dep) {
      throw new Error(`Dependency not found: ${name}@${version}`);
    }

    dep.vulnerabilities.push(vulnerability);
    dep.lastUpdated = Date.now();
    this.state.dependencies.set(key, dep);
  }

  // ==================== Vulnerability Detection ====================

  /**
   * Get vulnerabilities by severity
   */
  getVulnerabilitiesBySeverity(severity: VulnerabilitySeverity): Vulnerability[] {
    const vulnerabilities: Vulnerability[] = [];

    for (const dep of this.state.dependencies.values()) {
      for (const vuln of dep.vulnerabilities) {
        if (vuln.severity === severity) {
          vulnerabilities.push(vuln);
        }
      }
    }

    return vulnerabilities;
  }

  /**
   * Get vulnerable dependencies
   */
  getVulnerableDependencies(): Dependency[] {
    return this.getAllDependencies().filter((d) => d.vulnerabilities.length > 0);
  }

  /**
   * Check if vulnerability exceeds severity threshold
   */
  private exceedsSeverityThreshold(
    severity: VulnerabilitySeverity,
    threshold: VulnerabilitySeverity
  ): boolean {
    const levels: VulnerabilitySeverity[] = ['low', 'medium', 'high', 'critical'];
    return levels.indexOf(severity) >= levels.indexOf(threshold);
  }

  // ==================== License Validation ====================

  /**
   * Classify license type
   */
  classifyLicense(licenseName: string): LicenseType {
    return LICENSE_TYPE_MAP[licenseName] || 'unknown';
  }

  /**
   * Validate license against policy
   */
  validateLicense(license: License): { valid: boolean; reason?: string } {
    // Check blocked licenses
    if (this.state.config.blockedLicenseNames.includes(license.name)) {
      return {
        valid: false,
        reason: `License ${license.name} is explicitly blocked`,
      };
    }

    // Check allowed license names
    if (
      this.state.config.allowedLicenseNames.length > 0 &&
      this.state.config.allowedLicenseNames.includes(license.name)
    ) {
      return { valid: true };
    }

    // Check allowed license types
    if (this.state.config.allowedLicenses.includes(license.type)) {
      return { valid: true };
    }

    return {
      valid: false,
      reason: `License ${license.name} (${license.type}) is not in allowed list`,
    };
  }

  /**
   * Get license violations
   */
  getLicenseViolations(): Dependency[] {
    const violations: Dependency[] = [];

    for (const dep of this.state.dependencies.values()) {
      const validation = this.validateLicense(dep.license);
      if (!validation.valid) {
        violations.push(dep);
      }
    }

    return violations;
  }

  // ==================== SBOM Generation ====================

  /**
   * Generate SBOM (Software Bill of Materials)
   */
  generateSBOM(projectName: string): SBOM {
    const components: SBOMEntry[] = [];

    for (const dep of this.state.dependencies.values()) {
      components.push({
        name: dep.name,
        version: dep.version,
        license: dep.license.name,
        scope: dep.isDirect ? 'direct' : 'transitive',
        dependencies: dep.dependencies || [],
      });
    }

    return {
      version: '1.0.0',
      project: projectName,
      generatedAt: Date.now(),
      components,
    };
  }

  // ==================== Dependency Scanning ====================

  /**
   * Perform dependency scan
   */
  scan(): ScanResult {
    const dependencies = this.getAllDependencies();
    const vulnerableDeps = this.getVulnerableDependencies();

    // Count vulnerabilities by severity
    const vulnBySeverity: Record<VulnerabilitySeverity, number> = {
      low: 0,
      medium: 0,
      high: 0,
      critical: 0,
    };

    let totalVulnerabilities = 0;
    for (const dep of vulnerableDeps) {
      for (const vuln of dep.vulnerabilities) {
        vulnBySeverity[vuln.severity]++;
        totalVulnerabilities++;
      }
    }

    // Get license violations
    const licenseViolations = this.getLicenseViolations().map(
      (d) => `${d.name}@${d.version}`
    );

    // Calculate risk level
    const riskLevel = this.calculateRiskLevel(
      vulnBySeverity,
      licenseViolations.length
    );

    const result: ScanResult = {
      timestamp: Date.now(),
      totalDependencies: dependencies.length,
      vulnerableDependencies: vulnerableDeps.length,
      totalVulnerabilities,
      vulnerabilitiesBySeverity: vulnBySeverity,
      licenseViolations,
      riskLevel,
      dependencies,
    };

    this.state.scanHistory.push(result);
    return result;
  }

  /**
   * Calculate overall risk level
   */
  private calculateRiskLevel(
    vulnBySeverity: Record<VulnerabilitySeverity, number>,
    licenseViolationCount: number
  ): RiskLevel {
    if (vulnBySeverity.critical > 0 || licenseViolationCount > 3) {
      return 'critical';
    }
    if (vulnBySeverity.high > 2 || licenseViolationCount > 1) {
      return 'high';
    }
    if (vulnBySeverity.high > 0 || vulnBySeverity.medium > 5) {
      return 'medium';
    }
    return 'low';
  }

  /**
   * Get scan history
   */
  getScanHistory(): ScanResult[] {
    return [...this.state.scanHistory];
  }

  /**
   * Get last scan result
   */
  getLastScan(): ScanResult | undefined {
    return this.state.scanHistory[this.state.scanHistory.length - 1];
  }

  // ==================== Patch Policy ====================

  /**
   * Get patch policy for severity
   */
  getPatchPolicy(severity: VulnerabilitySeverity): PatchPolicy | undefined {
    return this.state.config.patchPolicies.get(severity);
  }

  /**
   * Set patch policy for severity
   */
  setPatchPolicy(policy: PatchPolicy): void {
    this.state.config.patchPolicies.set(policy.severity, policy);
  }

  /**
   * Get patch deadline for vulnerability
   */
  getPatchDeadline(vulnerability: Vulnerability): number | undefined {
    const policy = this.getPatchPolicy(vulnerability.severity);
    if (!policy) {
      return undefined;
    }

    return vulnerability.publishedAt + policy.slaDays * 24 * 60 * 60 * 1000;
  }

  /**
   * Check if patch is overdue
   */
  isPatchOverdue(vulnerability: Vulnerability): boolean {
    const deadline = this.getPatchDeadline(vulnerability);
    if (!deadline) {
      return false;
    }

    return Date.now() > deadline;
  }

  /**
   * Get patch recommendations
   */
  getPatchRecommendations(): PatchRecommendation[] {
    const recommendations: PatchRecommendation[] = [];
    const vulnerableDeps = this.getVulnerableDependencies();

    for (const dep of vulnerableDeps) {
      // Find latest fixed version
      const fixableVulns = dep.vulnerabilities.filter((v) => v.fixedIn);
      if (fixableVulns.length === 0) {
        continue;
      }

      // Group by fixed version
      const versionMap = new Map<string, Vulnerability[]>();
      for (const vuln of fixableVulns) {
        const version = vuln.fixedIn!;
        if (!versionMap.has(version)) {
          versionMap.set(version, []);
        }
        versionMap.get(version)!.push(vuln);
      }

      // Create recommendation for each version
      for (const [version, vulns] of versionMap.entries()) {
        const priority = this.calculatePatchPriority(vulns);
        const deadline = Math.min(
          ...vulns.map((v) => this.getPatchDeadline(v) || Infinity)
        );

        recommendations.push({
          dependency: dep.name,
          currentVersion: dep.version,
          recommendedVersion: version,
          fixedVulnerabilities: vulns,
          priority,
          deadline: deadline === Infinity ? undefined : deadline,
        });
      }
    }

    // Sort by priority (highest first)
    return recommendations.sort((a, b) => b.priority - a.priority);
  }

  /**
   * Calculate patch priority (1-5)
   */
  private calculatePatchPriority(vulnerabilities: Vulnerability[]): number {
    let priority = 0;

    for (const vuln of vulnerabilities) {
      switch (vuln.severity) {
        case 'critical':
          priority += 5;
          break;
        case 'high':
          priority += 3;
          break;
        case 'medium':
          priority += 2;
          break;
        case 'low':
          priority += 1;
          break;
      }

      // Increase priority if overdue
      if (this.isPatchOverdue(vuln)) {
        priority += 2;
      }
    }

    return Math.min(priority, 5);
  }

  // ==================== Validation ====================

  /**
   * Validate scan result against policy
   */
  validateScanResult(result: ScanResult): { passed: boolean; reasons: string[] } {
    const reasons: string[] = [];

    // Check license violations
    if (
      this.state.config.failOnLicenseViolation &&
      result.licenseViolations.length > 0
    ) {
      reasons.push(
        `Found ${result.licenseViolations.length} license violation(s): ${result.licenseViolations.join(', ')}`
      );
    }

    // Check vulnerabilities
    if (this.state.config.failOnVulnerabilities) {
      const threshold = this.state.config.minimumFailSeverity;
      const criticalCount = result.vulnerabilitiesBySeverity.critical;
      const highCount = result.vulnerabilitiesBySeverity.high;
      const mediumCount = result.vulnerabilitiesBySeverity.medium;
      const lowCount = result.vulnerabilitiesBySeverity.low;

      if (
        (threshold === 'low' && (lowCount > 0 || mediumCount > 0 || highCount > 0 || criticalCount > 0)) ||
        (threshold === 'medium' && (mediumCount > 0 || highCount > 0 || criticalCount > 0)) ||
        (threshold === 'high' && (highCount > 0 || criticalCount > 0)) ||
        (threshold === 'critical' && criticalCount > 0)
      ) {
        reasons.push(
          `Found vulnerabilities exceeding ${threshold} severity: ${criticalCount} critical, ${highCount} high, ${mediumCount} medium, ${lowCount} low`
        );
      }
    }

    return {
      passed: reasons.length === 0,
      reasons,
    };
  }

  // ==================== Configuration ====================

  /**
   * Get current configuration
   */
  getConfig(): DependencyHygieneConfig {
    return {
      ...this.state.config,
      patchPolicies: new Map(this.state.config.patchPolicies),
    };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<DependencyHygieneConfig>): void {
    this.state.config = {
      ...this.state.config,
      ...updates,
    };

    if (updates.patchPolicies) {
      this.state.config.patchPolicies = updates.patchPolicies;
    }
  }

  /**
   * Reset scanner state
   */
  reset(): void {
    this.state.dependencies.clear();
    this.state.scanHistory = [];
  }
}
