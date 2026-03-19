/**
 * Type definitions for component traceability.
 *
 * @doc.type module
 * @doc.purpose Component traceability types
 * @doc.layer product
 * @doc.pattern Value Object
 */

/**
 * Component-to-requirement mapping.
 *
 * @doc.type interface
 * @doc.purpose Component requirement mapping
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ComponentRequirement {
  /** Component ID */
  componentId: string;
  /** Requirement IDs */
  requirementIds: string[];
  /** Coverage percentage (0-100) */
  coverage: number;
  /** Last updated timestamp */
  updatedAt: number;
}

/**
 * Requirement-to-component mapping.
 *
 * @doc.type interface
 * @doc.purpose Requirement component mapping
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RequirementComponent {
  /** Requirement ID */
  requirementId: string;
  /** Component IDs */
  componentIds: string[];
  /** Is covered */
  covered: boolean;
  /** Last updated timestamp */
  updatedAt: number;
}

/**
 * Traceability report.
 *
 * @doc.type interface
 * @doc.purpose Traceability report
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TraceabilityReport {
  /** Total requirements */
  totalRequirements: number;
  /** Covered requirements */
  coveredRequirements: number;
  /** Uncovered requirement IDs */
  uncoveredRequirements: string[];
  /** Overall coverage percentage */
  coverage: number;
  /** Component requirements */
  components: ComponentRequirement[];
  /** Requirements coverage */
  requirements: RequirementComponent[];
  /** Generated timestamp */
  timestamp: number;
}

/**
 * Coverage analysis.
 *
 * @doc.type interface
 * @doc.purpose Coverage analysis
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CoverageAnalysis {
  /** Component ID */
  componentId: string;
  /** Total requirements */
  totalRequirements: number;
  /** Covered requirements */
  coveredRequirements: number;
  /** Uncovered requirements */
  uncoveredRequirements: string[];
  /** Coverage percentage */
  coverage: number;
  /** Coverage trend */
  trend?: 'improving' | 'stable' | 'declining';
}

/**
 * Traceability link.
 *
 * @doc.type interface
 * @doc.purpose Traceability link
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TraceabilityLink {
  /** Link ID */
  id: string;
  /** Component ID */
  componentId: string;
  /** Requirement ID */
  requirementId: string;
  /** Link type */
  type: 'implements' | 'tests' | 'documents' | 'relates';
  /** Link status */
  status: 'active' | 'deprecated' | 'archived';
  /** Created timestamp */
  createdAt: number;
  /** Updated timestamp */
  updatedAt: number;
}

/**
 * Traceability options.
 *
 * @doc.type interface
 * @doc.purpose Traceability options
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TraceabilityOptions {
  /** Include deprecated links */
  includeDeprecated?: boolean;
  /** Include archived links */
  includeArchived?: boolean;
  /** Minimum coverage threshold */
  minCoverage?: number;
}

/**
 * Traceability event.
 *
 * @doc.type type
 * @doc.purpose Traceability event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type TraceabilityEvent = 'link-created' | 'link-updated' | 'link-deleted' | 'coverage-changed';

/**
 * Traceability event listener.
 *
 * @doc.type type
 * @doc.purpose Traceability event listener
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type TraceabilityEventListener = (event: TraceabilityEvent, data: unknown) => void;

/**
 * Traceability matrix.
 *
 * @doc.type interface
 * @doc.purpose Traceability matrix
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface TraceabilityMatrix {
  /** Component IDs */
  components: string[];
  /** Requirement IDs */
  requirements: string[];
  /** Matrix data (component x requirement) */
  matrix: boolean[][];
  /** Generated timestamp */
  timestamp: number;
}

/**
 * Gap analysis.
 *
 * @doc.type interface
 * @doc.purpose Gap analysis
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface GapAnalysis {
  /** Uncovered requirements */
  uncoveredRequirements: string[];
  /** Over-implemented requirements */
  overImplementedRequirements: string[];
  /** Orphaned components */
  orphanedComponents: string[];
  /** Gap severity */
  severity: 'critical' | 'high' | 'medium' | 'low';
}
