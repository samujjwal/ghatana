import { createLogger } from '../utils/logger.js';
const logger = createLogger('domain-loader');

/**
 * Domain Loader
 *
 * Main loader that orchestrates parsing, validation, and persistence of domain content.
 *
 * @doc.type module
 * @doc.purpose Load and persist domain content to database
 * @doc.layer product
 * @doc.pattern ETL
 */

import { readFileSync } from "fs";
import { join, resolve, dirname } from "path";
import { fileURLToPath } from "url";
import type { TutorPrismaClient, TransactionClient } from "../prisma-utils.js";
import type { DomainConcept } from "@ghatana/tutorputor-contracts/v1/curriculum/types";
import type {
  LoaderOptions,
  LoaderResult,
  LoadStatistics,
  ValidationResult,
  ValidationError,
  ValidationWarning,
  PrerequisiteGraph,
  PrerequisiteNode,
} from "../types";
import { parsePhysicsJSON } from "../parsers/physics-parser";
import { parseChemistryJSON } from "../parsers/chemistry-parser";

// Default content directory (relative to project root)
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

function getTutorputorRootDir(): string {
  return resolve(__dirname, "../../../..");
}

const DEFAULT_CONTENT_DIR = resolve(getTutorputorRootDir(), "content", "domains");

/**
 * Load domain content from JSON files into the database.
 */
export async function loadDomainContent(
  prisma: TutorPrismaClient,
  options: LoaderOptions
): Promise<LoaderResult> {
  const startTime = Date.now();
  const warnings: string[] = [];
  const errors: string[] = [];
  const domainsLoaded: string[] = [];

  const stats: LoadStatistics = {
    conceptsLoaded: 0,
    conceptsByDomain: {},
    conceptsByLevel: {
      FOUNDATIONAL: 0,
      INTERMEDIATE: 0,
      ADVANCED: 0,
      RESEARCH: 0,
    },
    modulesCreated: 0,
    prerequisiteLinks: 0,
    crossDomainLinks: 0,
    learningPathsCreated: 0,
  };

  try {
    const contentDir = options.contentDir ?? DEFAULT_CONTENT_DIR;
    const allConcepts: DomainConcept[] = [];

    // Load Physics
    if (options.domain === "physics" || options.domain === "all" || !options.domain) {
      try {
        const physicsPath = join(contentDir, "physics.json");
        const physicsData = JSON.parse(readFileSync(physicsPath, "utf-8"));
        const physicsConcepts = parsePhysicsJSON(physicsData);
        allConcepts.push(...physicsConcepts);
        domainsLoaded.push("PHYSICS");

        stats.conceptsByDomain["PHYSICS"] = physicsConcepts.length;

        if (options.verbose) {
          logger.info({}, `Parsed ${physicsConcepts.length} physics concepts`);
        }
      } catch (error) {
        const msg = `Failed to load physics.json: ${error instanceof Error ? error.message : String(error)}`;
        errors.push(msg);
        if (options.verbose) logger.error({}, msg);
      }
    }

    // Load Chemistry
    if (options.domain === "chemistry" || options.domain === "all" || !options.domain) {
      try {
        const chemistryPath = join(contentDir, "chemistry.json");
        const chemistryData = JSON.parse(readFileSync(chemistryPath, "utf-8"));
        const chemistryConcepts = parseChemistryJSON(chemistryData);
        allConcepts.push(...chemistryConcepts);
        domainsLoaded.push("CHEMISTRY");

        stats.conceptsByDomain["CHEMISTRY"] = chemistryConcepts.length;

        if (options.verbose) {
          logger.info({}, `Parsed ${chemistryConcepts.length} chemistry concepts`);
        }
      } catch (error) {
        const msg = `Failed to load chemistry.json: ${error instanceof Error ? error.message : String(error)}`;
        errors.push(msg);
        if (options.verbose) logger.error({}, msg);
      }
    }

    if (allConcepts.length === 0) {
      return {
        success: false,
        domains: [],
        stats,
        warnings,
        errors: [...errors, "No concepts loaded from any domain"],
        durationMs: Date.now() - startTime,
      };
    }

    // Validate prerequisites (check for cycles)
    const graph = buildPrerequisiteGraph(allConcepts);
    if (graph.hasCycles) {
      for (const cycle of graph.cycles) {
        warnings.push(`Circular prerequisite detected: ${cycle.join(" → ")}`);
      }
    }

    // Count by level
    for (const concept of allConcepts) {
      stats.conceptsByLevel[concept.level]++;
      stats.crossDomainLinks += concept.crossDomainLinks.length;
    }
    stats.conceptsLoaded = allConcepts.length;

    // Dry run - stop here
    if (options.dryRun) {
      return {
        success: true,
        domains: domainsLoaded,
        stats,
        warnings,
        errors,
        durationMs: Date.now() - startTime,
      };
    }

    // Persist to database
    await persistConcepts(prisma, options.tenantId, allConcepts, stats, warnings);

    return {
      success: errors.length === 0,
      domains: domainsLoaded,
      stats,
      warnings,
      errors,
      durationMs: Date.now() - startTime,
    };
  } catch (error) {
    errors.push(`Unexpected error: ${error instanceof Error ? error.message : String(error)}`);
    return {
      success: false,
      domains: domainsLoaded,
      stats,
      warnings,
      errors,
      durationMs: Date.now() - startTime,
    };
  }
}

/**
 * Validate domain content without persisting.
 */
export async function validateDomainContent(
  options: Omit<LoaderOptions, "dryRun">
): Promise<ValidationResult> {
  const errors: ValidationError[] = [];
  const warnings: ValidationWarning[] = [];
  let conceptCount = 0;

  try {
    const contentDir = options.contentDir ?? DEFAULT_CONTENT_DIR;
    const allConcepts: DomainConcept[] = [];

    // Load and parse
    if (options.domain === "physics" || options.domain === "all" || !options.domain) {
      try {
        const physicsPath = join(contentDir, "physics.json");
        const physicsData = JSON.parse(readFileSync(physicsPath, "utf-8"));
        const physicsConcepts = parsePhysicsJSON(physicsData);
        allConcepts.push(...physicsConcepts);
      } catch (error) {
        errors.push({
          path: "physics.json",
          message: error instanceof Error ? error.message : String(error),
          code: "PARSE_ERROR",
        });
      }
    }

    if (options.domain === "chemistry" || options.domain === "all" || !options.domain) {
      try {
        const chemistryPath = join(contentDir, "chemistry.json");
        const chemistryData = JSON.parse(readFileSync(chemistryPath, "utf-8"));
        const chemistryConcepts = parseChemistryJSON(chemistryData);
        allConcepts.push(...chemistryConcepts);
      } catch (error) {
        errors.push({
          path: "chemistry.json",
          message: error instanceof Error ? error.message : String(error),
          code: "PARSE_ERROR",
        });
      }
    }

    conceptCount = allConcepts.length;

    // Check for duplicate IDs
    const seenIds = new Set<string>();
    for (const concept of allConcepts) {
      if (seenIds.has(concept.id)) {
        errors.push({
          conceptId: concept.id,
          path: "id",
          message: `Duplicate concept ID: ${concept.id}`,
          code: "DUPLICATE_ID",
        });
      }
      seenIds.add(concept.id);
    }

    // Validate each concept
    for (const concept of allConcepts) {
      validateConcept(concept, seenIds, errors, warnings);
    }

    // Check for circular prerequisites
    const graph = buildPrerequisiteGraph(allConcepts);
    if (graph.hasCycles) {
      for (const cycle of graph.cycles) {
        errors.push({
          path: "prerequisites",
          message: `Circular prerequisite: ${cycle.join(" → ")}`,
          code: "CIRCULAR_PREREQUISITE",
        });
      }
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
      conceptCount,
    };
  } catch (error) {
    errors.push({
      path: "root",
      message: error instanceof Error ? error.message : String(error),
      code: "PARSE_ERROR",
    });
    return {
      valid: false,
      errors,
      warnings,
      conceptCount,
    };
  }
}

/**
 * Validate a single concept.
 */
function validateConcept(
  concept: DomainConcept,
  allIds: Set<string>,
  errors: ValidationError[],
  warnings: ValidationWarning[]
): void {
  // Check prerequisites exist
  for (const prereqId of concept.prerequisites) {
    if (!allIds.has(prereqId)) {
      warnings.push({
        conceptId: concept.id,
        path: "prerequisites",
        message: `Prerequisite not found: ${prereqId}`,
        code: "BROKEN_CROSS_DOMAIN_LINK",
      });
    }
  }

  // Warn about empty learning objectives
  if (concept.pedagogicalMetadata.learningObjectives.length === 0) {
    warnings.push({
      conceptId: concept.id,
      path: "pedagogicalMetadata.learningObjectives",
      message: "No learning objectives defined",
      code: "MISSING_LEARNING_OBJECTIVES",
    });
  }

  // Warn about empty keywords
  if (concept.keywords.length === 0) {
    warnings.push({
      conceptId: concept.id,
      path: "keywords",
      message: "No keywords defined",
      code: "EMPTY_KEYWORDS",
    });
  }
}

/**
 * Build a prerequisite graph to detect cycles.
 */
function buildPrerequisiteGraph(concepts: DomainConcept[]): PrerequisiteGraph {
  const nodes = new Map<string, PrerequisiteNode>();

  // Initialize nodes
  for (const concept of concepts) {
    nodes.set(concept.id, {
      conceptId: concept.id,
      dependencies: [...concept.prerequisites],
      dependents: [],
      level: -1,
    });
  }

  // Build reverse links (dependents)
  for (const concept of concepts) {
    for (const prereqId of concept.prerequisites) {
      const prereqNode = nodes.get(prereqId);
      if (prereqNode) {
        prereqNode.dependents.push(concept.id);
      }
    }
  }

  // Detect cycles using DFS
  const cycles: string[][] = [];
  const visited = new Set<string>();
  const recursionStack = new Set<string>();

  function dfs(nodeId: string, path: string[]): boolean {
    visited.add(nodeId);
    recursionStack.add(nodeId);
    path.push(nodeId);

    const node = nodes.get(nodeId);
    if (node) {
      for (const depId of node.dependencies) {
        if (!visited.has(depId)) {
          if (dfs(depId, path)) {
            return true;
          }
        } else if (recursionStack.has(depId)) {
          // Found cycle
          const cycleStart = path.indexOf(depId);
          cycles.push([...path.slice(cycleStart), depId]);
          return true;
        }
      }
    }

    path.pop();
    recursionStack.delete(nodeId);
    return false;
  }

  for (const concept of concepts) {
    if (!visited.has(concept.id)) {
      dfs(concept.id, []);
    }
  }

  // Compute topological order (if no cycles)
  const topologicalOrder: string[] = [];
  if (cycles.length === 0) {
    const inDegree = new Map<string, number>();
    for (const [id, node] of nodes) {
      inDegree.set(id, node.dependencies.filter((d) => nodes.has(d)).length);
    }

    const queue: string[] = [];
    for (const [id, degree] of inDegree) {
      if (degree === 0) queue.push(id);
    }

    while (queue.length > 0) {
      const current = queue.shift()!;
      topologicalOrder.push(current);

      const node = nodes.get(current);
      if (node) {
        for (const depId of node.dependents) {
          const deg = inDegree.get(depId) ?? 0;
          inDegree.set(depId, deg - 1);
          if (deg - 1 === 0) queue.push(depId);
        }
      }
    }
  }

  return {
    nodes,
    hasCycles: cycles.length > 0,
    cycles,
    topologicalOrder,
  };
}

/**
 * Persist concepts to the database.
 */
async function persistConcepts(
  prisma: TutorPrismaClient,
  tenantId: string,
  concepts: DomainConcept[],
  stats: LoadStatistics,
  warnings: string[]
): Promise<void> {
  // Create concept records in a transaction
  await prisma.$transaction(async (tx: TransactionClient) => {
    // First, create all DomainConcept records
    for (const concept of concepts) {
      // Convert complex objects to JSON-compatible format for Prisma
      const simulationMeta = JSON.parse(JSON.stringify(concept.simulationMetadata));
      const learningMeta = JSON.parse(JSON.stringify(concept.learningObjectMetadata));
      const pedagogicalMeta = JSON.parse(JSON.stringify(concept.pedagogicalMetadata));
      const crossDomainLinksJson = JSON.parse(JSON.stringify(concept.crossDomainLinks));

      await tx.domainConcept.upsert({
        where: {
          tenantId_externalId: {
            tenantId,
            externalId: concept.id,
          },
        },
        create: {
          tenantId,
          externalId: concept.id,
          name: concept.name,
          description: concept.description,
          domain: concept.domain as "PHYSICS" | "CHEMISTRY",
          level: concept.level,
          keywords: JSON.stringify(concept.keywords),
          audienceTags: JSON.stringify(concept.audienceTags),
          simulationMetadata: simulationMeta,
          learningObjectMetadata: learningMeta,
          pedagogicalMetadata: pedagogicalMeta,
          crossDomainLinks: crossDomainLinksJson,
          status: concept.learningObjectMetadata.status === "published" ? "PUBLISHED" : "DRAFT",
        },
        update: {
          name: concept.name,
          description: concept.description,
          keywords: JSON.stringify(concept.keywords),
          audienceTags: JSON.stringify(concept.audienceTags),
          simulationMetadata: simulationMeta,
          learningObjectMetadata: learningMeta,
          pedagogicalMetadata: pedagogicalMeta,
          crossDomainLinks: crossDomainLinksJson,
          status: concept.learningObjectMetadata.status === "published" ? "PUBLISHED" : "DRAFT",
        },
      });
    }

    // Build ID map for linking prerequisites
    const conceptIdMap = new Map<string, string>();
    const dbConcepts = await tx.domainConcept.findMany({
      where: { tenantId },
      select: { id: true, externalId: true },
    });
    for (const c of dbConcepts) {
      conceptIdMap.set(c.externalId, c.id);
    }

    // Create prerequisite links
    for (const concept of concepts) {
      const conceptDbId = conceptIdMap.get(concept.id);
      if (!conceptDbId) continue;

      for (const prereqExternalId of concept.prerequisites) {
        const prereqDbId = conceptIdMap.get(prereqExternalId);
        if (!prereqDbId) {
          warnings.push(`Prerequisite ${prereqExternalId} not found for ${concept.id}`);
          continue;
        }

        try {
          await tx.conceptPrerequisite.upsert({
            where: {
              conceptId_prerequisiteId: {
                conceptId: conceptDbId,
                prerequisiteId: prereqDbId,
              },
            },
            create: {
              conceptId: conceptDbId,
              prerequisiteId: prereqDbId,
            },
            update: {},
          });
          stats.prerequisiteLinks++;
        } catch (error) {
          warnings.push(
            `Failed to link ${concept.id} → ${prereqExternalId}: ${error instanceof Error ? error.message : String(error)}`
          );
        }
      }
    }
  });
}
