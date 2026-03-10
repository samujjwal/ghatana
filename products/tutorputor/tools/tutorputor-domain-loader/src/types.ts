/**
 * Domain Loader Types
 *
 * @doc.type module
 * @doc.purpose Internal types for domain loader service
 * @doc.layer product
 * @doc.pattern Types
 */

import type { SimulationDomain } from "@ghatana/tutorputor-contracts/v1/simulation/types";
import type { CurriculumLevel, DomainConcept } from "@ghatana/tutorputor-contracts/v1/curriculum/types";

// =============================================================================
// Loader Options
// =============================================================================

/**
 * Options for loading domain content.
 */
export interface LoaderOptions {
  /** Tenant ID to load content into */
  tenantId: string;

  /** Specific domain to load (if not provided, loads all) */
  domain?: "physics" | "chemistry" | "all";

  /** Dry run - validate only, don't persist */
  dryRun?: boolean;

  /** Skip simulation manifest generation */
  skipManifests?: boolean;

  /** Skip learning path generation */
  skipLearningPaths?: boolean;

  /** Skip module generation */
  skipModules?: boolean;

  /** Verbose logging */
  verbose?: boolean;

  /** Path to domain content directory */
  contentDir?: string;
}

// =============================================================================
// Loader Results
// =============================================================================

/**
 * Result of loading domain content.
 */
export interface LoaderResult {
  /** Whether the load was successful */
  success: boolean;

  /** Domains that were loaded */
  domains: string[];

  /** Statistics */
  stats: LoadStatistics;

  /** Warnings encountered */
  warnings: string[];

  /** Errors encountered */
  errors: string[];

  /** Duration in milliseconds */
  durationMs: number;
}

/**
 * Statistics about loaded content.
 */
export interface LoadStatistics {
  /** Total concepts loaded */
  conceptsLoaded: number;

  /** Concepts per domain */
  conceptsByDomain: Record<string, number>;

  /** Concepts per level */
  conceptsByLevel: Record<CurriculumLevel, number>;

  /** Modules created */
  modulesCreated: number;

  /** Prerequisite links created */
  prerequisiteLinks: number;

  /** Cross-domain links recorded */
  crossDomainLinks: number;

  /** Learning paths created */
  learningPathsCreated: number;
}

// =============================================================================
// Validation Results
// =============================================================================

/**
 * Result of validating domain content.
 */
export interface ValidationResult {
  /** Whether the content is valid */
  valid: boolean;

  /** Validation errors */
  errors: ValidationError[];

  /** Validation warnings */
  warnings: ValidationWarning[];

  /** Concepts validated */
  conceptCount: number;
}

/**
 * A validation error.
 */
export interface ValidationError {
  /** Concept ID where error occurred */
  conceptId?: string;

  /** Path within the concept */
  path: string;

  /** Error message */
  message: string;

  /** Error code */
  code: ValidationErrorCode;
}

/**
 * A validation warning.
 */
export interface ValidationWarning {
  /** Concept ID where warning occurred */
  conceptId?: string;

  /** Path within the concept */
  path: string;

  /** Warning message */
  message: string;

  /** Warning code */
  code: ValidationWarningCode;
}

/**
 * Validation error codes.
 */
export type ValidationErrorCode =
  | "INVALID_ID"
  | "MISSING_FIELD"
  | "INVALID_DOMAIN"
  | "INVALID_LEVEL"
  | "CIRCULAR_PREREQUISITE"
  | "MISSING_PREREQUISITE"
  | "INVALID_CROSS_DOMAIN_LINK"
  | "DUPLICATE_ID"
  | "PARSE_ERROR";

/**
 * Validation warning codes.
 */
export type ValidationWarningCode =
  | "MISSING_DESCRIPTION"
  | "EMPTY_PREREQUISITES"
  | "EMPTY_KEYWORDS"
  | "MISSING_LEARNING_OBJECTIVES"
  | "UNKNOWN_AUDIENCE_TAG"
  | "BROKEN_CROSS_DOMAIN_LINK";

// =============================================================================
// Internal Types
// =============================================================================

/**
 * Parsed concept with normalized data.
 */
export interface ParsedConcept extends DomainConcept {
  /** Original raw data for reference */
  _raw?: unknown;
}

/**
 * Concept with resolved prerequisites (IDs to actual concepts).
 */
export interface ResolvedConcept extends ParsedConcept {
  /** Resolved prerequisite concepts */
  resolvedPrerequisites: ParsedConcept[];
}

/**
 * A node in the prerequisite DAG.
 */
export interface PrerequisiteNode {
  conceptId: string;
  dependencies: string[];
  dependents: string[];
  level: number; // Topological level
}

/**
 * Prerequisite graph for a domain.
 */
export interface PrerequisiteGraph {
  nodes: Map<string, PrerequisiteNode>;
  hasCycles: boolean;
  cycles: string[][];
  topologicalOrder: string[];
}
