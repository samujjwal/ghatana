/**
 * Simulation Adapter for Learning Paths
 *
 * Pure functions to map simulation manifests to learning path steps.
 * Enables treating simulations as first-class learning nodes.
 *
 * @doc.type module
 * @doc.purpose Convert simulation manifests to learning path simulation steps
 * @doc.layer libs
 * @doc.pattern Adapter
 */

import type {
  SimulationManifest,
  SimulationDomain,
  SimulationStep,
  SimEntity,
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type {
  SimulationLearningStep,
  SimulationSkill,
  SimulationStepMetadata,
  SimulationCompletionCriteria,
  LearningPathStepId,
  SkillId,
} from "@ghatana/tutorputor-contracts/v1/learning-path";
import type { Difficulty } from "@ghatana/tutorputor-contracts/v1";

// =============================================================================
// Domain-to-Skills Mapping
// =============================================================================

/**
 * Default skills for each simulation domain.
 * Used when no explicit skills are provided.
 */
const DOMAIN_DEFAULT_SKILLS: Record<SimulationDomain, Array<{ name: string; weight: number }>> = {
  CS_DISCRETE: [
    { name: "Algorithm Analysis", weight: 0.8 },
    { name: "Data Structures", weight: 0.7 },
    { name: "Problem Solving", weight: 0.9 },
  ],
  PHYSICS: [
    { name: "Mechanics", weight: 0.8 },
    { name: "Energy Conservation", weight: 0.7 },
    { name: "Vector Analysis", weight: 0.6 },
  ],
  ECONOMICS: [
    { name: "System Dynamics", weight: 0.8 },
    { name: "Market Analysis", weight: 0.7 },
    { name: "Quantitative Reasoning", weight: 0.6 },
  ],
  CHEMISTRY: [
    { name: "Reaction Mechanisms", weight: 0.8 },
    { name: "Molecular Structure", weight: 0.7 },
    { name: "Stoichiometry", weight: 0.6 },
  ],
  BIOLOGY: [
    { name: "Cellular Processes", weight: 0.8 },
    { name: "Molecular Biology", weight: 0.7 },
    { name: "Systems Thinking", weight: 0.6 },
  ],
  MEDICINE: [
    { name: "Pharmacokinetics", weight: 0.8 },
    { name: "Drug Interactions", weight: 0.7 },
    { name: "Clinical Reasoning", weight: 0.9 },
  ],
  ENGINEERING: [
    { name: "Systems Design", weight: 0.8 },
    { name: "Analysis", weight: 0.7 },
    { name: "Problem Solving", weight: 0.8 },
  ],
  MATHEMATICS: [
    { name: "Mathematical Modeling", weight: 0.8 },
    { name: "Visualization", weight: 0.6 },
    { name: "Abstract Reasoning", weight: 0.9 },
  ],
};

// =============================================================================
// Difficulty Inference
// =============================================================================

/**
 * Infer difficulty level from a simulation manifest.
 *
 * Uses heuristics based on:
 * - Number of entities
 * - Number of steps
 * - Complexity of actions
 * - Domain metadata
 *
 * @param manifest - The simulation manifest to analyze
 * @returns Inferred difficulty level
 */
export function inferDifficulty(manifest: SimulationManifest): Difficulty {
  const entityCount = manifest.initialEntities.length;
  const stepCount = manifest.steps.length;
  const totalActions = manifest.steps.reduce(
    (sum, step) => sum + step.actions.length,
    0
  );

  // Calculate complexity score (0-100)
  let complexityScore = 0;

  // Entity count contribution (max 30 points)
  complexityScore += Math.min(entityCount * 3, 30);

  // Step count contribution (max 30 points)
  complexityScore += Math.min(stepCount * 2, 30);

  // Action density contribution (max 20 points)
  const actionDensity = stepCount > 0 ? totalActions / stepCount : 0;
  complexityScore += Math.min(actionDensity * 4, 20);

  // Domain-specific complexity (max 20 points)
  complexityScore += getDomainComplexityBonus(manifest.domain, manifest);

  // Map to difficulty levels
  if (complexityScore < 30) {
    return "INTRO";
  } else if (complexityScore < 60) {
    return "INTERMEDIATE";
  } else {
    return "ADVANCED";
  }
}

/**
 * Get domain-specific complexity bonus.
 */
function getDomainComplexityBonus(
  domain: SimulationDomain,
  manifest: SimulationManifest
): number {
  switch (domain) {
    case "MEDICINE":
    case "CHEMISTRY":
      // These domains tend to be more complex
      return 15;
    case "PHYSICS":
    case "ECONOMICS":
      return 10;
    case "BIOLOGY":
    case "ENGINEERING":
      return 8;
    case "CS_DISCRETE":
    case "MATHEMATICS":
      // Complexity depends more on content
      return manifest.steps.length > 10 ? 12 : 5;
    default:
      return 5;
  }
}

// =============================================================================
// Skills Inference
// =============================================================================

/**
 * Infer skills from a simulation manifest.
 *
 * Analyzes:
 * - Domain default skills
 * - Entity types present
 * - Action types used
 * - Complexity indicators
 *
 * @param manifest - The simulation manifest to analyze
 * @returns Array of inferred skills
 */
export function inferSkills(manifest: SimulationManifest): SimulationSkill[] {
  const skills: SimulationSkill[] = [];
  const seenSkills = new Set<string>();

  // Add domain default skills
  const domainSkills = DOMAIN_DEFAULT_SKILLS[manifest.domain] || [];
  for (const skill of domainSkills) {
    const skillId = generateSkillId(skill.name);
    if (!seenSkills.has(skill.name)) {
      skills.push({
        skillId: skillId as SkillId,
        name: skill.name,
        weight: skill.weight,
      });
      seenSkills.add(skill.name);
    }
  }

  // Infer additional skills from entity types
  const entitySkills = inferSkillsFromEntities(manifest.initialEntities);
  for (const skill of entitySkills) {
    if (!seenSkills.has(skill.name)) {
      skills.push(skill);
      seenSkills.add(skill.name);
    }
  }

  // Infer skills from actions
  const actionSkills = inferSkillsFromActions(manifest.steps);
  for (const skill of actionSkills) {
    if (!seenSkills.has(skill.name)) {
      skills.push(skill);
      seenSkills.add(skill.name);
    }
  }

  return skills;
}

/**
 * Generate a skill ID from a skill name.
 */
function generateSkillId(name: string): string {
  return `skill_${name.toLowerCase().replace(/\s+/g, "_")}`;
}

/**
 * Infer skills from entity types.
 */
function inferSkillsFromEntities(entities: SimEntity[]): SimulationSkill[] {
  const skills: SimulationSkill[] = [];
  const entityTypes = new Set(entities.map((e) => e.type));

  // Map entity types to skills
  const typeSkillMap: Record<string, { name: string; weight: number }> = {
    rigidBody: { name: "Physics Simulation", weight: 0.7 },
    spring: { name: "Harmonic Motion", weight: 0.6 },
    vector: { name: "Vector Analysis", weight: 0.6 },
    stock: { name: "Systems Thinking", weight: 0.7 },
    flow: { name: "Dynamic Systems", weight: 0.7 },
    atom: { name: "Atomic Structure", weight: 0.6 },
    molecule: { name: "Molecular Chemistry", weight: 0.7 },
    bond: { name: "Chemical Bonding", weight: 0.6 },
    cell: { name: "Cell Biology", weight: 0.7 },
    enzyme: { name: "Biochemistry", weight: 0.7 },
    gene: { name: "Genetics", weight: 0.7 },
    pkCompartment: { name: "Pharmacokinetics", weight: 0.8 },
    dose: { name: "Drug Dosing", weight: 0.7 },
    node: { name: "Graph Theory", weight: 0.6 },
    edge: { name: "Graph Traversal", weight: 0.6 },
  };

  for (const type of entityTypes) {
    const skillDef = typeSkillMap[type];
    if (skillDef) {
      skills.push({
        skillId: generateSkillId(skillDef.name) as SkillId,
        name: skillDef.name,
        weight: skillDef.weight,
      });
    }
  }

  return skills;
}

/**
 * Infer skills from action types.
 */
function inferSkillsFromActions(steps: SimulationStep[]): SimulationSkill[] {
  const skills: SimulationSkill[] = [];
  const actionTypes = new Set<string>();

  for (const step of steps) {
    for (const action of step.actions) {
      actionTypes.add(action.action);
    }
  }

  // Map action types to skills
  const actionSkillMap: Record<string, { name: string; weight: number }> = {
    APPLY_FORCE: { name: "Force Analysis", weight: 0.6 },
    SET_GRAVITY: { name: "Gravitational Physics", weight: 0.5 },
    CREATE_BOND: { name: "Bond Formation", weight: 0.6 },
    BREAK_BOND: { name: "Reaction Mechanisms", weight: 0.7 },
    TRANSCRIBE: { name: "Gene Expression", weight: 0.7 },
    TRANSLATE: { name: "Protein Synthesis", weight: 0.7 },
    ABSORB: { name: "Drug Absorption", weight: 0.6 },
    ELIMINATE: { name: "Drug Elimination", weight: 0.6 },
    SWAP: { name: "Sorting Algorithms", weight: 0.6 },
    COMPARE: { name: "Comparison Operations", weight: 0.5 },
  };

  for (const action of actionTypes) {
    const skillDef = actionSkillMap[action];
    if (skillDef) {
      skills.push({
        skillId: generateSkillId(skillDef.name) as SkillId,
        name: skillDef.name,
        weight: skillDef.weight,
      });
    }
  }

  return skills;
}

// =============================================================================
// Time Estimation
// =============================================================================

/**
 * Estimate time to complete a simulation in minutes.
 *
 * @param manifest - The simulation manifest to analyze
 * @returns Estimated time in minutes
 */
export function estimateCompletionTime(manifest: SimulationManifest): number {
  const stepCount = manifest.steps.length;
  const entityCount = manifest.initialEntities.length;
  const totalActions = manifest.steps.reduce(
    (sum, step) => sum + step.actions.length,
    0
  );

  // Base time per step (30 seconds)
  let estimatedSeconds = stepCount * 30;

  // Additional time for complexity
  estimatedSeconds += entityCount * 5;
  estimatedSeconds += totalActions * 3;

  // Domain-specific multipliers
  const domainMultipliers: Record<SimulationDomain, number> = {
    CS_DISCRETE: 1.0,
    PHYSICS: 1.2,
    ECONOMICS: 1.3,
    CHEMISTRY: 1.4,
    BIOLOGY: 1.3,
    MEDICINE: 1.5,
    ENGINEERING: 1.2,
    MATHEMATICS: 1.1,
  };

  const multiplier = domainMultipliers[manifest.domain] || 1.0;
  estimatedSeconds *= multiplier;

  // Convert to minutes, minimum 1 minute
  return Math.max(1, Math.ceil(estimatedSeconds / 60));
}

// =============================================================================
// Main Conversion Function
// =============================================================================

/**
 * Convert a simulation manifest to a learning path step.
 *
 * This is the main entry point for the adapter. It:
 * - Infers difficulty from manifest complexity
 * - Extracts/infers skills
 * - Estimates completion time
 * - Generates appropriate metadata
 *
 * @param manifest - The simulation manifest to convert
 * @param options - Optional overrides and configuration
 * @returns A SimulationLearningStep ready for inclusion in a learning path
 */
export function toSimulationStep(
  manifest: SimulationManifest,
  options?: {
    stepId?: LearningPathStepId;
    overrideDifficulty?: Difficulty;
    additionalSkills?: SimulationSkill[];
    prerequisites?: Array<{ stepId: LearningPathStepId; type: "required" | "recommended" }>;
    assessmentRefs?: Array<{ assessmentId: string; position: "pre" | "post" | "inline"; required: boolean }>;
    learningObjectives?: string[];
  }
): SimulationLearningStep {
  const difficulty = options?.overrideDifficulty ?? inferDifficulty(manifest);
  const baseSkills = inferSkills(manifest);
  const skills = options?.additionalSkills
    ? [...baseSkills, ...options.additionalSkills]
    : baseSkills;
  const estimatedTime = estimateCompletionTime(manifest);

  const metadata: SimulationStepMetadata = {
    title: manifest.title,
    description: manifest.description,
    tags: extractTags(manifest),
    keywords: extractKeywords(manifest),
  };

  const completionCriteria: SimulationCompletionCriteria = {
    minTimeSpentSeconds: Math.max(60, estimatedTime * 30), // At least 50% of estimated time
    minInteractions: Math.max(1, manifest.steps.length),
  };

  return {
    id: (options?.stepId ?? generateStepId(manifest.id)) as LearningPathStepId,
    type: "simulation",
    simulationId: manifest.id,
    manifestId: manifest.id,
    domain: manifest.domain,
    difficulty,
    skills,
    prerequisites: options?.prerequisites?.map((p) => ({
      stepId: p.stepId,
      type: p.type,
      minScore: p.type === "required" ? 70 : undefined,
    })) ?? [],
    estimatedTimeMinutes: estimatedTime,
    assessmentRefs: (options?.assessmentRefs ?? []).map((ref) => ({
      assessmentId: ref.assessmentId as any, // Will be cast properly at runtime
      position: ref.position,
      required: ref.required,
    })),
    learningObjectives: options?.learningObjectives,
    metadata,
    completionCriteria,
  };
}

/**
 * Generate a step ID from a simulation ID.
 */
function generateStepId(simulationId: string): string {
  return `step_sim_${simulationId}`;
}

/**
 * Extract tags from a manifest.
 */
function extractTags(manifest: SimulationManifest): string[] {
  const tags: string[] = [manifest.domain.toLowerCase()];

  // Add difficulty as tag
  const difficulty = inferDifficulty(manifest);
  tags.push(difficulty.toLowerCase());

  // Add entity type tags
  const entityTypes = new Set(manifest.initialEntities.map((e) => e.type));
  for (const type of entityTypes) {
    tags.push(type);
  }

  return [...new Set(tags)];
}

/**
 * Extract keywords from a manifest for search.
 */
function extractKeywords(manifest: SimulationManifest): string[] {
  const keywords: string[] = [];

  // Add title words
  keywords.push(...manifest.title.toLowerCase().split(/\s+/));

  // Add description words (if any)
  if (manifest.description) {
    keywords.push(...manifest.description.toLowerCase().split(/\s+/).slice(0, 10));
  }

  // Filter out common words and duplicates
  const stopWords = new Set(["the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for"]);
  return [...new Set(keywords.filter((w) => w.length > 2 && !stopWords.has(w)))];
}

// =============================================================================
// Batch Conversion
// =============================================================================

/**
 * Convert multiple manifests to simulation steps.
 *
 * @param manifests - Array of simulation manifests
 * @param options - Optional configuration for all steps
 * @returns Array of SimulationLearningSteps
 */
export function toSimulationSteps(
  manifests: SimulationManifest[],
  options?: {
    sortByDifficulty?: boolean;
  }
): SimulationLearningStep[] {
  const steps = manifests.map((manifest) => toSimulationStep(manifest));

  if (options?.sortByDifficulty) {
    const difficultyOrder: Record<Difficulty, number> = {
      INTRO: 0,
      INTERMEDIATE: 1,
      ADVANCED: 2,
    };
    steps.sort(
      (a, b) => difficultyOrder[a.difficulty] - difficultyOrder[b.difficulty]
    );
  }

  return steps;
}

// =============================================================================
// Validation
// =============================================================================

/**
 * Validate a simulation step for completeness.
 *
 * @param step - The step to validate
 * @returns Validation result with any errors
 */
export function validateSimulationStep(step: SimulationLearningStep): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  if (!step.id) {
    errors.push("Step ID is required");
  }

  if (!step.simulationId) {
    errors.push("Simulation ID is required");
  }

  if (!step.manifestId) {
    errors.push("Manifest ID is required");
  }

  if (!step.domain) {
    errors.push("Domain is required");
  }

  if (!step.difficulty) {
    errors.push("Difficulty is required");
  }

  if (step.estimatedTimeMinutes < 1) {
    errors.push("Estimated time must be at least 1 minute");
  }

  if (!step.metadata?.title) {
    errors.push("Step title is required in metadata");
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
