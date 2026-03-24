/**
 * Compliance Mapping System
 * 
 * Provides compliance control tagging and tracking for canvas nodes:
 * - Attach compliance controls to nodes with status tracking
 * - Generate coverage reports (satisfied/unsatisfied controls)
 * - Create audit bundles with diagrams and logs
 * - Map controls to frameworks (SOC2, ISO27001, GDPR, HIPAA)
 * 
 * Features:
 * - Control lifecycle management (planned → implemented → tested → validated)
 * - Evidence attachment and tracking
 * - Gap analysis and remediation tracking
 * - Export to multiple formats (JSON, CSV, Markdown)
 * 
 * @module devsecops/complianceMapping
 */

/**
 * Compliance frameworks
 */
export type ComplianceFramework = 
  | 'SOC2'
  | 'ISO27001'
  | 'GDPR'
  | 'HIPAA'
  | 'PCI-DSS'
  | 'NIST'
  | 'custom';

/**
 * Control status
 */
export type ControlStatus = 
  | 'planned'
  | 'implemented'
  | 'tested'
  | 'validated'
  | 'failed'
  | 'not_applicable';

/**
 * Control severity/priority
 */
export type ControlSeverity = 'low' | 'medium' | 'high' | 'critical';

/**
 * Compliance control definition
 */
export interface ComplianceControl {
  /** Control ID */
  id: string;
  /** Framework it belongs to */
  framework: ComplianceFramework;
  /** Control identifier in framework (e.g., "CC6.1" for SOC2) */
  controlId: string;
  /** Control title */
  title: string;
  /** Description */
  description: string;
  /** Current status */
  status: ControlStatus;
  /** Severity/priority */
  severity: ControlSeverity;
  /** Owner/responsible party */
  owner?: string;
  /** Implementation date */
  implementedAt?: number;
  /** Validation date */
  validatedAt?: number;
  /** Attached evidence */
  evidence: Evidence[];
  /** Node IDs this control applies to */
  nodeIds: string[];
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Evidence attachment
 */
export interface Evidence {
  /** Evidence ID */
  id: string;
  /** Evidence type */
  type: 'document' | 'screenshot' | 'log' | 'test_result' | 'audit_report' | 'other';
  /** Title */
  title: string;
  /** Description */
  description?: string;
  /** URL or file path */
  url?: string;
  /** Timestamp */
  timestamp: number;
  /** Uploaded by */
  uploadedBy?: string;
}

/**
 * Node control mapping
 */
export interface NodeControlMapping {
  /** Node ID */
  nodeId: string;
  /** Node label/name */
  nodeLabel?: string;
  /** Node type */
  nodeType?: string;
  /** Applied control IDs */
  controlIds: string[];
  /** Coverage percentage */
  coverage: number;
  /** Has failing controls */
  hasFailures: boolean;
}

/**
 * Coverage report
 */
export interface CoverageReport {
  /** Report timestamp */
  timestamp: number;
  /** Framework filter applied */
  framework?: ComplianceFramework;
  /** Total controls */
  totalControls: number;
  /** Controls by status */
  byStatus: Record<ControlStatus, number>;
  /** Controls by severity */
  bySeverity: Record<ControlSeverity, number>;
  /** Overall coverage percentage */
  overallCoverage: number;
  /** Satisfied controls */
  satisfiedControls: ComplianceControl[];
  /** Unsatisfied controls */
  unsatisfiedControls: ComplianceControl[];
  /** Gaps (controls without implementation) */
  gaps: ComplianceControl[];
  /** Node coverage */
  nodeCoverage: NodeControlMapping[];
}

/**
 * Audit bundle
 */
export interface AuditBundle {
  /** Bundle ID */
  id: string;
  /** Creation timestamp */
  createdAt: number;
  /** Created by */
  createdBy?: string;
  /** Framework scope */
  framework?: ComplianceFramework;
  /** Controls included */
  controls: ComplianceControl[];
  /** Coverage report */
  coverageReport: CoverageReport;
  /** Diagram data (simplified) */
  diagram?: {
    nodes: Array<{ id: string; label: string; type: string }>;
    edges: Array<{ source: string; target: string }>;
  };
  /** Audit logs (references) */
  auditLogReferences?: string[];
  /** Additional documents */
  documents?: Evidence[];
}

/**
 * ComplianceStore configuration
 */
export interface ComplianceStoreConfig {
  /** Enable automatic coverage calculation */
  autoCalculateCoverage?: boolean;
  /** Default framework */
  defaultFramework?: ComplianceFramework;
}

/**
 * ComplianceStore
 * 
 * Manages compliance controls and mappings
 */
export class ComplianceStore {
  private config: Required<ComplianceStoreConfig>;
  private controls = new Map<string, ComplianceControl>();
  private nodeMappings = new Map<string, Set<string>>(); // nodeId -> Set<controlId>
  private evidenceCounter = 0;
  private bundleCounter = 0;

  /**
   *
   */
  constructor(config: ComplianceStoreConfig = {}) {
    this.config = {
      autoCalculateCoverage: config.autoCalculateCoverage ?? true,
      defaultFramework: config.defaultFramework ?? 'custom',
    };
  }

  /**
   * Add compliance control
   */
  addControl(control: Omit<ComplianceControl, 'evidence' | 'nodeIds'>): ComplianceControl {
    const fullControl: ComplianceControl = {
      ...control,
      evidence: [],
      nodeIds: [],
    };

    this.controls.set(control.id, fullControl);
    return fullControl;
  }

  /**
   * Update control
   */
  updateControl(controlId: string, updates: Partial<ComplianceControl>): boolean {
    const control = this.controls.get(controlId);
    
    if (!control) {
      return false;
    }

    Object.assign(control, updates);
    return true;
  }

  /**
   * Get control
   */
  getControl(controlId: string): ComplianceControl | undefined {
    return this.controls.get(controlId);
  }

  /**
   * Delete control
   */
  deleteControl(controlId: string): boolean {
    const control = this.controls.get(controlId);
    
    if (!control) {
      return false;
    }

    // Remove from node mappings
    for (const nodeId of control.nodeIds) {
      this.nodeMappings.get(nodeId)?.delete(controlId);
    }

    return this.controls.delete(controlId);
  }

  /**
   * List controls with filtering
   */
  listControls(filter?: {
    framework?: ComplianceFramework;
    status?: ControlStatus;
    severity?: ControlSeverity;
    owner?: string;
    nodeId?: string;
  }): ComplianceControl[] {
    let controls = Array.from(this.controls.values());

    if (filter?.framework) {
      controls = controls.filter(c => c.framework === filter.framework);
    }

    if (filter?.status) {
      controls = controls.filter(c => c.status === filter.status);
    }

    if (filter?.severity) {
      controls = controls.filter(c => c.severity === filter.severity);
    }

    if (filter?.owner) {
      controls = controls.filter(c => c.owner === filter.owner);
    }

    if (filter?.nodeId) {
      controls = controls.filter(c => c.nodeIds.includes(filter.nodeId!));
    }

    return controls;
  }

  /**
   * Attach control to node
   */
  attachControlToNode(controlId: string, nodeId: string): boolean {
    const control = this.controls.get(controlId);
    
    if (!control) {
      return false;
    }

    if (!control.nodeIds.includes(nodeId)) {
      control.nodeIds.push(nodeId);
    }

    if (!this.nodeMappings.has(nodeId)) {
      this.nodeMappings.set(nodeId, new Set());
    }

    this.nodeMappings.get(nodeId)!.add(controlId);
    return true;
  }

  /**
   * Detach control from node
   */
  detachControlFromNode(controlId: string, nodeId: string): boolean {
    const control = this.controls.get(controlId);
    
    if (!control) {
      return false;
    }

    control.nodeIds = control.nodeIds.filter(id => id !== nodeId);
    this.nodeMappings.get(nodeId)?.delete(controlId);
    return true;
  }

  /**
   * Get controls for node
   */
  getNodeControls(nodeId: string): ComplianceControl[] {
    const controlIds = this.nodeMappings.get(nodeId);
    
    if (!controlIds) {
      return [];
    }

    return Array.from(controlIds)
      .map(id => this.controls.get(id))
      .filter((c): c is ComplianceControl => c !== undefined);
  }

  /**
   * Add evidence to control
   */
  addEvidence(
    controlId: string,
    evidence: Omit<Evidence, 'id' | 'timestamp'>
  ): Evidence | null {
    const control = this.controls.get(controlId);
    
    if (!control) {
      return null;
    }

    const fullEvidence: Evidence = {
      ...evidence,
      id: `evidence-${++this.evidenceCounter}`,
      timestamp: Date.now(),
    };

    control.evidence.push(fullEvidence);
    return fullEvidence;
  }

  /**
   * Generate coverage report
   */
  generateCoverageReport(options?: {
    framework?: ComplianceFramework;
    includeNodeCoverage?: boolean;
  }): CoverageReport {
    let controls = Array.from(this.controls.values());

    if (options?.framework) {
      controls = controls.filter(c => c.framework === options.framework);
    }

    const byStatus: Record<ControlStatus, number> = {
      planned: 0,
      implemented: 0,
      tested: 0,
      validated: 0,
      failed: 0,
      not_applicable: 0,
    };

    const bySeverity: Record<ControlSeverity, number> = {
      low: 0,
      medium: 0,
      high: 0,
      critical: 0,
    };

    const satisfiedControls: ComplianceControl[] = [];
    const unsatisfiedControls: ComplianceControl[] = [];
    const gaps: ComplianceControl[] = [];

    for (const control of controls) {
      byStatus[control.status]++;
      bySeverity[control.severity]++;

      if (control.status === 'validated' || control.status === 'not_applicable') {
        satisfiedControls.push(control);
      } else if (control.status === 'planned') {
        gaps.push(control);
        unsatisfiedControls.push(control);
      } else {
        unsatisfiedControls.push(control);
      }
    }

    const overallCoverage = controls.length > 0
      ? (satisfiedControls.length / controls.length) * 100
      : 0;

    const nodeCoverage = options?.includeNodeCoverage !== false
      ? this.calculateNodeCoverage()
      : [];

    return {
      timestamp: Date.now(),
      framework: options?.framework,
      totalControls: controls.length,
      byStatus,
      bySeverity,
      overallCoverage,
      satisfiedControls,
      unsatisfiedControls,
      gaps,
      nodeCoverage,
    };
  }

  /**
   * Create audit bundle
   */
  createAuditBundle(options?: {
    framework?: ComplianceFramework;
    createdBy?: string;
    diagram?: AuditBundle['diagram'];
    auditLogReferences?: string[];
    documents?: Evidence[];
  }): AuditBundle {
    const controls = this.listControls({ framework: options?.framework });
    const coverageReport = this.generateCoverageReport({ framework: options?.framework });

    return {
      id: `bundle-${++this.bundleCounter}`,
      createdAt: Date.now(),
      createdBy: options?.createdBy,
      framework: options?.framework,
      controls,
      coverageReport,
      diagram: options?.diagram,
      auditLogReferences: options?.auditLogReferences,
      documents: options?.documents,
    };
  }

  /**
   * Export coverage report to format
   */
  exportCoverageReport(
    report: CoverageReport,
    format: 'json' | 'csv' | 'markdown'
  ): string {
    switch (format) {
      case 'json':
        return JSON.stringify(report, null, 2);

      case 'csv':
        return this.toCsv(report);

      case 'markdown':
        return this.toMarkdown(report);

      default:
        throw new Error(`Unsupported format: ${format}`);
    }
  }

  /**
   * Get statistics
   */
  getStatistics(): {
    totalControls: number;
    totalNodes: number;
    controlsByFramework: Record<ComplianceFramework, number>;
    controlsByStatus: Record<ControlStatus, number>;
    nodesWithControls: number;
    nodesWithoutControls: number;
    averageControlsPerNode: number;
  } {
    const controls = Array.from(this.controls.values());

    const controlsByFramework: Record<ComplianceFramework, number> = {
      SOC2: 0,
      ISO27001: 0,
      GDPR: 0,
      HIPAA: 0,
      'PCI-DSS': 0,
      NIST: 0,
      custom: 0,
    };

    const controlsByStatus: Record<ControlStatus, number> = {
      planned: 0,
      implemented: 0,
      tested: 0,
      validated: 0,
      failed: 0,
      not_applicable: 0,
    };

    for (const control of controls) {
      controlsByFramework[control.framework]++;
      controlsByStatus[control.status]++;
    }

    const nodesWithControls = this.nodeMappings.size;
    const totalControls = controls.length;

    const totalNodeMappings = Array.from(this.nodeMappings.values())
      .reduce((sum, set) => sum + set.size, 0);

    return {
      totalControls,
      totalNodes: nodesWithControls,
      controlsByFramework,
      controlsByStatus,
      nodesWithControls,
      nodesWithoutControls: 0, // Would need node list to calculate
      averageControlsPerNode: nodesWithControls > 0 ? totalNodeMappings / nodesWithControls : 0,
    };
  }

  // Private methods

  /**
   *
   */
  private calculateNodeCoverage(): NodeControlMapping[] {
    const nodeCoverage: NodeControlMapping[] = [];

    for (const [nodeId, controlIds] of this.nodeMappings.entries()) {
      const controls = Array.from(controlIds)
        .map(id => this.controls.get(id))
        .filter((c): c is ComplianceControl => c !== undefined);

      const validatedCount = controls.filter(c => 
        c.status === 'validated' || c.status === 'not_applicable'
      ).length;

      const coverage = controls.length > 0 
        ? (validatedCount / controls.length) * 100 
        : 0;

      const hasFailures = controls.some(c => c.status === 'failed');

      nodeCoverage.push({
        nodeId,
        controlIds: Array.from(controlIds),
        coverage,
        hasFailures,
      });
    }

    return nodeCoverage;
  }

  /**
   *
   */
  private toCsv(report: CoverageReport): string {
    const lines: string[] = [];
    
    // Header
    lines.push('Control ID,Framework,Title,Status,Severity,Owner,Node Count');
    
    // Data rows
    for (const control of [...report.satisfiedControls, ...report.unsatisfiedControls]) {
      lines.push([
        control.controlId,
        control.framework,
        `"${control.title}"`,
        control.status,
        control.severity,
        control.owner ?? '',
        control.nodeIds.length.toString(),
      ].join(','));
    }

    return lines.join('\n');
  }

  /**
   *
   */
  private toMarkdown(report: CoverageReport): string {
    const lines: string[] = [];
    
    lines.push('# Compliance Coverage Report');
    lines.push('');
    lines.push(`**Generated**: ${new Date(report.timestamp).toISOString()}`);
    if (report.framework) {
      lines.push(`**Framework**: ${report.framework}`);
    }
    lines.push(`**Overall Coverage**: ${report.overallCoverage.toFixed(2)}%`);
    lines.push('');

    lines.push('## Summary');
    lines.push('');
    lines.push(`- Total Controls: ${report.totalControls}`);
    lines.push(`- Satisfied: ${report.satisfiedControls.length}`);
    lines.push(`- Unsatisfied: ${report.unsatisfiedControls.length}`);
    lines.push(`- Gaps: ${report.gaps.length}`);
    lines.push('');

    if (report.unsatisfiedControls.length > 0) {
      lines.push('## Unsatisfied Controls');
      lines.push('');
      lines.push('| Control ID | Title | Status | Severity |');
      lines.push('|-----------|-------|--------|----------|');
      for (const control of report.unsatisfiedControls) {
        lines.push(`| ${control.controlId} | ${control.title} | ${control.status} | ${control.severity} |`);
      }
      lines.push('');
    }

    return lines.join('\n');
  }
}

/**
 * Create compliance store
 */
export function createComplianceStore(config?: ComplianceStoreConfig): ComplianceStore {
  return new ComplianceStore(config);
}
