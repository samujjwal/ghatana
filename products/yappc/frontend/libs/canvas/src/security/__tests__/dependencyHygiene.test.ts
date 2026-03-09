/**
 * Tests for Dependency Hygiene Scanner
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  DependencyHygieneScanner,
  type Dependency,
  type License,
  type Vulnerability,
} from '../dependencyHygiene';

describe.skip('DependencyHygieneScanner', () => {
  let scanner: DependencyHygieneScanner;

  beforeEach(() => {
    scanner = new DependencyHygieneScanner();
  });

  describe('initialization', () => {
    it('should initialize with default configuration', () => {
      const config = scanner.getConfig();
      expect(config.enabled).toBe(true);
      expect(config.allowedLicenses).toEqual(['permissive']);
      expect(config.allowedLicenseNames).toContain('MIT');
      expect(config.blockedLicenseNames).toContain('GPL-3.0');
      expect(config.failOnLicenseViolation).toBe(true);
      expect(config.failOnVulnerabilities).toBe(false);
      expect(config.minimumFailSeverity).toBe('high');
    });

    it('should initialize with custom configuration', () => {
      const customScanner = new DependencyHygieneScanner({
        enabled: false,
        failOnVulnerabilities: true,
        minimumFailSeverity: 'medium',
      });

      const config = customScanner.getConfig();
      expect(config.enabled).toBe(false);
      expect(config.failOnVulnerabilities).toBe(true);
      expect(config.minimumFailSeverity).toBe('medium');
    });
  });

  describe('dependency management', () => {
    it('should register dependency', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      const dep = scanner.registerDependency('react', '18.0.0', license, true);

      expect(dep.name).toBe('react');
      expect(dep.version).toBe('18.0.0');
      expect(dep.license).toEqual(license);
      expect(dep.isDirect).toBe(true);
      expect(dep.vulnerabilities).toEqual([]);
    });

    it('should register dependency with homepage and dependencies', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      const dep = scanner.registerDependency(
        'react',
        '18.0.0',
        license,
        true,
        'https://react.dev',
        ['scheduler@0.23.0']
      );

      expect(dep.homepage).toBe('https://react.dev');
      expect(dep.dependencies).toEqual(['scheduler@0.23.0']);
    });

    it('should get registered dependency', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('react', '18.0.0', license, true);

      const dep = scanner.getDependency('react', '18.0.0');
      expect(dep).toBeDefined();
      expect(dep?.name).toBe('react');
    });

    it('should return undefined for non-existent dependency', () => {
      const dep = scanner.getDependency('non-existent', '1.0.0');
      expect(dep).toBeUndefined();
    });

    it('should get all dependencies', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('react', '18.0.0', license, true);
      scanner.registerDependency('vue', '3.0.0', license, true);

      const deps = scanner.getAllDependencies();
      expect(deps.length).toBe(2);
    });

    it('should add vulnerability to dependency', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('lodash', '4.17.0', license, true);

      const vuln: Vulnerability = {
        id: 'CVE-2024-1234',
        severity: 'high',
        description: 'Prototype pollution',
        affectedVersions: ['<4.17.21'],
        fixedIn: '4.17.21',
        publishedAt: Date.now(),
        source: 'NVD',
      };

      scanner.addVulnerability('lodash', '4.17.0', vuln);

      const dep = scanner.getDependency('lodash', '4.17.0');
      expect(dep?.vulnerabilities).toContainEqual(vuln);
    });

    it('should throw error when adding vulnerability to non-existent dependency', () => {
      const vuln: Vulnerability = {
        id: 'CVE-2024-1234',
        severity: 'high',
        description: 'Test',
        affectedVersions: [],
        publishedAt: Date.now(),
        source: 'NVD',
      };

      expect(() => {
        scanner.addVulnerability('non-existent', '1.0.0', vuln);
      }).toThrow('Dependency not found');
    });
  });

  describe('vulnerability detection', () => {
    beforeEach(() => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('pkg1', '1.0.0', license, true);
      scanner.registerDependency('pkg2', '2.0.0', license, true);

      const criticalVuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'critical',
        description: 'Critical issue',
        affectedVersions: ['<2.0.0'],
        publishedAt: Date.now(),
        source: 'NVD',
      };

      const highVuln: Vulnerability = {
        id: 'CVE-2024-2',
        severity: 'high',
        description: 'High issue',
        affectedVersions: ['<2.0.0'],
        publishedAt: Date.now(),
        source: 'NVD',
      };

      scanner.addVulnerability('pkg1', '1.0.0', criticalVuln);
      scanner.addVulnerability('pkg2', '2.0.0', highVuln);
    });

    it('should get vulnerabilities by severity', () => {
      const critical = scanner.getVulnerabilitiesBySeverity('critical');
      expect(critical.length).toBe(1);
      expect(critical[0].id).toBe('CVE-2024-1');

      const high = scanner.getVulnerabilitiesBySeverity('high');
      expect(high.length).toBe(1);
      expect(high[0].id).toBe('CVE-2024-2');
    });

    it('should get vulnerable dependencies', () => {
      const vulnerable = scanner.getVulnerableDependencies();
      expect(vulnerable.length).toBe(2);
      expect(vulnerable.map((d) => d.name).sort()).toEqual(['pkg1', 'pkg2']);
    });

    it('should return empty array when no vulnerabilities', () => {
      const cleanScanner = new DependencyHygieneScanner();
      const license: License = { name: 'MIT', type: 'permissive' };
      cleanScanner.registerDependency('react', '18.0.0', license, true);

      const vulnerable = cleanScanner.getVulnerableDependencies();
      expect(vulnerable.length).toBe(0);
    });
  });

  describe('license validation', () => {
    it('should classify MIT as permissive', () => {
      const type = scanner.classifyLicense('MIT');
      expect(type).toBe('permissive');
    });

    it('should classify GPL as copyleft', () => {
      const type = scanner.classifyLicense('GPL-3.0');
      expect(type).toBe('copyleft');
    });

    it('should classify proprietary license', () => {
      const type = scanner.classifyLicense('Proprietary');
      expect(type).toBe('proprietary');
    });

    it('should classify unknown license', () => {
      const type = scanner.classifyLicense('CustomLicense');
      expect(type).toBe('unknown');
    });

    it('should validate allowed license', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      const result = scanner.validateLicense(license);

      expect(result.valid).toBe(true);
      expect(result.reason).toBeUndefined();
    });

    it('should reject blocked license', () => {
      const license: License = { name: 'GPL-3.0', type: 'copyleft' };
      const result = scanner.validateLicense(license);

      expect(result.valid).toBe(false);
      expect(result.reason).toContain('explicitly blocked');
    });

    it('should reject non-allowed license type', () => {
      const license: License = { name: 'CustomLicense', type: 'unknown' };
      const result = scanner.validateLicense(license);

      expect(result.valid).toBe(false);
      expect(result.reason).toContain('not in allowed list');
    });

    it('should get license violations', () => {
      const mitLicense: License = { name: 'MIT', type: 'permissive' };
      const gplLicense: License = { name: 'GPL-3.0', type: 'copyleft' };

      scanner.registerDependency('pkg1', '1.0.0', mitLicense, true);
      scanner.registerDependency('pkg2', '1.0.0', gplLicense, true);

      const violations = scanner.getLicenseViolations();
      expect(violations.length).toBe(1);
      expect(violations[0].name).toBe('pkg2');
    });
  });

  describe('SBOM generation', () => {
    it('should generate SBOM', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('react', '18.0.0', license, true, undefined, [
        'scheduler@0.23.0',
      ]);
      scanner.registerDependency('scheduler', '0.23.0', license, false);

      const sbom = scanner.generateSBOM('my-app');

      expect(sbom.version).toBe('1.0.0');
      expect(sbom.project).toBe('my-app');
      expect(sbom.generatedAt).toBeGreaterThan(0);
      expect(sbom.components.length).toBe(2);

      const react = sbom.components.find((c) => c.name === 'react');
      expect(react?.scope).toBe('direct');
      expect(react?.dependencies).toEqual(['scheduler@0.23.0']);

      const scheduler = sbom.components.find((c) => c.name === 'scheduler');
      expect(scheduler?.scope).toBe('transitive');
    });

    it('should generate empty SBOM for no dependencies', () => {
      const sbom = scanner.generateSBOM('empty-app');

      expect(sbom.components.length).toBe(0);
    });
  });

  describe('dependency scanning', () => {
    it('should perform scan and record result', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('react', '18.0.0', license, true);
      scanner.registerDependency('lodash', '4.17.0', license, true);

      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'high',
        description: 'Test',
        affectedVersions: [],
        publishedAt: Date.now(),
        source: 'NVD',
      };
      scanner.addVulnerability('lodash', '4.17.0', vuln);

      const result = scanner.scan();

      expect(result.totalDependencies).toBe(2);
      expect(result.vulnerableDependencies).toBe(1);
      expect(result.totalVulnerabilities).toBe(1);
      expect(result.vulnerabilitiesBySeverity.high).toBe(1);
      expect(result.riskLevel).toBe('medium');
    });

    it('should calculate risk level as critical for critical vulnerabilities', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('pkg', '1.0.0', license, true);

      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'critical',
        description: 'Test',
        affectedVersions: [],
        publishedAt: Date.now(),
        source: 'NVD',
      };
      scanner.addVulnerability('pkg', '1.0.0', vuln);

      const result = scanner.scan();
      expect(result.riskLevel).toBe('critical');
    });

    it('should calculate risk level as critical for many license violations', () => {
      const gplLicense: License = { name: 'GPL-3.0', type: 'copyleft' };
      scanner.registerDependency('pkg1', '1.0.0', gplLicense, true);
      scanner.registerDependency('pkg2', '1.0.0', gplLicense, true);
      scanner.registerDependency('pkg3', '1.0.0', gplLicense, true);
      scanner.registerDependency('pkg4', '1.0.0', gplLicense, true);

      const result = scanner.scan();
      expect(result.riskLevel).toBe('critical');
    });

    it('should calculate risk level as low for clean dependencies', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('react', '18.0.0', license, true);

      const result = scanner.scan();
      expect(result.riskLevel).toBe('low');
    });

    it('should get scan history', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('pkg', '1.0.0', license, true);

      scanner.scan();
      scanner.scan();

      const history = scanner.getScanHistory();
      expect(history.length).toBe(2);
    });

    it('should get last scan result', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('pkg', '1.0.0', license, true);

      scanner.scan();
      const result = scanner.getLastScan();

      expect(result).toBeDefined();
      expect(result?.totalDependencies).toBe(1);
    });

    it('should return undefined when no scans performed', () => {
      const result = scanner.getLastScan();
      expect(result).toBeUndefined();
    });
  });

  describe('patch policy', () => {
    it('should get default patch policy', () => {
      const policy = scanner.getPatchPolicy('critical');

      expect(policy).toBeDefined();
      expect(policy?.slaDays).toBe(1);
      expect(policy?.requireApproval).toBe(true);
    });

    it('should set custom patch policy', () => {
      scanner.setPatchPolicy({
        severity: 'critical',
        slaDays: 0.5,
        autoPatch: true,
        requireApproval: false,
      });

      const policy = scanner.getPatchPolicy('critical');
      expect(policy?.slaDays).toBe(0.5);
      expect(policy?.autoPatch).toBe(true);
    });

    it('should calculate patch deadline', () => {
      const publishedAt = Date.now() - 10 * 24 * 60 * 60 * 1000; // 10 days ago
      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'high',
        description: 'Test',
        affectedVersions: [],
        publishedAt,
        source: 'NVD',
      };

      const deadline = scanner.getPatchDeadline(vuln);
      expect(deadline).toBeDefined();

      // High severity has 7 day SLA
      const expectedDeadline = publishedAt + 7 * 24 * 60 * 60 * 1000;
      expect(deadline).toBe(expectedDeadline);
    });

    it('should detect overdue patches', () => {
      const publishedAt = Date.now() - 10 * 24 * 60 * 60 * 1000; // 10 days ago
      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'high', // 7 day SLA
        description: 'Test',
        affectedVersions: [],
        publishedAt,
        source: 'NVD',
      };

      const overdue = scanner.isPatchOverdue(vuln);
      expect(overdue).toBe(true); // Published 10 days ago, SLA is 7 days
    });

    it('should not detect overdue for recent vulnerabilities', () => {
      const publishedAt = Date.now() - 1 * 24 * 60 * 60 * 1000; // 1 day ago
      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'high', // 7 day SLA
        description: 'Test',
        affectedVersions: [],
        publishedAt,
        source: 'NVD',
      };

      const overdue = scanner.isPatchOverdue(vuln);
      expect(overdue).toBe(false);
    });

    it('should get patch recommendations', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('lodash', '4.17.0', license, true);

      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'high',
        description: 'Prototype pollution',
        affectedVersions: ['<4.17.21'],
        fixedIn: '4.17.21',
        publishedAt: Date.now(),
        source: 'NVD',
      };
      scanner.addVulnerability('lodash', '4.17.0', vuln);

      const recommendations = scanner.getPatchRecommendations();
      expect(recommendations.length).toBe(1);
      expect(recommendations[0].dependency).toBe('lodash');
      expect(recommendations[0].currentVersion).toBe('4.17.0');
      expect(recommendations[0].recommendedVersion).toBe('4.17.21');
      expect(recommendations[0].fixedVulnerabilities).toContainEqual(vuln);
      expect(recommendations[0].priority).toBeGreaterThan(0);
    });

    it('should not recommend patches for unfixable vulnerabilities', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('pkg', '1.0.0', license, true);

      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'high',
        description: 'No fix available',
        affectedVersions: ['*'],
        publishedAt: Date.now(),
        source: 'NVD',
      };
      scanner.addVulnerability('pkg', '1.0.0', vuln);

      const recommendations = scanner.getPatchRecommendations();
      expect(recommendations.length).toBe(0);
    });

    it('should prioritize critical vulnerabilities', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('pkg1', '1.0.0', license, true);
      scanner.registerDependency('pkg2', '1.0.0', license, true);

      const lowVuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'low',
        description: 'Low',
        affectedVersions: [],
        fixedIn: '1.0.1',
        publishedAt: Date.now(),
        source: 'NVD',
      };

      const criticalVuln: Vulnerability = {
        id: 'CVE-2024-2',
        severity: 'critical',
        description: 'Critical',
        affectedVersions: [],
        fixedIn: '1.0.1',
        publishedAt: Date.now(),
        source: 'NVD',
      };

      scanner.addVulnerability('pkg1', '1.0.0', lowVuln);
      scanner.addVulnerability('pkg2', '1.0.0', criticalVuln);

      const recommendations = scanner.getPatchRecommendations();
      expect(recommendations[0].dependency).toBe('pkg2'); // Critical should be first
    });
  });

  describe('validation', () => {
    it('should pass validation for clean scan', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('react', '18.0.0', license, true);

      const result = scanner.scan();
      const validation = scanner.validateScanResult(result);

      expect(validation.passed).toBe(true);
      expect(validation.reasons).toEqual([]);
    });

    it('should fail validation for license violations', () => {
      const gplLicense: License = { name: 'GPL-3.0', type: 'copyleft' };
      scanner.registerDependency('pkg', '1.0.0', gplLicense, true);

      const result = scanner.scan();
      const validation = scanner.validateScanResult(result);

      expect(validation.passed).toBe(false);
      expect(validation.reasons).toHaveLength(1);
      expect(validation.reasons[0]).toContain('license violation');
    });

    it('should fail validation for high vulnerabilities', () => {
      scanner.updateConfig({ failOnVulnerabilities: true });

      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('pkg', '1.0.0', license, true);

      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'high',
        description: 'Test',
        affectedVersions: [],
        publishedAt: Date.now(),
        source: 'NVD',
      };
      scanner.addVulnerability('pkg', '1.0.0', vuln);

      const result = scanner.scan();
      const validation = scanner.validateScanResult(result);

      expect(validation.passed).toBe(false);
      expect(validation.reasons[0]).toContain('vulnerabilities exceeding');
    });

    it('should not fail for low vulnerabilities when threshold is high', () => {
      scanner.updateConfig({
        failOnVulnerabilities: true,
        minimumFailSeverity: 'high',
      });

      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('pkg', '1.0.0', license, true);

      const vuln: Vulnerability = {
        id: 'CVE-2024-1',
        severity: 'low',
        description: 'Test',
        affectedVersions: [],
        publishedAt: Date.now(),
        source: 'NVD',
      };
      scanner.addVulnerability('pkg', '1.0.0', vuln);

      const result = scanner.scan();
      const validation = scanner.validateScanResult(result);

      expect(validation.passed).toBe(true);
    });
  });

  describe('configuration management', () => {
    it('should get current configuration', () => {
      const config = scanner.getConfig();

      expect(config.enabled).toBe(true);
      expect(config.allowedLicenses).toEqual(['permissive']);
      expect(config.patchPolicies).toBeDefined();
    });

    it('should update configuration', () => {
      scanner.updateConfig({
        failOnVulnerabilities: true,
        minimumFailSeverity: 'medium',
      });

      const config = scanner.getConfig();
      expect(config.failOnVulnerabilities).toBe(true);
      expect(config.minimumFailSeverity).toBe('medium');
    });
  });

  describe('reset', () => {
    it('should reset all state', () => {
      const license: License = { name: 'MIT', type: 'permissive' };
      scanner.registerDependency('react', '18.0.0', license, true);
      scanner.scan();

      scanner.reset();

      expect(scanner.getAllDependencies().length).toBe(0);
      expect(scanner.getScanHistory().length).toBe(0);
    });

    it('should preserve configuration after reset', () => {
      scanner.updateConfig({ failOnVulnerabilities: true });
      scanner.reset();

      const config = scanner.getConfig();
      expect(config.failOnVulnerabilities).toBe(true);
    });
  });
});
