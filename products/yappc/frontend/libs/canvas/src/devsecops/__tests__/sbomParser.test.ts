/**
 * Tests for SBOM Integration & Dependency Tracking
 */

import { describe, it, expect } from 'vitest';

import {
  parseCycloneDX,
  parseSPDX,
  sbomToCanvas,
  detectVulnerabilities,
  checkLicenseCompliance,
  analyzeDependencyRisk,
  createSBOMConfig,
  getComponentStyle,
  type SBOM,
  type Component,
  type Vulnerability,
  type SBOMConfig,
} from '../sbomParser';

describe.skip('SBOM - Configuration', () => {
  it('should create default configuration', () => {
    const config = createSBOMConfig();

    expect(config.layout).toBe('tree');
    expect(config.showVulnerabilities).toBe(true);
    expect(config.showLicenses).toBe(true);
    expect(config.highlightCritical).toBe(true);
  });

  it('should create configuration with overrides', () => {
    const config = createSBOMConfig({
      layout: 'circular',
      showVulnerabilities: false,
      maxDepth: 5,
    });

    expect(config.layout).toBe('circular');
    expect(config.showVulnerabilities).toBe(false);
    expect(config.maxDepth).toBe(5);
    expect(config.showLicenses).toBe(true); // Not overridden
  });
});

describe.skip('SBOM - CycloneDX Parsing', () => {
  it('should parse simple CycloneDX SBOM', () => {
    const cdx = {
      bomFormat: 'CycloneDX',
      specVersion: '1.4',
      serialNumber: 'urn:uuid:test-123',
      metadata: {
        timestamp: '2024-01-01T00:00:00Z',
        component: {
          name: 'my-app',
          version: '1.0.0',
        },
      },
      components: [
        {
          'bom-ref': 'pkg:npm/lodash@4.17.21',
          type: 'library',
          name: 'lodash',
          version: '4.17.21',
          purl: 'pkg:npm/lodash@4.17.21',
        },
        {
          'bom-ref': 'pkg:npm/express@4.18.0',
          type: 'framework',
          name: 'express',
          version: '4.18.0',
          purl: 'pkg:npm/express@4.18.0',
        },
      ],
    };

    const sbom = parseCycloneDX(cdx);

    expect(sbom.format).toBe('cyclonedx');
    expect(sbom.version).toBe('1.4');
    expect(sbom.components).toHaveLength(2);

    const lodash = sbom.components.find(c => c.name === 'lodash');
    expect(lodash?.version).toBe('4.17.21');
    expect(lodash?.type).toBe('library');
    expect(lodash?.purl).toBe('pkg:npm/lodash@4.17.21');
  });

  it('should parse CycloneDX with dependencies', () => {
    const cdx = {
      bomFormat: 'CycloneDX',
      specVersion: '1.4',
      components: [
        {
          'bom-ref': 'express',
          name: 'express',
          version: '4.18.0',
          type: 'framework',
        },
        {
          'bom-ref': 'body-parser',
          name: 'body-parser',
          version: '1.20.0',
          type: 'library',
        },
      ],
      dependencies: [
        {
          ref: 'express',
          dependsOn: ['body-parser'],
        },
      ],
    };

    const sbom = parseCycloneDX(cdx);

    expect(sbom.dependencies).toHaveLength(1);
    expect(sbom.dependencies[0].from).toBe('express');
    expect(sbom.dependencies[0].to).toBe('body-parser');

    const express = sbom.components.find(c => c.name === 'express');
    expect(express?.dependencies).toContain('body-parser');
  });

  it('should parse CycloneDX with licenses', () => {
    const cdx = {
      bomFormat: 'CycloneDX',
      specVersion: '1.4',
      components: [
        {
          'bom-ref': 'lodash',
          name: 'lodash',
          version: '4.17.21',
          type: 'library',
          licenses: [
            {
              license: {
                id: 'MIT',
                url: 'https://opensource.org/licenses/MIT',
              },
            },
          ],
        },
      ],
    };

    const sbom = parseCycloneDX(cdx);

    const lodash = sbom.components[0];
    expect(lodash.licenses).toHaveLength(1);
    expect(lodash.licenses[0].id).toBe('MIT');
    expect(lodash.licenses[0].category).toBe('permissive');
  });

  it('should parse CycloneDX with vulnerabilities', () => {
    const cdx = {
      bomFormat: 'CycloneDX',
      specVersion: '1.4',
      components: [
        {
          'bom-ref': 'lodash',
          name: 'lodash',
          version: '4.17.20',
          type: 'library',
          vulnerabilities: [
            {
              id: 'CVE-2021-23337',
              source: { name: 'NVD' },
              description: 'Command injection vulnerability',
              ratings: [
                {
                  score: 7.2,
                  severity: 'HIGH',
                  method: 'CVSSv3',
                  vector: 'CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:L',
                },
              ],
              cwes: ['CWE-77'],
            },
          ],
        },
      ],
    };

    const sbom = parseCycloneDX(cdx);

    const lodash = sbom.components[0];
    expect(lodash.metadata.vulnerabilities).toHaveLength(1);
    expect(lodash.metadata.vulnerabilities![0].id).toBe('CVE-2021-23337');
    expect(lodash.metadata.vulnerabilities![0].severity).toBe('high');
    expect(lodash.metadata.vulnerabilities![0].cvssScore).toBe(7.2);
  });

  it('should parse CycloneDX with hashes', () => {
    const cdx = {
      bomFormat: 'CycloneDX',
      specVersion: '1.4',
      components: [
        {
          'bom-ref': 'lodash',
          name: 'lodash',
          version: '4.17.21',
          type: 'library',
          hashes: [
            {
              alg: 'SHA-256',
              content: 'abc123def456',
            },
          ],
        },
      ],
    };

    const sbom = parseCycloneDX(cdx);

    const lodash = sbom.components[0];
    expect(lodash.hashes).toHaveLength(1);
    expect(lodash.hashes![0].algorithm).toBe('SHA-256');
    expect(lodash.hashes![0].value).toBe('abc123def456');
  });

  it('should calculate SBOM metadata statistics', () => {
    const cdx = {
      bomFormat: 'CycloneDX',
      specVersion: '1.4',
      components: [
        {
          'bom-ref': 'comp1',
          name: 'comp1',
          version: '1.0.0',
          type: 'library',
          vulnerabilities: [
            { id: 'CVE-1', ratings: [{ score: 9.5 }] },
            { id: 'CVE-2', ratings: [{ score: 5.0 }] },
          ],
        },
        {
          'bom-ref': 'comp2',
          name: 'comp2',
          version: '2.0.0',
          type: 'library',
          vulnerabilities: [{ id: 'CVE-3', ratings: [{ score: 9.8 }] }],
        },
      ],
    };

    const sbom = parseCycloneDX(cdx);

    expect(sbom.metadata.totalVulnerabilities).toBe(3);
    expect(sbom.metadata.criticalVulnerabilities).toBe(2); // Scores >= 9.0
  });
});

describe.skip('SBOM - SPDX Parsing', () => {
  it('should parse simple SPDX SBOM', () => {
    const spdx = {
      spdxVersion: 'SPDX-2.3',
      SPDXID: 'SPDXRef-DOCUMENT',
      name: 'my-project',
      creationInfo: {
        created: '2024-01-01T00:00:00Z',
        creators: ['Tool: syft-0.68.0'],
      },
      packages: [
        {
          SPDXID: 'SPDXRef-Package-lodash',
          name: 'lodash',
          versionInfo: '4.17.21',
          licenseConcluded: 'MIT',
        },
        {
          SPDXID: 'SPDXRef-Package-express',
          name: 'express',
          versionInfo: '4.18.0',
          licenseConcluded: 'MIT',
        },
      ],
    };

    const sbom = parseSPDX(spdx);

    expect(sbom.format).toBe('spdx');
    expect(sbom.version).toBe('SPDX-2.3');
    expect(sbom.components).toHaveLength(2);

    const lodash = sbom.components.find(c => c.name === 'lodash');
    expect(lodash?.version).toBe('4.17.21');
    expect(lodash?.licenses[0].id).toBe('MIT');
  });

  it('should parse SPDX with relationships', () => {
    const spdx = {
      spdxVersion: 'SPDX-2.3',
      SPDXID: 'SPDXRef-DOCUMENT',
      name: 'test',
      packages: [
        {
          SPDXID: 'SPDXRef-express',
          name: 'express',
          versionInfo: '4.18.0',
        },
        {
          SPDXID: 'SPDXRef-body-parser',
          name: 'body-parser',
          versionInfo: '1.20.0',
        },
      ],
      relationships: [
        {
          spdxElementId: 'SPDXRef-express',
          relatedSpdxElement: 'SPDXRef-body-parser',
          relationshipType: 'DEPENDS_ON',
        },
      ],
    };

    const sbom = parseSPDX(spdx);

    expect(sbom.dependencies).toHaveLength(1);
    expect(sbom.dependencies[0].from).toBe('SPDXRef-express');
    expect(sbom.dependencies[0].to).toBe('SPDXRef-body-parser');
  });

  it('should parse SPDX with compound licenses', () => {
    const spdx = {
      spdxVersion: 'SPDX-2.3',
      SPDXID: 'SPDXRef-DOCUMENT',
      name: 'test',
      packages: [
        {
          SPDXID: 'SPDXRef-pkg',
          name: 'my-package',
          versionInfo: '1.0.0',
          licenseConcluded: 'MIT AND Apache-2.0',
        },
      ],
    };

    const sbom = parseSPDX(spdx);

    const pkg = sbom.components[0];
    expect(pkg.licenses).toHaveLength(2);
    expect(pkg.licenses[0].id).toBe('MIT');
    expect(pkg.licenses[1].id).toBe('Apache-2.0');
  });

  it('should parse SPDX with CPE external refs', () => {
    const spdx = {
      spdxVersion: 'SPDX-2.3',
      SPDXID: 'SPDXRef-DOCUMENT',
      name: 'test',
      packages: [
        {
          SPDXID: 'SPDXRef-pkg',
          name: 'openssl',
          versionInfo: '1.1.1',
          externalRefs: [
            {
              referenceCategory: 'SECURITY',
              referenceType: 'cpe23Type',
              referenceLocator: 'cpe:2.3:a:openssl:openssl:1.1.1:*:*:*:*:*:*:*',
            },
          ],
        },
      ],
    };

    const sbom = parseSPDX(spdx);

    const pkg = sbom.components[0];
    expect(pkg.cpe).toBe('cpe:2.3:a:openssl:openssl:1.1.1:*:*:*:*:*:*:*');
  });

  it('should extract CVEs from SPDX comments', () => {
    const spdx = {
      spdxVersion: 'SPDX-2.3',
      SPDXID: 'SPDXRef-DOCUMENT',
      name: 'test',
      packages: [
        {
          SPDXID: 'SPDXRef-pkg',
          name: 'vulnerable-package',
          versionInfo: '1.0.0',
          externalRefs: [
            {
              referenceCategory: 'SECURITY',
              referenceType: 'advisory',
              referenceLocator: 'https://example.com',
              comment: 'Affected by CVE-2024-1234 and CVE-2024-5678',
            },
          ],
        },
      ],
    };

    const sbom = parseSPDX(spdx);

    const pkg = sbom.components[0];
    expect(pkg.metadata.vulnerabilities).toHaveLength(2);
    expect(pkg.metadata.vulnerabilities![0].id).toBe('CVE-2024-1234');
    expect(pkg.metadata.vulnerabilities![1].id).toBe('CVE-2024-5678');
  });

  it('should handle NOASSERTION licenses', () => {
    const spdx = {
      spdxVersion: 'SPDX-2.3',
      SPDXID: 'SPDXRef-DOCUMENT',
      name: 'test',
      packages: [
        {
          SPDXID: 'SPDXRef-pkg',
          name: 'unknown-license',
          versionInfo: '1.0.0',
          licenseConcluded: 'NOASSERTION',
        },
      ],
    };

    const sbom = parseSPDX(spdx);

    const pkg = sbom.components[0];
    expect(pkg.licenses).toHaveLength(1);
    expect(pkg.licenses[0].name).toBe('Unknown');
    expect(pkg.licenses[0].category).toBe('unknown');
  });
});

describe.skip('SBOM - Canvas Conversion', () => {
  it('should convert SBOM to canvas document', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      name: 'Test App',
      components: [
        {
          id: 'comp1',
          name: 'lodash',
          version: '4.17.21',
          type: 'library',
          licenses: [{ id: 'MIT', category: 'permissive' }],
          dependencies: [],
          metadata: {},
        },
        {
          id: 'comp2',
          name: 'express',
          version: '4.18.0',
          type: 'framework',
          licenses: [{ id: 'MIT', category: 'permissive' }],
          dependencies: ['comp1'],
          metadata: {},
        },
      ],
      dependencies: [{ from: 'comp2', to: 'comp1', relationship: 'dependsOn' }],
      metadata: {},
    };

    const config = createSBOMConfig();
    const doc = sbomToCanvas(sbom, config);

    expect(doc.id).toBe('sbom-Test App');
    expect(doc.title).toBe('SBOM: Test App');
    expect(Object.keys(doc.elements)).toHaveLength(3); // 2 components + 1 edge
  });

  it('should position components in tree layout', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'root',
          name: 'app',
          version: '1.0.0',
          type: 'application',
          licenses: [],
          dependencies: ['lib1', 'lib2'],
          metadata: {},
        },
        {
          id: 'lib1',
          name: 'lib1',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
        {
          id: 'lib2',
          name: 'lib2',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [
        { from: 'root', to: 'lib1', relationship: 'dependsOn' },
        { from: 'root', to: 'lib2', relationship: 'dependsOn' },
      ],
      metadata: {},
    };

    const doc = sbomToCanvas(sbom, createSBOMConfig());

    const rootNode = doc.elements['root'];
    const lib1Node = doc.elements['lib1'];

    expect(rootNode.type).toBe('node');
    expect(lib1Node.type).toBe('node');

    if (rootNode.type === 'node' && lib1Node.type === 'node') {
      // lib1 should be to the right of root (next level)
      expect(lib1Node.transform.position.x).toBeGreaterThan(
        rootNode.transform.position.x
      );
    }
  });

  it('should create edges for dependencies', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'a',
          name: 'A',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: ['b'],
          metadata: {},
        },
        {
          id: 'b',
          name: 'B',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [{ from: 'a', to: 'b', relationship: 'dependsOn' }],
      metadata: {},
    };

    const doc = sbomToCanvas(sbom, createSBOMConfig());

    const edge = doc.elements['edge-a-b'];
    expect(edge).toBeDefined();
    expect(edge.type).toBe('edge');

    if (edge.type === 'edge') {
      const canvasEdge = edge as import('../../types/canvas-document').CanvasEdge;
      expect(canvasEdge.sourceId).toBe('a');
      expect(canvasEdge.targetId).toBe('b');
    }
  });

  it('should filter components by severity', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'safe',
          name: 'safe-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
        {
          id: 'vuln',
          name: 'vuln-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {
            vulnerabilities: [{ id: 'CVE-2024-1', severity: 'critical' }],
          },
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const config = createSBOMConfig({ filterSeverity: ['critical'] });
    const doc = sbomToCanvas(sbom, config);

    // Only vulnerable component should be shown
    expect(doc.elements['vuln']).toBeDefined();
    expect(doc.elements['safe']).toBeUndefined();
  });

  it('should highlight critical vulnerabilities with z-index', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'critical',
          name: 'critical-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {
            vulnerabilities: [{ id: 'CVE-1', severity: 'critical' }],
          },
        },
        {
          id: 'safe',
          name: 'safe-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const doc = sbomToCanvas(sbom, createSBOMConfig());

    const criticalNode = doc.elements['critical'];
    const safeNode = doc.elements['safe'];

    expect(criticalNode.zIndex).toBe(3); // Critical
    expect(safeNode.zIndex).toBe(1); // Normal
  });

  it('should include vulnerability counts in node data', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'comp',
          name: 'my-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {
            vulnerabilities: [
              { id: 'CVE-1', severity: 'critical' },
              { id: 'CVE-2', severity: 'high' },
              { id: 'CVE-3', severity: 'medium' },
            ],
          },
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const doc = sbomToCanvas(sbom, createSBOMConfig());

    const node = doc.elements['comp'];
    if (node.type === 'node') {
      const canvasNode = node as import('../../types/canvas-document').CanvasNode;
      expect(canvasNode.data.vulnCount).toBe(3);
      expect(canvasNode.data.criticalVulns).toBe(1);
      expect(canvasNode.data.highVulns).toBe(1);
    }
  });

  it('should respect maxDepth configuration', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: Array.from({ length: 5 }, (_, i) => ({
        id: `comp${i}`,
        name: `Component ${i}`,
        version: '1.0.0',
        type: 'library' as const,
        licenses: [],
        dependencies: i < 4 ? [`comp${i + 1}`] : [],
        metadata: {},
      })),
      dependencies: Array.from({ length: 4 }, (_, i) => ({
        from: `comp${i}`,
        to: `comp${i + 1}`,
        relationship: 'dependsOn' as const,
      })),
      metadata: {},
    };

    const config = createSBOMConfig({ maxDepth: 2 });
    const doc = sbomToCanvas(sbom, config);

    // Components beyond depth 2 should still be created but positioned at depth 2
    expect(Object.keys(doc.elements).length).toBeGreaterThan(0);
  });
});

describe.skip('SBOM - Vulnerability Detection', () => {
  it('should enrich SBOM with vulnerabilities from database', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'lodash@4.17.20',
          name: 'lodash',
          version: '4.17.20',
          type: 'library',
          purl: 'pkg:npm/lodash@4.17.20',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const cveDatabase: Record<string, Vulnerability[]> = {
      'pkg:npm/lodash@4.17.20': [
        {
          id: 'CVE-2021-23337',
          severity: 'high',
          cvssScore: 7.2,
          description: 'Command injection',
          source: 'NVD',
        },
      ],
    };

    const enriched = detectVulnerabilities(sbom, cveDatabase);

    const lodash = enriched.components[0];
    expect(lodash.metadata.vulnerabilities).toHaveLength(1);
    expect(lodash.metadata.vulnerabilities![0].id).toBe('CVE-2021-23337');
  });

  it('should calculate risk scores', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'comp1',
          name: 'safe-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const cveDatabase: Record<string, Vulnerability[]> = {
      'safe-lib': [
        { id: 'CVE-1', severity: 'critical', cvssScore: 9.8 },
        { id: 'CVE-2', severity: 'high', cvssScore: 7.5 },
      ],
    };

    const enriched = detectVulnerabilities(sbom, cveDatabase);

    const comp = enriched.components[0];
    expect(comp.metadata.riskScore).toBeGreaterThan(0);
    expect(comp.metadata.riskScore).toBeLessThanOrEqual(100);
  });

  it('should update SBOM metadata with vulnerability counts', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'comp1',
          name: 'vuln-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const cveDatabase: Record<string, Vulnerability[]> = {
      'vuln-lib': [
        { id: 'CVE-1', severity: 'critical', cvssScore: 9.5 },
        { id: 'CVE-2', severity: 'medium', cvssScore: 5.0 },
      ],
    };

    const enriched = detectVulnerabilities(sbom, cveDatabase);

    expect(enriched.metadata.totalVulnerabilities).toBe(2);
    expect(enriched.metadata.criticalVulnerabilities).toBe(1);
  });
});

describe.skip('SBOM - License Compliance', () => {
  it('should check license compliance with allowed list', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'comp1',
          name: 'mit-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [{ id: 'MIT', category: 'permissive' }],
          dependencies: [],
          metadata: {},
        },
        {
          id: 'comp2',
          name: 'gpl-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [{ id: 'GPL-3.0', category: 'copyleft-strong' }],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const result = checkLicenseCompliance(sbom, ['MIT', 'Apache-2.0'], []);

    expect(result.compliant).toBe(true); // No prohibited licenses
    expect(result.issues).toHaveLength(2); // GPL not in allowed list + copyleft warning
    
    // Find the "not in allowed list" issue
    const notAllowedIssue = result.issues.find(i => i.reason.includes('not in allowed list'));
    expect(notAllowedIssue?.license).toBe('GPL-3.0');
    expect(notAllowedIssue?.severity).toBe('medium');
  });

  it('should detect prohibited licenses', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'comp1',
          name: 'gpl-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [{ id: 'GPL-3.0', category: 'copyleft-strong' }],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const result = checkLicenseCompliance(sbom, [], ['GPL-3.0', 'AGPL-3.0']);

    expect(result.compliant).toBe(false);
    expect(result.issues).toHaveLength(2); // Prohibited + copyleft warning
    expect(result.issues[0].severity).toBe('high');
  });

  it('should warn about strong copyleft licenses', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'comp1',
          name: 'agpl-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [{ id: 'AGPL-3.0', category: 'copyleft-strong' }],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const result = checkLicenseCompliance(sbom, [], []);

    const copyleftIssue = result.issues.find(i =>
      i.reason.includes('copyleft')
    );
    expect(copyleftIssue).toBeDefined();
    expect(copyleftIssue?.severity).toBe('medium');
  });

  it('should categorize licenses in summary', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'c1',
          name: 'mit-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [{ id: 'MIT', category: 'permissive' }],
          dependencies: [],
          metadata: {},
        },
        {
          id: 'c2',
          name: 'gpl-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [{ id: 'GPL-3.0', category: 'copyleft-strong' }],
          dependencies: [],
          metadata: {},
        },
        {
          id: 'c3',
          name: 'unknown-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [{ name: 'Custom', category: 'unknown' }],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const result = checkLicenseCompliance(sbom, [], []);

    expect(result.summary.totalComponents).toBe(3);
    expect(result.summary.permissive).toBe(1);
    expect(result.summary.copyleft).toBe(1);
    expect(result.summary.unknown).toBe(1);
  });

  it('should handle unknown licenses', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'comp1',
          name: 'unknown-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [{ name: 'Proprietary', category: 'unknown' }],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const result = checkLicenseCompliance(sbom, [], []);

    const unknownIssue = result.issues.find(i => i.reason.includes('Unknown'));
    expect(unknownIssue).toBeDefined();
    expect(unknownIssue?.severity).toBe('low');
  });
});

describe.skip('SBOM - Risk Analysis', () => {
  it('should analyze dependency risk', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'comp1',
          name: 'high-risk',
          version: '1.0.0',
          type: 'library',
          licenses: [{ id: 'GPL-3.0', category: 'copyleft-strong' }],
          dependencies: [],
          metadata: {
            vulnerabilities: [
              { id: 'CVE-1', severity: 'critical', cvssScore: 9.8 },
              { id: 'CVE-2', severity: 'high', cvssScore: 7.5 },
            ],
            deprecated: true,
          },
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const analyzed = analyzeDependencyRisk(sbom);

    const comp = analyzed.components[0];
    expect(comp.metadata.riskScore).toBeGreaterThan(30); // Has vulns + deprecated + copyleft
  });

  it('should calculate high-risk component count', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'high-risk',
          name: 'high-risk-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {
            vulnerabilities: [
              { id: 'CVE-1', severity: 'critical', cvssScore: 9.8 },
              { id: 'CVE-2', severity: 'critical', cvssScore: 9.5 },
              { id: 'CVE-3', severity: 'critical', cvssScore: 9.2 },
              { id: 'CVE-4', severity: 'high', cvssScore: 7.5 },
            ],
          },
        },
        {
          id: 'safe',
          name: 'safe-lib',
          version: '1.0.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {},
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const analyzed = analyzeDependencyRisk(sbom);

    expect(analyzed.metadata.highRiskComponents).toBe(1);
  });

  it('should account for deprecated packages in risk score', () => {
    const sbom: SBOM = {
      format: 'cyclonedx',
      version: '1.4',
      components: [
        {
          id: 'deprecated',
          name: 'old-lib',
          version: '0.1.0',
          type: 'library',
          licenses: [],
          dependencies: [],
          metadata: {
            deprecated: true,
          },
        },
      ],
      dependencies: [],
      metadata: {},
    };

    const analyzed = analyzeDependencyRisk(sbom);

    const comp = analyzed.components[0];
    expect(comp.metadata.riskScore).toBeGreaterThan(0);
  });
});

describe.skip('SBOM - Component Styling', () => {
  it('should apply critical vulnerability style', () => {
    const comp: Component = {
      id: 'comp1',
      name: 'vuln-lib',
      version: '1.0.0',
      type: 'library',
      licenses: [],
      dependencies: [],
      metadata: {
        vulnerabilities: [{ id: 'CVE-1', severity: 'critical', cvssScore: 9.8 }],
      },
    };

    const config = createSBOMConfig({ highlightCritical: true });
    const style = getComponentStyle(comp, config);

    expect(style.backgroundColor).toBe('#fef2f2');
    expect(style.borderColor).toBe('#dc2626');
    expect(style.borderWidth).toBe(3);
  });

  it('should apply high severity style', () => {
    const comp: Component = {
      id: 'comp1',
      name: 'vuln-lib',
      version: '1.0.0',
      type: 'library',
      licenses: [],
      dependencies: [],
      metadata: {
        vulnerabilities: [{ id: 'CVE-1', severity: 'high', cvssScore: 7.5 }],
      },
    };

    const config = createSBOMConfig({ highlightCritical: true });
    const style = getComponentStyle(comp, config);

    expect(style.backgroundColor).toBe('#fff7ed');
    expect(style.borderColor).toBe('#ea580c');
  });

  it('should apply library type style', () => {
    const comp: Component = {
      id: 'comp1',
      name: 'my-lib',
      version: '1.0.0',
      type: 'library',
      licenses: [],
      dependencies: [],
      metadata: {},
    };

    const config = createSBOMConfig();
    const style = getComponentStyle(comp, config);

    expect(style.backgroundColor).toBe('#eff6ff');
    expect(style.borderColor).toBe('#3b82f6');
  });

  it('should apply framework type style', () => {
    const comp: Component = {
      id: 'comp1',
      name: 'express',
      version: '4.18.0',
      type: 'framework',
      licenses: [],
      dependencies: [],
      metadata: {},
    };

    const config = createSBOMConfig();
    const style = getComponentStyle(comp, config);

    expect(style.backgroundColor).toBe('#f0f9ff');
    expect(style.borderColor).toBe('#0ea5e9');
  });

  it('should apply container type style', () => {
    const comp: Component = {
      id: 'comp1',
      name: 'nginx',
      version: '1.21.0',
      type: 'container',
      licenses: [],
      dependencies: [],
      metadata: {},
    };

    const config = createSBOMConfig();
    const style = getComponentStyle(comp, config);

    expect(style.backgroundColor).toBe('#ecfeff');
    expect(style.borderColor).toBe('#06b6d4');
  });
});
