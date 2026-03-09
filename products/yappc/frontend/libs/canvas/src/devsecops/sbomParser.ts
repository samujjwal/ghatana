/**
 * Software Bill of Materials (SBOM) Integration & Dependency Tracking
 * 
 * Parses CycloneDX and SPDX SBOM formats to visualize software dependencies,
 * detect vulnerabilities, check license compliance, and assess security risks.
 * 
 * @module devsecops/sbomParser
 */

import type { CanvasDocument, CanvasNode, CanvasEdge } from '../types/canvas-document';

/**
 * Supported SBOM formats
 */
export type SBOMFormat = 'cyclonedx' | 'spdx' | 'syft' | 'trivy';

/**
 * Package/Component types
 */
export type PackageType =
  | 'library'
  | 'framework'
  | 'application'
  | 'operating-system'
  | 'device'
  | 'firmware'
  | 'file'
  | 'container'
  | 'other';

/**
 * Vulnerability severity levels (CVSS)
 */
export type Severity = 'critical' | 'high' | 'medium' | 'low' | 'info' | 'none';

/**
 * License categories
 */
export type LicenseCategory =
  | 'permissive'
  | 'copyleft-strong'
  | 'copyleft-weak'
  | 'proprietary'
  | 'public-domain'
  | 'unknown';

/**
 * Dependency scope
 */
export type DependencyScope = 'required' | 'optional' | 'dev' | 'test' | 'runtime';

/**
 * Component/Package in SBOM
 */
export interface Component {
  id: string;
  name: string;
  version: string;
  type: PackageType;
  purl?: string; // Package URL
  cpe?: string; // Common Platform Enumeration
  description?: string;
  supplier?: string;
  author?: string;
  licenses: License[];
  hashes?: Hash[];
  dependencies: string[]; // Component IDs
  scope?: DependencyScope;
  metadata: {
    vulnerabilities?: Vulnerability[];
    riskScore?: number;
    deprecated?: boolean;
    [key: string]: unknown;
  };
}

/**
 * License information
 */
export interface License {
  id?: string; // SPDX License ID
  name?: string;
  url?: string;
  category?: LicenseCategory;
  text?: string;
}

/**
 * Hash/Checksum
 */
export interface Hash {
  algorithm: string; // sha256, sha1, md5, etc.
  value: string;
}

/**
 * Vulnerability information
 */
export interface Vulnerability {
  id: string; // CVE-2024-1234
  source?: string; // NVD, GitHub, OSV
  description?: string;
  severity: Severity;
  cvssScore?: number; // 0-10
  cvssVector?: string;
  published?: Date;
  updated?: Date;
  references?: string[];
  cwe?: string[]; // Common Weakness Enumeration
  fixedIn?: string; // Version that fixes the vulnerability
  exploitable?: boolean;
}

/**
 * SBOM document
 */
export interface SBOM {
  format: SBOMFormat;
  version: string;
  serialNumber?: string;
  name?: string;
  description?: string;
  timestamp?: Date;
  components: Component[];
  dependencies: DependencyRelation[];
  metadata: {
    tools?: string[];
    authors?: string[];
    totalVulnerabilities?: number;
    criticalVulnerabilities?: number;
    highRiskComponents?: number;
    licenseIssues?: number;
    [key: string]: unknown;
  };
}

/**
 * Dependency relationship
 */
export interface DependencyRelation {
  from: string; // Component ID
  to: string; // Component ID
  scope?: DependencyScope;
  relationship?: 'dependsOn' | 'contains' | 'optionalDependency';
}

/**
 * SBOM visualization configuration
 */
export interface SBOMConfig {
  layout?: 'tree' | 'circular' | 'force' | 'layered';
  showVulnerabilities?: boolean;
  showLicenses?: boolean;
  showDeprecated?: boolean;
  highlightCritical?: boolean;
  groupBy?: 'type' | 'license' | 'severity' | 'none';
  filterSeverity?: Severity[];
  componentSpacing?: { x: number; y: number };
  maxDepth?: number;
}

/**
 * License compliance result
 */
export interface ComplianceResult {
  compliant: boolean;
  issues: ComplianceIssue[];
  summary: {
    totalComponents: number;
    permissive: number;
    copyleft: number;
    proprietary: number;
    unknown: number;
  };
}

/**
 * License compliance issue
 */
export interface ComplianceIssue {
  component: string;
  license: string;
  category: LicenseCategory;
  severity: Severity;
  reason: string;
  recommendation?: string;
}

/**
 * Create default SBOM configuration
 */
export function createSBOMConfig(overrides?: Partial<SBOMConfig>): SBOMConfig {
  return {
    layout: 'tree',
    showVulnerabilities: true,
    showLicenses: true,
    showDeprecated: true,
    highlightCritical: true,
    groupBy: 'type',
    filterSeverity: undefined,
    componentSpacing: { x: 250, y: 150 },
    maxDepth: 10,
    ...overrides,
  };
}

/**
 * Parse CycloneDX SBOM
 * 
 * @param json - CycloneDX JSON string or object
 * @returns Parsed SBOM
 */
export function parseCycloneDX(json: string | any): SBOM {
  const data = typeof json === 'string' ? JSON.parse(json) : json;

  const sbom: SBOM = {
    format: 'cyclonedx',
    version: data.specVersion || '1.4',
    serialNumber: data.serialNumber,
    name: data.metadata?.component?.name,
    description: data.metadata?.component?.description,
    timestamp: data.metadata?.timestamp ? new Date(data.metadata.timestamp) : undefined,
    components: [],
    dependencies: [],
    metadata: {
      tools: data.metadata?.tools?.map((t: unknown) => t.name || t.vendor),
    },
  };

  // Parse components
  if (data.components) {
    data.components.forEach((comp: unknown) => {
      const component: Component = {
        id: comp['bom-ref'] || `${comp.name}@${comp.version}`,
        name: comp.name,
        version: comp.version || 'unknown',
        type: mapCycloneDXType(comp.type),
        purl: comp.purl,
        cpe: comp.cpe,
        description: comp.description,
        supplier: comp.supplier?.name,
        author: comp.author,
        licenses: parseCycloneDXLicenses(comp.licenses),
        hashes: comp.hashes?.map((h: unknown) => ({
          algorithm: h.alg,
          value: h.content,
        })),
        dependencies: [],
        scope: mapCycloneDXScope(comp.scope),
        metadata: {},
      };

      // Parse vulnerabilities if present
      if (comp.vulnerabilities) {
        component.metadata.vulnerabilities = comp.vulnerabilities.map((v: unknown) => ({
          id: v.id,
          source: v.source?.name,
          description: v.description,
          severity: mapCVSSSeverity(v.ratings?.[0]?.score),
          cvssScore: v.ratings?.[0]?.score,
          cvssVector: v.ratings?.[0]?.vector,
          published: v.published ? new Date(v.published) : undefined,
          updated: v.updated ? new Date(v.updated) : undefined,
          references: v.references?.map((r: unknown) => r.url),
          cwe: v.cwes,
        }));
      }

      sbom.components.push(component);
    });
  }

  // Parse dependencies
  if (data.dependencies) {
    data.dependencies.forEach((dep: unknown) => {
      const from = dep.ref;
      if (dep.dependsOn) {
        dep.dependsOn.forEach((to: string) => {
          sbom.dependencies.push({
            from,
            to,
            relationship: 'dependsOn',
          });

          // Add to component's dependencies array
          const component = sbom.components.find(c => c.id === from);
          if (component) {
            component.dependencies.push(to);
          }
        });
      }
    });
  }

  // Calculate metadata statistics
  calculateSBOMMetadata(sbom);

  return sbom;
}

/**
 * Parse SPDX SBOM
 * 
 * @param json - SPDX JSON string or object
 * @returns Parsed SBOM
 */
export function parseSPDX(json: string | any): SBOM {
  const data = typeof json === 'string' ? JSON.parse(json) : json;

  const sbom: SBOM = {
    format: 'spdx',
    version: data.spdxVersion || 'SPDX-2.3',
    serialNumber: data.SPDXID,
    name: data.name,
    description: data.documentDescribes?.[0],
    timestamp: data.creationInfo?.created ? new Date(data.creationInfo.created) : undefined,
    components: [],
    dependencies: [],
    metadata: {
      tools: data.creationInfo?.creators?.filter((c: string) => c.startsWith('Tool:')),
      authors: data.creationInfo?.creators?.filter((c: string) => c.startsWith('Person:')),
    },
  };

  // Parse packages (components)
  if (data.packages) {
    data.packages.forEach((pkg: unknown) => {
      const component: Component = {
        id: pkg.SPDXID,
        name: pkg.name,
        version: pkg.versionInfo || 'unknown',
        type: mapSPDXType(pkg.primaryPackagePurpose),
        description: pkg.description,
        supplier: pkg.supplier,
        licenses: parseSPDXLicenses(pkg.licenseConcluded, pkg.licenseDeclared),
        hashes: pkg.checksums?.map((c: unknown) => ({
          algorithm: c.algorithm.toLowerCase(),
          value: c.checksumValue,
        })),
        dependencies: [],
        metadata: {
          deprecated: pkg.comment?.includes('deprecated'),
        },
      };

      // Extract external refs (like CVEs)
      if (pkg.externalRefs) {
        const vulnerabilities: Vulnerability[] = [];
        pkg.externalRefs.forEach((ref: unknown) => {
          if (ref.referenceCategory === 'SECURITY' && ref.referenceType === 'cpe23Type') {
            component.cpe = ref.referenceLocator;
          }
          if (ref.comment && ref.comment.includes('CVE-')) {
            const cveMatch = ref.comment.match(/CVE-\d{4}-\d+/g);
            if (cveMatch) {
              cveMatch.forEach((cve: string) => {
                vulnerabilities.push({
                  id: cve,
                  severity: 'medium', // Default, should be enriched
                  source: 'SPDX',
                });
              });
            }
          }
        });
        if (vulnerabilities.length > 0) {
          component.metadata.vulnerabilities = vulnerabilities;
        }
      }

      sbom.components.push(component);
    });
  }

  // Parse relationships (dependencies)
  if (data.relationships) {
    data.relationships.forEach((rel: unknown) => {
      if (rel.relationshipType === 'DEPENDS_ON' || rel.relationshipType === 'DEPENDENCY_OF') {
        const from = rel.spdxElementId;
        const to = rel.relatedSpdxElement;

        sbom.dependencies.push({
          from,
          to,
          relationship: 'dependsOn',
        });

        const component = sbom.components.find(c => c.id === from);
        if (component) {
          component.dependencies.push(to);
        }
      }
    });
  }

  calculateSBOMMetadata(sbom);

  return sbom;
}

/**
 * Convert SBOM to canvas document
 * 
 * @param sbom - Parsed SBOM
 * @param config - Visualization configuration
 * @returns Canvas document
 */
export function sbomToCanvas(
  sbom: SBOM,
  config: SBOMConfig = createSBOMConfig()
): CanvasDocument {
  const elements: Record<string, CanvasNode | CanvasEdge> = {};
  const elementOrder: string[] = [];

  const NODE_WIDTH = 220;
  const NODE_HEIGHT = 100;
  const HORIZONTAL_GAP = config.componentSpacing?.x || 250;
  const VERTICAL_GAP = config.componentSpacing?.y || 150;

  // Filter components based on severity
  let filteredComponents = sbom.components;
  if (config.filterSeverity && config.filterSeverity.length > 0) {
    filteredComponents = sbom.components.filter(comp => {
      const vulnerabilities = comp.metadata.vulnerabilities || [];
      return vulnerabilities.some(v => config.filterSeverity!.includes(v.severity));
    });
  }

  // Build dependency tree
  const roots = findRootComponents(filteredComponents, sbom.dependencies);
  const levels = calculateComponentLevels(filteredComponents, sbom.dependencies, config.maxDepth);

  // Layout components by level
  const levelGroups = new Map<number, Component[]>();
  filteredComponents.forEach(comp => {
    const level = levels.get(comp.id) || 0;
    const group = levelGroups.get(level) || [];
    group.push(comp);
    levelGroups.set(level, group);
  });

  // Position components
  levelGroups.forEach((components, level) => {
    components.forEach((comp, index) => {
      const x = 50 + (level * HORIZONTAL_GAP);
      const y = 50 + (index * VERTICAL_GAP);

      const hasCriticalVuln = comp.metadata.vulnerabilities?.some(
        v => v.severity === 'critical'
      );
      const hasHighVuln = comp.metadata.vulnerabilities?.some(v => v.severity === 'high');

      const node: CanvasNode = {
        id: comp.id,
        type: 'node',
        nodeType: 'sbom-component',
        transform: {
          position: { x, y },
          scale: 1,
          rotation: 0,
        },
        bounds: {
          x,
          y,
          width: NODE_WIDTH,
          height: NODE_HEIGHT,
        },
        visible: true,
        locked: false,
        selected: false,
        zIndex: hasCriticalVuln ? 3 : hasHighVuln ? 2 : 1,
        metadata: {
          componentName: comp.name,
          version: comp.version,
          type: comp.type,
          licenses: comp.licenses.map(l => l.id || l.name).filter(Boolean),
          vulnerabilities: comp.metadata.vulnerabilities,
          riskScore: comp.metadata.riskScore,
          deprecated: comp.metadata.deprecated,
        },
        version: '1.0.0',
        createdAt: new Date(),
        updatedAt: new Date(),
        data: {
          label: `${comp.name}@${comp.version}`,
          type: comp.type,
          vulnCount: comp.metadata.vulnerabilities?.length || 0,
          criticalVulns: comp.metadata.vulnerabilities?.filter(v => v.severity === 'critical')
            .length,
          highVulns: comp.metadata.vulnerabilities?.filter(v => v.severity === 'high').length,
          licenses: comp.licenses.map(l => l.id || l.name).join(', '),
          riskScore: comp.metadata.riskScore,
        },
        inputs: [],
        outputs: comp.dependencies,
        style: getComponentStyle(comp, config),
      };

      elements[comp.id] = node;
      elementOrder.push(comp.id);
    });
  });

  // Create edges for dependencies
  sbom.dependencies.forEach(dep => {
    // Only create edges for visible components
    if (elements[dep.from] && elements[dep.to]) {
      const edgeId = `edge-${dep.from}-${dep.to}`;
      const edge: CanvasEdge = {
        id: edgeId,
        type: 'edge',
        sourceId: dep.from,
        targetId: dep.to,
        path: [],
        transform: {
          position: { x: 0, y: 0 },
          scale: 1,
          rotation: 0,
        },
        bounds: { x: 0, y: 0, width: 0, height: 0 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {
          scope: dep.scope,
          relationship: dep.relationship,
        },
        version: '1.0.0',
        createdAt: new Date(),
        updatedAt: new Date(),
        style: {
          stroke: dep.scope === 'dev' ? '#94a3b8' : '#475569',
          strokeWidth: 2,
          strokeDasharray: dep.scope === 'optional' ? '5,5' : undefined,
        },
      };
      elements[edgeId] = edge;
      elementOrder.push(edgeId);
    }
  });

  return {
    id: `sbom-${sbom.name || 'document'}`,
    version: '1.0.0',
    title: `SBOM: ${sbom.name || 'Software Bill of Materials'}`,
    description: `${sbom.format} SBOM with ${sbom.components.length} components`,
    viewport: {
      center: { x: 400, y: 300 },
      zoom: 1,
    },
    elements,
    elementOrder,
    metadata: {
      format: sbom.format,
      componentCount: sbom.components.length,
      totalVulnerabilities: sbom.metadata.totalVulnerabilities,
      criticalVulnerabilities: sbom.metadata.criticalVulnerabilities,
      highRiskComponents: sbom.metadata.highRiskComponents,
      licenseIssues: sbom.metadata.licenseIssues,
    },
    capabilities: {
      canEdit: false,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: false,
      canRedo: false,
      canExport: true,
      canImport: false,
      canCollaborate: false,
      canPersist: true,
      allowedElementTypes: ['node', 'edge'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

/**
 * Detect vulnerabilities by enriching SBOM with CVE data
 * 
 * @param sbom - SBOM to enrich
 * @param cveDatabase - CVE vulnerability database
 * @returns SBOM with vulnerability information
 */
export function detectVulnerabilities(
  sbom: SBOM,
  cveDatabase: Record<string, Vulnerability[]>
): SBOM {
  const enriched = { ...sbom };

  enriched.components = sbom.components.map(comp => {
    // Look up vulnerabilities by component name, purl, or cpe
    const key = comp.purl || comp.cpe || `${comp.name}@${comp.version}`;
    const vulns = cveDatabase[key] || cveDatabase[comp.name] || [];

    return {
      ...comp,
      metadata: {
        ...comp.metadata,
        vulnerabilities: [...(comp.metadata.vulnerabilities || []), ...vulns],
        riskScore: calculateRiskScore(comp, vulns),
      },
    };
  });

  calculateSBOMMetadata(enriched);

  return enriched;
}

/**
 * Check license compliance
 * 
 * @param sbom - SBOM to check
 * @param allowedLicenses - List of allowed license IDs
 * @param prohibitedLicenses - List of prohibited license IDs
 * @returns Compliance result
 */
export function checkLicenseCompliance(
  sbom: SBOM,
  allowedLicenses: string[] = [],
  prohibitedLicenses: string[] = []
): ComplianceResult {
  const issues: ComplianceIssue[] = [];
  const summary = {
    totalComponents: sbom.components.length,
    permissive: 0,
    copyleft: 0,
    proprietary: 0,
    unknown: 0,
  };

  sbom.components.forEach(comp => {
    comp.licenses.forEach(license => {
      const licenseId = license.id || license.name || 'unknown';
      const category = license.category || categorizeLicense(licenseId);

      // Update summary
      switch (category) {
        case 'permissive':
        case 'public-domain':
          summary.permissive++;
          break;
        case 'copyleft-strong':
        case 'copyleft-weak':
          summary.copyleft++;
          break;
        case 'proprietary':
          summary.proprietary++;
          break;
        default:
          summary.unknown++;
      }

      // Check prohibited licenses
      if (prohibitedLicenses.includes(licenseId)) {
        issues.push({
          component: `${comp.name}@${comp.version}`,
          license: licenseId,
          category,
          severity: 'high',
          reason: `Prohibited license: ${licenseId}`,
          recommendation: 'Find alternative component or obtain license exception',
        });
      }

      // Check if license is not in allowed list (if specified)
      if (allowedLicenses.length > 0 && !allowedLicenses.includes(licenseId)) {
        issues.push({
          component: `${comp.name}@${comp.version}`,
          license: licenseId,
          category,
          severity: 'medium',
          reason: `License not in allowed list: ${licenseId}`,
          recommendation: 'Review license and add to allowed list if acceptable',
        });
      }

      // Warn about unknown licenses
      if (category === 'unknown' && licenseId !== 'unknown') {
        issues.push({
          component: `${comp.name}@${comp.version}`,
          license: licenseId,
          category,
          severity: 'low',
          reason: `Unknown or unrecognized license: ${licenseId}`,
          recommendation: 'Manually review license terms',
        });
      }

      // Warn about strong copyleft in proprietary products
      if (category === 'copyleft-strong') {
        issues.push({
          component: `${comp.name}@${comp.version}`,
          license: licenseId,
          category,
          severity: 'medium',
          reason: `Strong copyleft license may require source disclosure: ${licenseId}`,
          recommendation: 'Review obligations for distribution',
        });
      }
    });
  });

  return {
    compliant: issues.filter(i => i.severity === 'high').length === 0,
    issues,
    summary,
  };
}

/**
 * Analyze dependency risk
 * 
 * @param sbom - SBOM to analyze
 * @returns SBOM with risk scores
 */
export function analyzeDependencyRisk(sbom: SBOM): SBOM {
  const analyzed = { ...sbom };

  analyzed.components = sbom.components.map(comp => {
    const vulns = comp.metadata.vulnerabilities || [];
    const riskScore = calculateRiskScore(comp, vulns);

    return {
      ...comp,
      metadata: {
        ...comp.metadata,
        riskScore,
      },
    };
  });

  // Calculate high-risk components
  analyzed.metadata.highRiskComponents = analyzed.components.filter(
    c => (c.metadata.riskScore || 0) >= 70
  ).length;

  return analyzed;
}

// Helper functions

/**
 *
 */
function mapCycloneDXType(type: string): PackageType {
  const typeMap: Record<string, PackageType> = {
    library: 'library',
    framework: 'framework',
    application: 'application',
    'operating-system': 'operating-system',
    device: 'device',
    firmware: 'firmware',
    file: 'file',
    container: 'container',
  };
  return typeMap[type] || 'other';
}

/**
 *
 */
function mapCycloneDXScope(scope?: string): DependencyScope | undefined {
  const scopeMap: Record<string, DependencyScope> = {
    required: 'required',
    optional: 'optional',
    excluded: 'dev',
  };
  return scope ? scopeMap[scope] || 'required' : undefined;
}

/**
 *
 */
function mapSPDXType(purpose?: string): PackageType {
  if (!purpose) return 'library';

  const typeMap: Record<string, PackageType> = {
    LIBRARY: 'library',
    FRAMEWORK: 'framework',
    APPLICATION: 'application',
    OPERATING_SYSTEM: 'operating-system',
    DEVICE: 'device',
    FIRMWARE: 'firmware',
    CONTAINER: 'container',
    FILE: 'file',
  };
  return typeMap[purpose] || 'other';
}

/**
 *
 */
function parseCycloneDXLicenses(licenses?: unknown[]): License[] {
  if (!licenses) return [];

  return licenses
    .map(l => {
      if (l.license) {
        return {
          id: l.license.id,
          name: l.license.name,
          url: l.license.url,
          category: categorizeLicense(l.license.id || l.license.name),
        };
      }
      if (l.expression) {
        return {
          name: l.expression,
          category: 'unknown',
        };
      }
      return null;
    })
    .filter(Boolean) as License[];
}

/**
 *
 */
function parseSPDXLicenses(concluded?: string, declared?: string): License[] {
  const licenses: License[] = [];
  const licenseStr = concluded || declared || 'NOASSERTION';

  if (licenseStr === 'NOASSERTION' || licenseStr === 'NONE') {
    return [{ name: 'Unknown', category: 'unknown' }];
  }

  // Split compound licenses (AND, OR)
  const licenseIds = licenseStr.split(/\s+(?:AND|OR)\s+/);

  licenseIds.forEach(id => {
    const cleanId = id.trim().replace(/[()]/g, '');
    licenses.push({
      id: cleanId,
      name: cleanId,
      category: categorizeLicense(cleanId),
    });
  });

  return licenses;
}

/**
 *
 */
function categorizeLicense(licenseId: string): LicenseCategory {
  const permissive = ['MIT', 'Apache-2.0', 'BSD-2-Clause', 'BSD-3-Clause', 'ISC', '0BSD'];
  const strongCopyleft = ['GPL-2.0', 'GPL-3.0', 'AGPL-3.0'];
  const weakCopyleft = ['LGPL-2.1', 'LGPL-3.0', 'MPL-2.0', 'EPL-1.0'];
  const publicDomain = ['Unlicense', 'CC0-1.0'];

  if (permissive.includes(licenseId)) return 'permissive';
  if (strongCopyleft.includes(licenseId)) return 'copyleft-strong';
  if (weakCopyleft.includes(licenseId)) return 'copyleft-weak';
  if (publicDomain.includes(licenseId)) return 'public-domain';
  if (licenseId.includes('Proprietary')) return 'proprietary';

  return 'unknown';
}

/**
 *
 */
function mapCVSSSeverity(score?: number): Severity {
  if (!score) return 'none';
  if (score >= 9.0) return 'critical';
  if (score >= 7.0) return 'high';
  if (score >= 4.0) return 'medium';
  if (score >= 0.1) return 'low';
  return 'info';
}

/**
 *
 */
function calculateRiskScore(comp: Component, vulnerabilities: Vulnerability[]): number {
  let score = 0;

  // Vulnerability score (0-70 points)
  vulnerabilities.forEach(v => {
    switch (v.severity) {
      case 'critical':
        score += 20;
        break;
      case 'high':
        score += 10;
        break;
      case 'medium':
        score += 5;
        break;
      case 'low':
        score += 2;
        break;
    }
  });

  // Deprecated component (10 points)
  if (comp.metadata.deprecated) {
    score += 10;
  }

  // Unknown license (5 points)
  if (comp.licenses.some(l => l.category === 'unknown')) {
    score += 5;
  }

  // Copyleft license (5 points for strong, 3 for weak)
  if (comp.licenses.some(l => l.category === 'copyleft-strong')) {
    score += 5;
  } else if (comp.licenses.some(l => l.category === 'copyleft-weak')) {
    score += 3;
  }

  return Math.min(score, 100);
}

/**
 *
 */
function findRootComponents(
  components: Component[],
  dependencies: DependencyRelation[]
): Component[] {
  const dependentIds = new Set(dependencies.map(d => d.to));
  return components.filter(c => !dependentIds.has(c.id));
}

/**
 *
 */
function calculateComponentLevels(
  components: Component[],
  dependencies: DependencyRelation[],
  maxDepth: number = 10
): Map<string, number> {
  const levels = new Map<string, number>();

  // Build adjacency list
  const adjList = new Map<string, string[]>();
  dependencies.forEach(dep => {
    const deps = adjList.get(dep.from) || [];
    deps.push(dep.to);
    adjList.set(dep.from, deps);
  });

  // BFS to calculate levels
  const roots = findRootComponents(components, dependencies);
  const queue: Array<{ id: string; level: number }> = roots.map(r => ({
    id: r.id,
    level: 0,
  }));

  while (queue.length > 0) {
    const { id, level } = queue.shift()!;

    if (level >= maxDepth) continue;
    if (levels.has(id)) continue;

    levels.set(id, level);

    const deps = adjList.get(id) || [];
    deps.forEach(depId => {
      queue.push({ id: depId, level: level + 1 });
    });
  }

  // Set level 0 for any orphaned components
  components.forEach(c => {
    if (!levels.has(c.id)) {
      levels.set(c.id, 0);
    }
  });

  return levels;
}

/**
 *
 */
function calculateSBOMMetadata(sbom: SBOM): void {
  let totalVulns = 0;
  let criticalVulns = 0;

  sbom.components.forEach(comp => {
    const vulns = comp.metadata.vulnerabilities || [];
    totalVulns += vulns.length;
    criticalVulns += vulns.filter(v => v.severity === 'critical').length;
  });

  sbom.metadata.totalVulnerabilities = totalVulns;
  sbom.metadata.criticalVulnerabilities = criticalVulns;
}

/**
 *
 */
export function getComponentStyle(
  comp: Component,
  config: SBOMConfig
): Record<string, unknown> {
  const baseStyle = {
    borderRadius: 8,
    padding: 12,
    fontSize: 12,
    fontWeight: 500,
  };

  // Vulnerability-based styling (highest priority)
  if (config.highlightCritical) {
    const vulns = comp.metadata.vulnerabilities || [];
    const hasCritical = vulns.some(v => v.severity === 'critical');
    const hasHigh = vulns.some(v => v.severity === 'high');

    if (hasCritical) {
      return {
        ...baseStyle,
        backgroundColor: '#fef2f2',
        borderColor: '#dc2626',
        borderWidth: 3,
        color: '#7f1d1d',
      };
    }
    if (hasHigh) {
      return {
        ...baseStyle,
        backgroundColor: '#fff7ed',
        borderColor: '#ea580c',
        borderWidth: 2,
        color: '#7c2d12',
      };
    }
  }

  // Type-based styling
  const typeColors: Record<PackageType, { bg: string; border: string; text: string }> = {
    library: { bg: '#eff6ff', border: '#3b82f6', text: '#1e3a8a' },
    framework: { bg: '#f0f9ff', border: '#0ea5e9', text: '#0c4a6e' },
    application: { bg: '#f0fdf4', border: '#22c55e', text: '#14532d' },
    'operating-system': { bg: '#fef3c7', border: '#f59e0b', text: '#78350f' },
    container: { bg: '#ecfeff', border: '#06b6d4', text: '#164e63' },
    device: { bg: '#fce7f3', border: '#ec4899', text: '#831843' },
    firmware: { bg: '#f5f3ff', border: '#a78bfa', text: '#4c1d95' },
    file: { bg: '#f8fafc', border: '#94a3b8', text: '#334155' },
    other: { bg: '#f9fafb', border: '#9ca3af', text: '#374151' },
  };

  const colors = typeColors[comp.type];

  return {
    ...baseStyle,
    backgroundColor: colors.bg,
    borderColor: colors.border,
    borderWidth: 2,
    color: colors.text,
  };
}
