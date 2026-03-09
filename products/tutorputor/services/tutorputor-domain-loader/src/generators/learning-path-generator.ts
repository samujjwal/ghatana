import { createLogger } from '../utils/logger.js';
const logger = createLogger('learning-path-generator');

/**
 * Learning Path Generator
 *
 * Generates learning paths from domain concepts based on prerequisites.
 *
 * @doc.type module
 * @doc.purpose Generate learning paths from domain concepts
 * @doc.layer product
 * @doc.pattern Generator
 */

import type { TutorPrismaClient } from "../prisma-utils.js";
import type { DomainConcept, CurriculumLevel, ConceptModuleMapping } from "@ghatana/tutorputor-contracts/v1/curriculum/types";

/**
 * Options for learning path generation.
 */
export interface LearningPathGeneratorOptions {
  /** Tenant ID */
  tenantId: string;

  /** User ID for the learning path */
  userId: string;

  /** Whether to create cross-level paths */
  createCrossLevelPaths?: boolean;

  /** Verbose logging */
  verbose?: boolean;
}

/**
 * Result of learning path generation.
 */
export interface LearningPathGeneratorResult {
  /** Number of learning paths created */
  pathsCreated: number;

  /** Path IDs created */
  pathIds: string[];

  /** Warnings */
  warnings: string[];

  /** Errors */
  errors: string[];
}

/**
 * Generate learning paths from domain concepts.
 *
 * Creates:
 * 1. Level-specific paths (e.g., "Physics Foundational", "Chemistry Intermediate")
 * 2. Cross-level paths (e.g., "Physics Complete Journey")
 */
export async function generateLearningPaths(
  prisma: TutorPrismaClient,
  concepts: DomainConcept[],
  mappings: ConceptModuleMapping[],
  options: LearningPathGeneratorOptions
): Promise<LearningPathGeneratorResult> {
  const pathIds: string[] = [];
  const warnings: string[] = [];
  const errors: string[] = [];
  let pathsCreated = 0;

  // Build concept-to-module map
  const conceptToModule = new Map<string, string>();
  for (const mapping of mappings) {
    conceptToModule.set(mapping.conceptId, mapping.moduleId);
  }

  // Group concepts by domain and level
  const domainLevelGroups = new Map<string, DomainConcept[]>();

  for (const concept of concepts) {
    const key = `${concept.domain}:${concept.level}`;
    const group = domainLevelGroups.get(key) ?? [];
    group.push(concept);
    domainLevelGroups.set(key, group);
  }

  // Create level-specific paths
  for (const [key, groupConcepts] of domainLevelGroups) {
    const [domain, level] = key.split(":");

    try {
      const pathId = await createLevelPath(
        prisma,
        groupConcepts,
        conceptToModule,
        domain,
        level as CurriculumLevel,
        options
      );

      if (pathId) {
        pathIds.push(pathId);
        pathsCreated++;

        if (options.verbose) {
          logger.info({}, `Created learning path: ${domain} ${level} (${groupConcepts.length} modules)`);
        }
      }
    } catch (error) {
      errors.push(
        `Failed to create path for ${domain} ${level}: ${error instanceof Error ? error.message : String(error)}`
      );
    }
  }

  // Create cross-level paths (one per domain)
  if (options.createCrossLevelPaths) {
    const domains = new Set(concepts.map((c) => c.domain));

    for (const domain of domains) {
      const domainConcepts = concepts.filter((c) => c.domain === domain);

      try {
        const pathId = await createCrossLevelPath(
          prisma,
          domainConcepts,
          conceptToModule,
          domain,
          options
        );

        if (pathId) {
          pathIds.push(pathId);
          pathsCreated++;

          if (options.verbose) {
            logger.info({}, `Created cross-level path: ${domain} Complete Journey`);
          }
        }
      } catch (error) {
        errors.push(
          `Failed to create cross-level path for ${domain}: ${error instanceof Error ? error.message : String(error)}`
        );
      }
    }
  }

  return {
    pathsCreated,
    pathIds,
    warnings,
    errors,
  };
}

/**
 * Create a learning path for a specific level.
 */
async function createLevelPath(
  prisma: TutorPrismaClient,
  concepts: DomainConcept[],
  conceptToModule: Map<string, string>,
  domain: string,
  level: CurriculumLevel,
  options: LearningPathGeneratorOptions
): Promise<string | null> {
  if (concepts.length === 0) return null;

  // Sort concepts by prerequisite order (topological sort)
  const sortedConcepts = topologicalSort(concepts);

  // Map to title
  const levelTitle = formatLevel(level);
  const domainTitle = formatDomain(domain);
  const pathTitle = `${domainTitle} ${levelTitle} Path`;

  // Check if path already exists
  const existingPath = await prisma.learningPath.findFirst({
    where: {
      tenantId: options.tenantId,
      userId: options.userId,
      title: pathTitle,
    },
  });

  if (existingPath) {
    // Update nodes
    await prisma.learningPathNode.deleteMany({
      where: { pathId: existingPath.id },
    });

    await createPathNodes(prisma, existingPath.id, sortedConcepts, conceptToModule);

    return existingPath.id;
  }

  // Create new path
  const path = await prisma.learningPath.create({
    data: {
      tenantId: options.tenantId,
      userId: options.userId,
      title: pathTitle,
      goal: `Complete all ${levelTitle.toLowerCase()} ${domainTitle.toLowerCase()} concepts`,
      status: "ACTIVE",
    },
  });

  await createPathNodes(prisma, path.id, sortedConcepts, conceptToModule);

  return path.id;
}

/**
 * Create a cross-level learning path for a domain.
 */
async function createCrossLevelPath(
  prisma: TutorPrismaClient,
  concepts: DomainConcept[],
  conceptToModule: Map<string, string>,
  domain: string,
  options: LearningPathGeneratorOptions
): Promise<string | null> {
  if (concepts.length === 0) return null;

  // Sort by level then by prerequisites within level
  const levelOrder: CurriculumLevel[] = ["FOUNDATIONAL", "INTERMEDIATE", "ADVANCED", "RESEARCH"];

  const sortedConcepts = [...concepts].sort((a, b) => {
    const aLevelIndex = levelOrder.indexOf(a.level);
    const bLevelIndex = levelOrder.indexOf(b.level);
    if (aLevelIndex !== bLevelIndex) {
      return aLevelIndex - bLevelIndex;
    }
    // Within same level, sort by prereq count (simpler concepts first)
    return a.prerequisites.length - b.prerequisites.length;
  });

  const domainTitle = formatDomain(domain);
  const pathTitle = `${domainTitle} Complete Journey`;

  // Check if path already exists
  const existingPath = await prisma.learningPath.findFirst({
    where: {
      tenantId: options.tenantId,
      userId: options.userId,
      title: pathTitle,
    },
  });

  if (existingPath) {
    await prisma.learningPathNode.deleteMany({
      where: { pathId: existingPath.id },
    });

    await createPathNodes(prisma, existingPath.id, sortedConcepts, conceptToModule);

    return existingPath.id;
  }

  // Create new path
  const path = await prisma.learningPath.create({
    data: {
      tenantId: options.tenantId,
      userId: options.userId,
      title: pathTitle,
      goal: `Master ${domainTitle.toLowerCase()} from foundations to advanced topics`,
      status: "ACTIVE",
    },
  });

  await createPathNodes(prisma, path.id, sortedConcepts, conceptToModule);

  return path.id;
}

/**
 * Create learning path nodes.
 */
async function createPathNodes(
  prisma: TutorPrismaClient,
  pathId: string,
  concepts: DomainConcept[],
  conceptToModule: Map<string, string>
): Promise<void> {
  for (let i = 0; i < concepts.length; i++) {
    const concept = concepts[i];
    const moduleId = conceptToModule.get(concept.id);

    if (!moduleId) continue;

    await prisma.learningPathNode.create({
      data: {
        pathId,
        moduleId,
        orderIndex: i,
        isOptional: concept.level === "RESEARCH", // Research topics are optional
      },
    });
  }
}

/**
 * Topological sort of concepts based on prerequisites.
 */
function topologicalSort(concepts: DomainConcept[]): DomainConcept[] {
  const conceptMap = new Map<string, DomainConcept>();
  for (const c of concepts) {
    conceptMap.set(c.id, c);
  }

  const inDegree = new Map<string, number>();
  const graph = new Map<string, string[]>();

  // Initialize
  for (const c of concepts) {
    inDegree.set(c.id, 0);
    graph.set(c.id, []);
  }

  // Build graph
  for (const c of concepts) {
    for (const prereqId of c.prerequisites) {
      if (conceptMap.has(prereqId)) {
        graph.get(prereqId)?.push(c.id);
        inDegree.set(c.id, (inDegree.get(c.id) ?? 0) + 1);
      }
    }
  }

  // Kahn's algorithm
  const queue: string[] = [];
  for (const [id, degree] of inDegree) {
    if (degree === 0) queue.push(id);
  }

  const result: DomainConcept[] = [];

  while (queue.length > 0) {
    const current = queue.shift()!;
    const concept = conceptMap.get(current);
    if (concept) result.push(concept);

    for (const next of graph.get(current) ?? []) {
      const deg = (inDegree.get(next) ?? 0) - 1;
      inDegree.set(next, deg);
      if (deg === 0) queue.push(next);
    }
  }

  // Add any remaining (cycle members) at the end
  for (const c of concepts) {
    if (!result.includes(c)) {
      result.push(c);
    }
  }

  return result;
}

/**
 * Format level for display.
 */
function formatLevel(level: CurriculumLevel): string {
  switch (level) {
    case "FOUNDATIONAL":
      return "Foundational";
    case "INTERMEDIATE":
      return "Intermediate";
    case "ADVANCED":
      return "Advanced";
    case "RESEARCH":
      return "Research";
    default:
      return level;
  }
}

/**
 * Format domain for display.
 */
function formatDomain(domain: string): string {
  switch (domain) {
    case "PHYSICS":
      return "Physics";
    case "CHEMISTRY":
      return "Chemistry";
    case "BIOLOGY":
      return "Biology";
    case "MATHEMATICS":
      return "Mathematics";
    case "CS_DISCRETE":
      return "Computer Science";
    case "ENGINEERING":
      return "Engineering";
    case "MEDICINE":
      return "Medicine";
    case "ECONOMICS":
      return "Economics";
    default:
      return domain;
  }
}
