/**
 * Physics JSON Parser
 *
 * Parses physics.json domain content and normalizes to DomainConcept format.
 *
 * @doc.type module
 * @doc.purpose Parse physics domain content from JSON
 * @doc.layer product
 * @doc.pattern Parser
 */

import type {
  DomainConcept,
  ConceptId,
  CurriculumLevel,
  AudienceTag,
  InteractivityLevel,
  CrossDomainRelation,
  ScaffoldingLevel,
  ContentStatus,
  RawConceptJSON,
  PhysicsJSONStructure,
  LearningObjectiveV2,
  SimulationRequirement,
  SimulationRequirementRole,
} from "@ghatana/tutorputor-contracts/v1/curriculum/types";
import { parseTimeToMinutes, mapDifficultyString, mapLevelString } from "../utils/mappers";

/**
 * Parse physics.json content into normalized DomainConcept array.
 *
 * physics.json is an array of objects with { domain, level, concepts: [...] }
 */
export function parsePhysicsJSON(json: unknown): DomainConcept[] {
  if (!Array.isArray(json)) {
    throw new Error("physics.json must be an array of level objects");
  }

  const concepts: DomainConcept[] = [];

  for (const levelBlock of json as PhysicsJSONStructure[]) {
    if (!levelBlock.concepts || !Array.isArray(levelBlock.concepts)) {
      continue;
    }

    const level = mapLevelString(levelBlock.level);

    for (const rawConcept of levelBlock.concepts) {
      try {
        const concept = parseRawConcept(rawConcept, "PHYSICS", level);
        concepts.push(concept);
      } catch (error) {
        console.warn(
          `Failed to parse physics concept ${rawConcept.id}: ${error instanceof Error ? error.message : String(error)}`
        );
      }
    }
  }

  return concepts;
}

/**
 * Parse a single raw concept from JSON into normalized DomainConcept.
 */
export function parseRawConcept(
  raw: RawConceptJSON,
  domain: "PHYSICS" | "CHEMISTRY",
  level: CurriculumLevel
): DomainConcept {
  // Validate required fields
  if (!raw.id || typeof raw.id !== "string") {
    throw new Error("Concept missing required 'id' field");
  }
  if (!raw.name || typeof raw.name !== "string") {
    throw new Error(`Concept ${raw.id} missing required 'name' field`);
  }

  // Parse simulation metadata
  const simulationMetadata = {
    simulationType: raw.simulation_metadata?.simulation_type ?? "interactive_visualization",
    recommendedInteractivity: mapInteractivity(raw.simulation_metadata?.recommended_interactivity),
    purpose: raw.simulation_metadata?.purpose ?? "",
    estimatedTimeMinutes: parseTimeToMinutes(raw.simulation_metadata?.estimated_time ?? "15 min"),
    resourcesNeeded: raw.simulation_metadata?.resources_needed,
    requirements: normalizeSimulationRequirements(raw),
  };

  // Parse learning object metadata
  const learningObjectMetadata = {
    author: raw.learning_object_metadata?.author ?? "TutorPutor Team",
    version: raw.learning_object_metadata?.version ?? "1.0.0",
    status: mapStatus(raw.learning_object_metadata?.status),
    intendedRoles: raw.learning_object_metadata?.intended_end_user_role ?? ["student"],
    contexts: raw.learning_object_metadata?.context ?? ["self-study"],
    difficulty: mapDifficultyString(raw.learning_object_metadata?.difficulty ?? "intermediate"),
    typicalLearningTimeMinutes: parseTimeToMinutes(
      raw.learning_object_metadata?.typical_learning_time ?? "20 min"
    ),
    learningObjectType: raw.learning_object_metadata?.learning_object_type ?? "simulation",
    createdAt: raw.learning_object_metadata?.creation_date ?? new Date().toISOString().split("T")[0],
    updatedAt: raw.learning_object_metadata?.last_modified ?? new Date().toISOString().split("T")[0],
  };

  // Parse pedagogical metadata
  const learningObjectives = raw.pedagogical_metadata?.learning_objectives ?? [];
  const pedagogicalMetadata = {
    learningObjectives,
    learningObjectivesV2: normalizeLearningObjectivesV2(raw.id, raw.pedagogical_metadata?.learning_objectives_v2, learningObjectives),
    competencies: raw.pedagogical_metadata?.competencies ?? [],
    scaffoldingLevel: mapScaffolding(raw.pedagogical_metadata?.scaffolding_level),
    accessibilityNotes: raw.pedagogical_metadata?.accessibility_notes ?? "",
  };

  // Parse cross-domain links
  const crossDomainLinks = (raw.cross_domain_links ?? []).map(
    (link: { target_concept_id: string; relation: string; notes: string }) => ({
      targetConceptId: link.target_concept_id as ConceptId,
      relation: mapRelation(link.relation),
      notes: link.notes ?? "",
    })
  );

  // Parse audience tags
  const audienceTags = (raw.audience_tags ?? []).map(mapAudienceTag);

  return {
    id: raw.id as ConceptId,
    name: raw.name,
    description: raw.description ?? "",
    domain,
    level,
    prerequisites: (raw.prerequisites ?? []) as ConceptId[],
    audienceTags,
    keywords: raw.keywords ?? [],
    simulationMetadata,
    crossDomainLinks,
    learningObjectMetadata,
    pedagogicalMetadata,
  };
}

// =============================================================================
// Mapper Helpers
// =============================================================================

function mapInteractivity(value: string | undefined): InteractivityLevel {
  const normalized = (value ?? "low").toLowerCase();
  if (normalized === "high") return "high";
  if (normalized === "medium") return "medium";
  return "low";
}

function mapStatus(value: string | undefined): ContentStatus {
  const normalized = (value ?? "draft").toLowerCase();
  if (normalized === "published") return "published";
  return "draft";
}

function mapScaffolding(value: string | undefined): ScaffoldingLevel {
  const normalized = (value ?? "scaffolded").toLowerCase();
  if (normalized === "standalone") return "standalone";
  return "scaffolded";
}

function mapRelation(value: string | undefined): CrossDomainRelation {
  const normalized = (value ?? "related_to").toLowerCase();
  if (normalized === "uses") return "uses";
  if (normalized === "depends_on") return "depends_on";
  if (normalized === "foundation_from") return "foundation_from";
  if (normalized === "intersects") return "intersects";
  return "related_to";
}

function mapAudienceTag(tag: string): AudienceTag {
  const normalized = tag.toLowerCase().replace(/[_-]/g, "_");
  if (normalized.includes("k-12") || normalized.includes("k_12")) return "K-12";
  if (normalized.includes("college")) return "College";
  if (normalized.includes("research")) return "Research";
  if (normalized.includes("university") || normalized.includes("advanced")) return "University_Advanced";
  return "Independent_Study";
}

function slugifyStableIdPart(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .replace(/--+/g, "-")
    .slice(0, 40);
}

function normalizeLearningObjectivesV2(
  conceptId: string,
  v2Raw: Array<{ id: string; text: string }> | undefined,
  legacy: string[]
): LearningObjectiveV2[] {
  if (Array.isArray(v2Raw) && v2Raw.length > 0) {
    return v2Raw
      .filter((o) => o && typeof o.id === "string" && typeof o.text === "string")
      .map((o) => ({ id: o.id, text: o.text })) as unknown as LearningObjectiveV2[];
  }

  const seen = new Set<string>();
  const out: LearningObjectiveV2[] = [];
  for (const text of legacy) {
    const base = `${conceptId}:obj:${slugifyStableIdPart(text)}`;
    let id = base;
    let i = 2;
    while (seen.has(id)) {
      id = `${base}-${i}`;
      i += 1;
    }
    seen.add(id);
    out.push({ id, text } as unknown as LearningObjectiveV2);
  }
  return out;
}

function normalizeSimulationRole(value: string | undefined): SimulationRequirementRole {
  const v = (value ?? "PRIMARY_EXPLANATION").toUpperCase();
  if (v === "PRACTICE") return "PRACTICE";
  if (v === "ASSESSMENT") return "ASSESSMENT";
  if (v === "EXTENSION") return "EXTENSION";
  return "PRIMARY_EXPLANATION";
}

function roleSuffix(role: SimulationRequirementRole): string {
  switch (role) {
    case "PRACTICE":
      return "practice";
    case "ASSESSMENT":
      return "assessment";
    case "EXTENSION":
      return "extension";
    case "PRIMARY_EXPLANATION":
    default:
      return "primary";
  }
}

function normalizeSimulationRequirements(raw: RawConceptJSON): SimulationRequirement[] {
  const reqs = raw.simulation_requirements;
  if (!Array.isArray(reqs) || reqs.length === 0) return [];

  const out: SimulationRequirement[] = [];
  for (const r of reqs) {
    if (!r || typeof r !== "object") continue;
    const role = normalizeSimulationRole(r.role);
    const requirementId = typeof r.id === "string" ? r.id : `${raw.id}:sim:${roleSuffix(role)}`;
    const objectiveIds = Array.isArray(r.objective_ids)
      ? (r.objective_ids as unknown[]).filter((x: unknown): x is string => typeof x === "string")
      : undefined;

    const pref = r.preferred_template_ref;
    const preferredTemplateRef =
      pref && typeof pref === "object"
        ? {
            domain: typeof pref.domain === "string" ? pref.domain : undefined,
            file: typeof pref.file === "string" ? pref.file : undefined,
            id: typeof pref.id === "string" ? pref.id : undefined,
          }
        : undefined;

    out.push({
      id: requirementId,
      role,
      objectiveIds,
      preferredTemplateRef,
    } as unknown as SimulationRequirement);
  }

  return out;
}
