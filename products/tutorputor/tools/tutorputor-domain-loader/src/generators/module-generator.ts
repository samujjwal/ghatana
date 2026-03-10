import { createLogger } from '../utils/logger.js';
const logger = createLogger('module-generator');

/**
 * Module Generator
 *
 * Generates Module records from DomainConcepts.
 *
 * @doc.type module
 * @doc.purpose Generate modules from domain concepts
 * @doc.layer product
 * @doc.pattern Generator
 */

import type { TutorPrismaClient } from "../prisma-utils.js";
import type { DomainConcept, ConceptModuleMapping, ConceptId } from "@ghatana/tutorputor-contracts/v1/curriculum/types";
import type { ModuleId } from "@ghatana/tutorputor-contracts/v1/types";
import { levelToDifficulty, domainToModuleDomain, generateSlug } from "../utils/mappers";

/**
 * Options for module generation.
 */
export interface ModuleGeneratorOptions {
  /** Tenant ID */
  tenantId: string;

  /** User ID for author attribution */
  authorId: string;

  /** Skip if module already exists */
  skipExisting?: boolean;

  /** Verbose logging */
  verbose?: boolean;
}

/**
 * Result of module generation.
 */
export interface ModuleGeneratorResult {
  /** Number of modules created */
  modulesCreated: number;

  /** Number of modules skipped (already existed) */
  modulesSkipped: number;

  /** Mappings from concept to module */
  mappings: ConceptModuleMapping[];

  /** Warnings */
  warnings: string[];

  /** Errors */
  errors: string[];
}

/**
 * Generate Module records from DomainConcepts.
 */
export async function generateModulesFromConcepts(
  prisma: TutorPrismaClient,
  concepts: DomainConcept[],
  options: ModuleGeneratorOptions
): Promise<ModuleGeneratorResult> {
  const mappings: ConceptModuleMapping[] = [];
  const warnings: string[] = [];
  const errors: string[] = [];
  let modulesCreated = 0;
  let modulesSkipped = 0;

  // Get concept DB IDs
  const dbConcepts = await prisma.domainConcept.findMany({
    where: { tenantId: options.tenantId },
    select: { id: true, externalId: true },
  });
  const conceptDbIdMap = new Map<string, string>();
  for (const c of dbConcepts) {
    conceptDbIdMap.set(c.externalId, c.id);
  }

  for (const concept of concepts) {
    try {
      const slug = generateSlug(concept.name, concept.id);
      const moduleDomain = domainToModuleDomain(concept.domain);
      const difficulty = levelToDifficulty(concept.level);

      // Check if module exists
      const existingModule = await prisma.module.findUnique({
        where: {
          tenantId_slug: {
            tenantId: options.tenantId,
            slug,
          },
        },
      });

      if (existingModule && options.skipExisting) {
        modulesSkipped++;
        mappings.push({
          conceptId: concept.id as ConceptId,
          moduleId: existingModule.id as ModuleId,
          simulationManifestIds: [],
        });
        continue;
      }

      // Create or update module
      const module = await prisma.module.upsert({
        where: {
          tenantId_slug: {
            tenantId: options.tenantId,
            slug,
          },
        },
        create: {
          tenantId: options.tenantId,
          slug,
          title: concept.name,
          description: concept.description,
          domain: moduleDomain,
          difficulty,
          estimatedTimeMinutes: concept.learningObjectMetadata.typicalLearningTimeMinutes,
          status: concept.learningObjectMetadata.status === "published" ? "PUBLISHED" : "DRAFT",
          authorId: options.authorId,
        },
        update: {
          title: concept.name,
          description: concept.description,
          estimatedTimeMinutes: concept.learningObjectMetadata.typicalLearningTimeMinutes,
          updatedBy: options.authorId,
        },
      });

      modulesCreated++;

      // Create tags
      await createModuleTags(prisma, module.id, concept);

      // Create learning objectives
      await createLearningObjectives(prisma, module.id, concept);

      // Create content blocks
      await createContentBlocks(prisma, module.id, concept);

      // Create concept-module mapping
      const conceptDbId = conceptDbIdMap.get(concept.id);
      if (conceptDbId) {
        await prisma.conceptModuleMapping.upsert({
          where: { conceptId: conceptDbId },
          create: {
            conceptId: conceptDbId,
            moduleId: module.id,
            simulationManifestIds: "[]",
          },
          update: {
            moduleId: module.id,
          },
        });
      }

      mappings.push({
        conceptId: concept.id as ConceptId,
        moduleId: module.id as ModuleId,
        simulationManifestIds: [],
      });

      if (options.verbose) {
        logger.info({}, `Created module: ${module.slug} for concept ${concept.id}`);
      }
    } catch (error) {
      errors.push(
        `Failed to create module for ${concept.id}: ${error instanceof Error ? error.message : String(error)}`
      );
    }
  }

  // Link module prerequisites after all modules are created
  await linkModulePrerequisites(prisma, concepts, mappings, options.tenantId, warnings);

  return {
    modulesCreated,
    modulesSkipped,
    mappings,
    warnings,
    errors,
  };
}

/**
 * Create module tags from concept keywords and audience tags.
 */
async function createModuleTags(
  prisma: TutorPrismaClient,
  moduleId: string,
  concept: DomainConcept
): Promise<void> {
  const tags = [...concept.keywords, ...concept.audienceTags];

  // Remove existing tags and recreate
  await prisma.moduleTag.deleteMany({ where: { moduleId } });

  for (const tag of tags) {
    try {
      await prisma.moduleTag.create({
        data: {
          moduleId,
          label: tag,
        },
      });
    } catch {
      // Ignore duplicate tag errors
    }
  }
}

/**
 * Create learning objectives from pedagogical metadata.
 */
async function createLearningObjectives(
  prisma: TutorPrismaClient,
  moduleId: string,
  concept: DomainConcept
): Promise<void> {
  // Remove existing objectives and recreate
  await prisma.moduleLearningObjective.deleteMany({ where: { moduleId } });

  const objectives = concept.pedagogicalMetadata.learningObjectives;

  for (const objective of objectives) {
    // Infer taxonomy level from objective text
    const taxonomyLevel = inferTaxonomyLevel(objective);

    await prisma.moduleLearningObjective.create({
      data: {
        moduleId,
        label: objective,
        taxonomyLevel,
      },
    });
  }
}

/**
 * Infer Bloom's taxonomy level from objective text.
 */
function inferTaxonomyLevel(objective: string): string {
  const lower = objective.toLowerCase();

  // Create level indicators
  if (
    lower.includes("design") ||
    lower.includes("create") ||
    lower.includes("develop") ||
    lower.includes("formulate") ||
    lower.includes("construct")
  ) {
    return "create";
  }

  // Evaluate level indicators
  if (
    lower.includes("evaluate") ||
    lower.includes("assess") ||
    lower.includes("judge") ||
    lower.includes("critique") ||
    lower.includes("justify")
  ) {
    return "evaluate";
  }

  // Analyze level indicators
  if (
    lower.includes("analyze") ||
    lower.includes("compare") ||
    lower.includes("contrast") ||
    lower.includes("differentiate") ||
    lower.includes("distinguish")
  ) {
    return "analyze";
  }

  // Apply level indicators
  if (
    lower.includes("apply") ||
    lower.includes("use") ||
    lower.includes("implement") ||
    lower.includes("solve") ||
    lower.includes("compute") ||
    lower.includes("calculate")
  ) {
    return "apply";
  }

  // Understand level indicators
  if (
    lower.includes("explain") ||
    lower.includes("describe") ||
    lower.includes("interpret") ||
    lower.includes("summarize") ||
    lower.includes("understand")
  ) {
    return "understand";
  }

  // Default to remember
  return "remember";
}

/**
 * Create content blocks for a module.
 */
async function createContentBlocks(
  prisma: TutorPrismaClient,
  moduleId: string,
  concept: DomainConcept
): Promise<void> {
  // Remove existing content blocks
  await prisma.moduleContentBlock.deleteMany({ where: { moduleId } });

  // Block 1: Introduction (rich text)
  await prisma.moduleContentBlock.create({
    data: {
      moduleId,
      orderIndex: 0,
      blockType: "rich_text",
      payload: {
        content: `<h2>${concept.name}</h2>\n<p>${concept.description}</p>`,
        format: "html",
      },
    },
  });

  // Block 2: Simulation placeholder
  if (concept.simulationMetadata.simulationType) {
    await prisma.moduleContentBlock.create({
      data: {
        moduleId,
        orderIndex: 1,
        blockType: "simulation",
        payload: {
          manifestId: null, // To be filled when manifest is generated
          inlineManifest: null,
          placeholder: true,
          simulationType: concept.simulationMetadata.simulationType,
          purpose: concept.simulationMetadata.purpose,
          interactivity: concept.simulationMetadata.recommendedInteractivity,
        },
      },
    });
  }

  // Block 3: Learning objectives summary
  if (concept.pedagogicalMetadata.learningObjectives.length > 0) {
    const objectivesList = concept.pedagogicalMetadata.learningObjectives
      .map((o: string) => `<li>${o}</li>`)
      .join("\n");

    await prisma.moduleContentBlock.create({
      data: {
        moduleId,
        orderIndex: 2,
        blockType: "rich_text",
        payload: {
          content: `<h3>Learning Objectives</h3>\n<ul>\n${objectivesList}\n</ul>`,
          format: "html",
        },
      },
    });
  }

  // Block 4: AI Tutor prompt
  await prisma.moduleContentBlock.create({
    data: {
      moduleId,
      orderIndex: 3,
      blockType: "ai_tutor_prompt",
      payload: {
        contextPrompt: `You are helping a student understand ${concept.name}. ${concept.description} Key concepts include: ${concept.keywords.join(", ")}.`,
        suggestedQuestions: [
          `What is the main concept behind ${concept.name}?`,
          `How does this relate to real-world applications?`,
          `What are common misconceptions about this topic?`,
        ],
      },
    },
  });
}

/**
 * Link module prerequisites based on concept prerequisites.
 */
async function linkModulePrerequisites(
  prisma: TutorPrismaClient,
  concepts: DomainConcept[],
  mappings: ConceptModuleMapping[],
  tenantId: string,
  warnings: string[]
): Promise<void> {
  // Build concept-to-module map
  const conceptToModule = new Map<string, string>();
  for (const mapping of mappings) {
    conceptToModule.set(mapping.conceptId, mapping.moduleId);
  }

  for (const concept of concepts) {
    const moduleId = conceptToModule.get(concept.id);
    if (!moduleId) continue;

    for (const prereqConceptId of concept.prerequisites) {
      const prereqModuleId = conceptToModule.get(prereqConceptId);
      if (!prereqModuleId) {
        warnings.push(
          `Prerequisite module not found for ${prereqConceptId} (required by ${concept.id})`
        );
        continue;
      }

      try {
        await prisma.modulePrerequisite.upsert({
          where: {
            moduleId_prerequisiteModuleId: {
              moduleId,
              prerequisiteModuleId: prereqModuleId,
            },
          },
          create: {
            moduleId,
            prerequisiteModuleId: prereqModuleId,
          },
          update: {},
        });
      } catch (error) {
        warnings.push(
          `Failed to link module prerequisite: ${moduleId} → ${prereqModuleId}: ${error instanceof Error ? error.message : String(error)}`
        );
      }
    }
  }
}
