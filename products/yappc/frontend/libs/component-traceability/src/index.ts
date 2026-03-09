/**
 * Component traceability service.
 *
 * <p><b>Purpose</b><br>
 * Manages component-to-requirement relationships and generates traceability reports.
 *
 * @doc.type module
 * @doc.purpose Component traceability
 * @doc.layer product
 * @doc.pattern Service
 */

import type {
  ComponentRequirement,
  RequirementComponent,
  TraceabilityReport,
  CoverageAnalysis,
  TraceabilityLink,
  TraceabilityOptions,
  TraceabilityEvent,
  TraceabilityEventListener,
  TraceabilityMatrix,
  GapAnalysis,
} from './types';

/**
 * Component traceability manager.
 *
 * <p><b>Purpose</b><br>
 * Manages component-to-requirement relationships and generates reports.
 *
 * @doc.type class
 * @doc.purpose Traceability manager
 * @doc.layer product
 * @doc.pattern Service
 */
export class TraceabilityManager {
  private links: Map<string, TraceabilityLink> = new Map();
  private listeners: Map<TraceabilityEvent, Set<TraceabilityEventListener>> = new Map();
  private options: Required<TraceabilityOptions>;

  /**
   * Create a new traceability manager.
   *
   * @param options - Manager options
   *
   * @doc.type constructor
   * @doc.purpose Initialize manager
   * @doc.layer product
   * @doc.pattern Service
   */
  constructor(options: TraceabilityOptions = {}) {
    this.options = {
      includeDeprecated: options.includeDeprecated ?? false,
      includeArchived: options.includeArchived ?? false,
      minCoverage: options.minCoverage ?? 0,
    };

    this.initializeListeners();
  }

  /**
   * Initialize event listeners.
   */
  private initializeListeners(): void {
    const events: TraceabilityEvent[] = [
      'link-created',
      'link-updated',
      'link-deleted',
      'coverage-changed',
    ];
    for (const event of events) {
      this.listeners.set(event, new Set());
    }
  }

  /**
   * Subscribe to traceability events.
   *
   * @param event - Event name
   * @param listener - Event listener
   */
  public on(event: TraceabilityEvent, listener: TraceabilityEventListener): void {
    const listeners = this.listeners.get(event);
    if (listeners) {
      listeners.add(listener);
    }
  }

  /**
   * Unsubscribe from traceability events.
   *
   * @param event - Event name
   * @param listener - Event listener
   */
  public off(event: TraceabilityEvent, listener: TraceabilityEventListener): void {
    const listeners = this.listeners.get(event);
    if (listeners) {
      listeners.delete(listener);
    }
  }

  /**
   * Emit traceability event.
   *
   * @param event - Event name
   * @param data - Event data
   */
  private emit(event: TraceabilityEvent, data: unknown): void {
    const listeners = this.listeners.get(event);
    if (listeners) {
      for (const listener of listeners) {
        listener(event, data);
      }
    }
  }

  /**
   * Link component to requirement.
   *
   * @param componentId - Component ID
   * @param requirementId - Requirement ID
   * @param type - Link type
   */
  public linkComponentToRequirement(
    componentId: string,
    requirementId: string,
    type: 'implements' | 'tests' | 'documents' | 'relates' = 'implements'
  ): TraceabilityLink {
    const linkId = `${componentId}-${requirementId}-${type}`;
    const link: TraceabilityLink = {
      id: linkId,
      componentId,
      requirementId,
      type,
      status: 'active',
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    this.links.set(linkId, link);
    this.emit('link-created', link);

    return link;
  }

  /**
   * Unlink component from requirement.
   *
   * @param componentId - Component ID
   * @param requirementId - Requirement ID
   */
  public unlinkComponentFromRequirement(componentId: string, requirementId: string): void {
    const keysToDelete: string[] = [];

    for (const [key, link] of this.links) {
      if (link.componentId === componentId && link.requirementId === requirementId) {
        keysToDelete.push(key);
      }
    }

    for (const key of keysToDelete) {
      const link = this.links.get(key);
      if (link) {
        this.links.delete(key);
        this.emit('link-deleted', link);
      }
    }
  }

  /**
   * Get component requirements.
   *
   * @param componentId - Component ID
   * @returns Component requirements
   */
  public getComponentRequirements(componentId: string): ComponentRequirement {
    const requirementIds: string[] = [];

    for (const link of this.links.values()) {
      if (
        link.componentId === componentId &&
        link.status === 'active' &&
        (this.options.includeDeprecated || link.status !== 'deprecated') &&
        (this.options.includeArchived || link.status !== 'archived')
      ) {
        requirementIds.push(link.requirementId);
      }
    }

    return {
      componentId,
      requirementIds,
      coverage: requirementIds.length > 0 ? 100 : 0,
      updatedAt: Date.now(),
    };
  }

  /**
   * Get requirement components.
   *
   * @param requirementId - Requirement ID
   * @returns Requirement components
   */
  public getRequirementComponents(requirementId: string): RequirementComponent {
    const componentIds: string[] = [];

    for (const link of this.links.values()) {
      if (
        link.requirementId === requirementId &&
        link.status === 'active' &&
        (this.options.includeDeprecated || link.status !== 'deprecated') &&
        (this.options.includeArchived || link.status !== 'archived')
      ) {
        componentIds.push(link.componentId);
      }
    }

    return {
      requirementId,
      componentIds,
      covered: componentIds.length > 0,
      updatedAt: Date.now(),
    };
  }

  /**
   * Generate traceability report.
   *
   * @param allRequirementIds - All requirement IDs
   * @param allComponentIds - All component IDs
   * @returns Traceability report
   */
  public generateReport(
    allRequirementIds: string[],
    allComponentIds: string[]
  ): TraceabilityReport {
    const components: ComponentRequirement[] = [];
    const requirements: RequirementComponent[] = [];
    let coveredRequirements = 0;

    for (const componentId of allComponentIds) {
      components.push(this.getComponentRequirements(componentId));
    }

    for (const requirementId of allRequirementIds) {
      const req = this.getRequirementComponents(requirementId);
      requirements.push(req);
      if (req.covered) {
        coveredRequirements++;
      }
    }

    const uncoveredRequirements = allRequirementIds.filter(
      (id) => !requirements.find((r) => r.requirementId === id)?.covered
    );

    const coverage =
      allRequirementIds.length > 0
        ? Math.round((coveredRequirements / allRequirementIds.length) * 100)
        : 0;

    return {
      totalRequirements: allRequirementIds.length,
      coveredRequirements,
      uncoveredRequirements,
      coverage,
      components,
      requirements,
      timestamp: Date.now(),
    };
  }

  /**
   * Analyze component coverage.
   *
   * @param componentId - Component ID
   * @param allRequirementIds - All requirement IDs
   * @returns Coverage analysis
   */
  public analyzeCoverage(componentId: string, allRequirementIds: string[]): CoverageAnalysis {
    const componentReqs = this.getComponentRequirements(componentId);
    const coveredCount = componentReqs.requirementIds.length;
    const totalCount = allRequirementIds.length;
    const coverage =
      totalCount > 0 ? Math.round((coveredCount / totalCount) * 100) : 0;

    const uncovered = allRequirementIds.filter(
      (id) => !componentReqs.requirementIds.includes(id)
    );

    return {
      componentId,
      totalRequirements: totalCount,
      coveredRequirements: coveredCount,
      uncoveredRequirements: uncovered,
      coverage,
    };
  }

  /**
   * Generate traceability matrix.
   *
   * @param componentIds - Component IDs
   * @param requirementIds - Requirement IDs
   * @returns Traceability matrix
   */
  public generateMatrix(componentIds: string[], requirementIds: string[]): TraceabilityMatrix {
    const matrix: boolean[][] = [];

    for (let i = 0; i < componentIds.length; i++) {
      matrix[i] = [];
      const componentReqs = this.getComponentRequirements(componentIds[i]);

      for (let j = 0; j < requirementIds.length; j++) {
        matrix[i][j] = componentReqs.requirementIds.includes(requirementIds[j]);
      }
    }

    return {
      components: componentIds,
      requirements: requirementIds,
      matrix,
      timestamp: Date.now(),
    };
  }

  /**
   * Perform gap analysis.
   *
   * @param componentIds - Component IDs
   * @param requirementIds - Requirement IDs
   * @returns Gap analysis
   */
  public analyzeGaps(componentIds: string[], requirementIds: string[]): GapAnalysis {
    const uncoveredRequirements: string[] = [];
    const overImplementedRequirements: string[] = [];
    const orphanedComponents: string[] = [];

    // Find uncovered requirements
    for (const requirementId of requirementIds) {
      const req = this.getRequirementComponents(requirementId);
      if (!req.covered) {
        uncoveredRequirements.push(requirementId);
      }
    }

    // Find orphaned components
    for (const componentId of componentIds) {
      const comp = this.getComponentRequirements(componentId);
      if (comp.requirementIds.length === 0) {
        orphanedComponents.push(componentId);
      }
    }

    // Determine severity
    let severity: 'critical' | 'high' | 'medium' | 'low' = 'low';
    const gapPercentage = (uncoveredRequirements.length / requirementIds.length) * 100;

    if (gapPercentage > 50) {
      severity = 'critical';
    } else if (gapPercentage > 30) {
      severity = 'high';
    } else if (gapPercentage > 10) {
      severity = 'medium';
    }

    return {
      uncoveredRequirements,
      overImplementedRequirements,
      orphanedComponents,
      severity,
    };
  }

  /**
   * Get all links.
   *
   * @returns All traceability links
   */
  public getAllLinks(): TraceabilityLink[] {
    return Array.from(this.links.values());
  }

  /**
   * Clear all links.
   */
  public clearAllLinks(): void {
    this.links.clear();
    this.emit('coverage-changed', { coverage: 0 });
  }
}

export type {
  ComponentRequirement,
  RequirementComponent,
  TraceabilityReport,
  CoverageAnalysis,
  TraceabilityLink,
  TraceabilityOptions,
  TraceabilityEvent,
  TraceabilityEventListener,
  TraceabilityMatrix,
  GapAnalysis,
};
