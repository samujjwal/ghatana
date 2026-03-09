/**
 * Curriculum Types for Domain Content
 *
 * Types for representing curriculum-level abstractions including domain concepts,
 * learning progressions, and cross-domain relationships.
 *
 * @doc.type module
 * @doc.purpose Define curriculum-level abstractions for domain content
 * @doc.layer contracts
 * @doc.pattern Schema
 */

import type { ModuleId, Difficulty, TenantId } from "../types";
import type { SimulationDomain, SimulationId } from "../simulation/types";

// =============================================================================
// Branded IDs for Type Safety
// =============================================================================

export type CurriculumId = string & { readonly __curriculumId: unique symbol };
export type ConceptId = string & { readonly __conceptId: unique symbol };

// =============================================================================
// Curriculum Level Types
// =============================================================================

/**
 * Learning levels within a curriculum, from foundational to research-level.
 */
export type CurriculumLevel =
  | "FOUNDATIONAL"
  | "INTERMEDIATE"
  | "ADVANCED"
  | "RESEARCH";

/**
 * Target audience tags for content.
 */
export type AudienceTag =
  | "K-12"
  | "College"
  | "Independent_Study"
  | "Research"
  | "University_Advanced";

/**
 * Types of relations between concepts across domains.
 */
export type CrossDomainRelation =
  | "uses"
  | "depends_on"
  | "related_to"
  | "foundation_from"
  | "intersects";

/**
 * Interactivity levels for simulations.
 */
export type InteractivityLevel = "low" | "medium" | "high";

/**
 * Scaffolding levels for pedagogical content.
 */
export type ScaffoldingLevel = "standalone" | "scaffolded";

/**
 * Content status.
 */
export type ContentStatus = "published" | "draft";

// =============================================================================
// Simulation Metadata
// =============================================================================

/**
 * Metadata describing simulation characteristics for a concept.
 */
export interface ConceptSimulationMetadata {
  /** Type of simulation (e.g., "physics-2D", "interactive_visualization") */
  simulationType: string;

  /** Recommended level of user interactivity */
  recommendedInteractivity: InteractivityLevel;

  /** Purpose/goal of the simulation */
  purpose: string;

  /** Estimated time to complete in minutes */
  estimatedTimeMinutes: number;

  /** Optional resources needed for the simulation */
  resourcesNeeded?: string[];

  /** Optional simulation requirements (Option 1 schema: multiple simulations per concept) */
  requirements?: SimulationRequirement[];
}

export interface LearningObjectiveV2 {
  /** Stable objective ID, namespaced by concept (e.g., "bio_F_1:obj:cell-types") */
  id: string;

  /** Objective text */
  text: string;
}

export type SimulationRequirementRole =
  | "PRIMARY_EXPLANATION"
  | "PRACTICE"
  | "ASSESSMENT"
  | "EXTENSION";

export interface SimulationTemplateRef {
  /** Template domain (e.g., "physics") */
  domain?: string;

  /** Template file name (e.g., "pendulum-motion.json") */
  file?: string;

  /** Optional template ID */
  id?: string;
}

export interface SimulationRequirement {
  /** Stable requirement ID (e.g., "bio_F_1:sim:primary") */
  id: string;

  /** How this simulation is used */
  role: SimulationRequirementRole;

  /** Stable objective IDs this simulation covers */
  objectiveIds?: string[];

  /** Optional preference for a file-based template */
  preferredTemplateRef?: SimulationTemplateRef;
}

// =============================================================================
// Cross-Domain Link
// =============================================================================

/**
 * A link to a related concept in another domain.
 */
export interface CrossDomainLink {
  /** ID of the target concept */
  targetConceptId: ConceptId;

  /** Type of relationship */
  relation: CrossDomainRelation;

  /** Additional notes about the relationship */
  notes: string;
}

// =============================================================================
// Learning Object Metadata (IEEE LOM inspired)
// =============================================================================

/**
 * Metadata about the learning object, inspired by IEEE LOM standard.
 */
export interface LearningObjectMetadata {
  /** Author/creator of the content */
  author: string;

  /** Semantic version of the content */
  version: string;

  /** Publication status */
  status: ContentStatus;

  /** Intended user roles */
  intendedRoles: string[];

  /** Learning contexts where this is applicable */
  contexts: string[];

  /** Difficulty level */
  difficulty: Difficulty;

  /** Typical time to complete in minutes */
  typicalLearningTimeMinutes: number;

  /** Type of learning object */
  learningObjectType: string;

  /** Creation timestamp (ISO 8601) */
  createdAt: string;

  /** Last modification timestamp (ISO 8601) */
  updatedAt: string;
}

// =============================================================================
// Pedagogical Metadata
// =============================================================================

/**
 * Pedagogical information about the concept.
 */
export interface PedagogicalMetadata {
  /** Learning objectives (Bloom's taxonomy aligned) */
  learningObjectives: string[];

  /** Learning objectives with stable IDs (Option 1 schema) */
  learningObjectivesV2?: LearningObjectiveV2[];

  /** Competencies developed by this concept */
  competencies: string[];

  /** Whether this is standalone or requires scaffolding */
  scaffoldingLevel: ScaffoldingLevel;

  /** Notes about accessibility considerations */
  accessibilityNotes: string;
}

// =============================================================================
// Core Concept Type
// =============================================================================

/**
 * A domain concept representing a single learning unit.
 * Maps directly to JSON structure from physics.json/chemistry.json.
 */
export interface DomainConcept {
  /** Unique identifier (e.g., "phy_F_1", "chem_I_3") */
  id: ConceptId;

  /** Human-readable name */
  name: string;

  /** Detailed description of the concept */
  description: string;

  /** Domain this concept belongs to */
  domain: SimulationDomain;

  /** Curriculum level */
  level: CurriculumLevel;

  /** IDs of prerequisite concepts */
  prerequisites: ConceptId[];

  /** Target audience tags */
  audienceTags: AudienceTag[];

  /** Searchable keywords */
  keywords: string[];

  /** Simulation configuration metadata */
  simulationMetadata: ConceptSimulationMetadata;

  /** Links to concepts in other domains */
  crossDomainLinks: CrossDomainLink[];

  /** IEEE LOM-inspired metadata */
  learningObjectMetadata: LearningObjectMetadata;

  /** Pedagogical information */
  pedagogicalMetadata: PedagogicalMetadata;
}

// =============================================================================
// Curriculum (Collection of Concepts)
// =============================================================================

/**
 * A curriculum is a collection of concepts organized by level for a domain.
 */
export interface Curriculum {
  /** Unique identifier */
  id: CurriculumId;

  /** Tenant owning this curriculum */
  tenantId: TenantId;

  /** Domain this curriculum covers */
  domain: SimulationDomain;

  /** Title of the curriculum */
  title: string;

  /** Description */
  description?: string;

  /** Concepts organized by level */
  levels: Partial<Record<CurriculumLevel, DomainConcept[]>>;

  /** Semantic version */
  version: string;

  /** Creation timestamp */
  createdAt: string;

  /** Last update timestamp */
  updatedAt: string;
}

// =============================================================================
// Mapping Types (Concept → Module/Simulation)
// =============================================================================

/**
 * Maps a concept to its generated module and simulation manifests.
 */
export interface ConceptModuleMapping {
  /** Original concept ID */
  conceptId: ConceptId;

  /** Generated module ID */
  moduleId: ModuleId;

  /** Generated simulation manifest IDs */
  simulationManifestIds: SimulationId[];
}

// =============================================================================
// Import/Export Types
// =============================================================================

/**
 * Raw physics.json structure (array of level objects).
 */
export interface PhysicsJSONStructure {
  domain: "Physics";
  level: string;
  concepts: RawConceptJSON[];
}

/**
 * Raw chemistry.json structure (object with levels).
 */
export interface ChemistryJSONStructure {
  domain: "Chemistry";
  levels: Record<string, { concepts: RawConceptJSON[] }>;
}

/**
 * Raw concept from JSON before normalization.
 */
export interface RawConceptJSON {
  id: string;
  name: string;
  description: string;
  domain?: string;
  level?: string;
  prerequisites: string[];
  audience_tags: string[];
  keywords: string[];
  simulation_metadata: {
    simulation_type: string;
    recommended_interactivity: string;
    purpose: string;
    estimated_time: string;
    resources_needed?: string[];
  };
  cross_domain_links: Array<{
    target_concept_id: string;
    relation: string;
    notes: string;
  }>;
  learning_object_metadata: {
    author: string;
    version: string;
    status: string;
    intended_end_user_role: string[];
    context: string[];
    difficulty: string;
    typical_learning_time: string;
    learning_object_type: string;
    creation_date: string;
    last_modified: string;
  };
  pedagogical_metadata: {
    learning_objectives: string[];
    learning_objectives_v2?: Array<{ id: string; text: string }>;
    competencies: string[];
    scaffolding_level: string;
    accessibility_notes: string;
  };

  simulation_requirements?: Array<{
    id?: string;
    role?: string;
    objective_ids?: string[];
    preferred_template_ref?: {
      domain?: string;
      file?: string;
      id?: string;
    };
  }>;
}

// =============================================================================
// Loader Result Types
// =============================================================================

/**
 * Result of loading domain content.
 */
export interface DomainLoadResult {
  /** Domain that was loaded */
  domain: SimulationDomain;

  /** Number of concepts loaded */
  conceptCount: number;

  /** Number of modules created */
  moduleCount: number;

  /** Number of learning paths created */
  learningPathCount: number;

  /** Any warnings during load */
  warnings: string[];

  /** Any errors during load */
  errors: string[];
}

/**
 * Statistics about loaded content.
 */
export interface LoadStatistics {
  /** Total concepts loaded */
  totalConcepts: number;

  /** Concepts per level */
  conceptsByLevel: Record<CurriculumLevel, number>;

  /** Concepts per domain */
  conceptsByDomain: Record<string, number>;

  /** Prerequisites linked */
  prerequisiteLinks: number;

  /** Cross-domain links */
  crossDomainLinks: number;
}
