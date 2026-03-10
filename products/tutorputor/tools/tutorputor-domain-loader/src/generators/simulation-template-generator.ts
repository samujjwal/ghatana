import { createLogger } from '../utils/logger.js';
const logger = createLogger('simulation-template-generator');

import type { TutorPrismaClient } from "../prisma-utils.js";
import type { DomainConcept, ConceptModuleMapping } from "@ghatana/tutorputor-contracts/v1/curriculum/types";
import type { SimulationManifest as ContractSimulationManifest } from "@ghatana/tutorputor-contracts/v1/simulation/types";

export interface SimulationTemplateGeneratorOptions {
  tenantId: string;
  authorId: string;
  verbose?: boolean;
}

export interface SimulationTemplateGeneratorResult {
  manifestsCreated: number;
  templatesCreated: number;
  warnings: string[];
  errors: string[];
}

export interface ManifestGeneratorResult {
  manifest: ContractSimulationManifest;
  templateType: string;
  isPlaceholder: boolean;
  warnings: string[];
}

/**
 * Persist simulation manifests and templates derived from domain concepts.
 */
export async function generateSimulationTemplates(
  prisma: TutorPrismaClient,
  concepts: DomainConcept[],
  manifestResults: Map<string, ManifestGeneratorResult>,
  moduleMappings: ConceptModuleMapping[],
  options: SimulationTemplateGeneratorOptions
): Promise<SimulationTemplateGeneratorResult> {
  const { tenantId, authorId, verbose } = options;
  const warnings: string[] = [];
  const errors: string[] = [];

  if (concepts.length === 0 || manifestResults.size === 0) {
    return { manifestsCreated: 0, templatesCreated: 0, warnings, errors };
  }

  // Lookup tables
  const conceptById = new Map<string, DomainConcept>();
  for (const concept of concepts) {
    conceptById.set(concept.id as unknown as string, concept);
  }

  const mappingByConceptId = new Map<string, ConceptModuleMapping>();
  for (const mapping of moduleMappings) {
    mappingByConceptId.set(mapping.conceptId as unknown as string, mapping);
  }

  // Load DomainConcept DB IDs for this tenant
  const dbConcepts = await prisma.domainConcept.findMany({
    where: { tenantId },
    select: { id: true, externalId: true },
  });
  const dbConceptIdByExternalId = new Map<string, string>();
  for (const c of dbConcepts) {
    dbConceptIdByExternalId.set(c.externalId, c.id);
  }

  let manifestsCreated = 0;
  let templatesCreated = 0;

  for (const [conceptKeyRaw, result] of manifestResults.entries()) {
    const conceptKey = conceptKeyRaw as string;
    const [baseConceptId, requirementId] = conceptKey.split("::");

    const concept = conceptById.get(baseConceptId);
    if (!concept) {
      warnings.push(`No concept found for manifest conceptId=${baseConceptId}`);
      continue;
    }

    const dbConceptId = dbConceptIdByExternalId.get(concept.id as unknown as string);
    if (!dbConceptId) {
      warnings.push(`No DomainConcept row found for externalId=${concept.id as string}`);
      continue;
    }

    const mapping = mappingByConceptId.get(concept.id as unknown as string);
    if (!mapping) {
      warnings.push(`No module mapping found for conceptId=${concept.id as string}`);
    }

    const manifest = result.manifest;
    // Prisma Json fields expect a plain JSON-compatible object. Our contracts use branded types,
    // which don't satisfy Prisma's InputJsonValue typing, so we serialize/deserialize.
    const manifestJson = JSON.parse(JSON.stringify(manifest));
    const moduleId = mapping?.moduleId as unknown as string | undefined;

    try {
      // Upsert SimulationManifest
      await prisma.simulationManifest.upsert({
        where: { id: manifest.id as unknown as string },
        create: {
          id: manifest.id as unknown as string,
          tenantId,
          domain: manifest.domain,
          version: manifest.version,
          title: manifest.title,
          description: manifest.description ?? null,
          moduleId: moduleId ?? null,
          manifest: manifestJson,
        },
        update: {
          domain: manifest.domain,
          version: manifest.version,
          title: manifest.title,
          description: manifest.description ?? null,
          moduleId: moduleId ?? null,
          manifest: manifestJson,
        },
      });
      manifestsCreated++;

      // Update ConceptModuleMapping.simulationManifestIds if mapping exists
      if (moduleId) {
        const dbMapping = await prisma.conceptModuleMapping.findFirst({
          where: { conceptId: dbConceptId },
        });
        if (dbMapping) {
          let ids: string[] = [];
          try {
            ids = JSON.parse(dbMapping.simulationManifestIds) as string[];
          } catch {
            ids = [];
          }
          if (!ids.includes(manifest.id as unknown as string)) {
            ids.push(manifest.id as unknown as string);
            await prisma.conceptModuleMapping.update({
              where: { conceptId: dbConceptId },
              data: { simulationManifestIds: JSON.stringify(ids) },
            });
          }
        }
      }

      // Derive SimulationTemplate fields from concept + manifest
      const templateDifficulty = mapTemplateDifficulty(concept);
      const tags = JSON.stringify(concept.keywords ?? []);
      const authorName = concept.learningObjectMetadata.author;
      const avgMinutes =
        concept.simulationMetadata.estimatedTimeMinutes ??
        concept.learningObjectMetadata.typicalLearningTimeMinutes ??
        0;

      const slug = generateTemplateSlug(concept, requirementId);

      await prisma.simulationTemplate.upsert({
        where: {
          tenantId_slug: {
            tenantId,
            slug,
          },
        },
        create: {
          tenantId,
          slug,
          title: concept.name,
          description: concept.description,
          domain: concept.domain,
          difficulty: templateDifficulty,
          tags,
          thumbnailUrl: null,
          license: "FREE",
          isPremium: false,
          isVerified: true,
          version: concept.learningObjectMetadata.version,
          authorId,
          authorName,
          authorAvatarUrl: null,
          organization: null,
          statsViews: 0,
          statsUses: 0,
          statsFavorites: 0,
          statsRating: 0,
          statsRatingCount: 0,
          statsCompletionRate: 0,
          statsAvgTimeMinutes: avgMinutes,
          publishedAt:
            concept.learningObjectMetadata.status === "published"
              ? new Date(concept.learningObjectMetadata.createdAt)
              : null,
          conceptId: dbConceptId,
          moduleId: moduleId ?? null,
          manifestId: manifest.id as unknown as string,
        },
        update: {
          title: concept.name,
          description: concept.description,
          domain: concept.domain,
          difficulty: templateDifficulty,
          tags,
          version: concept.learningObjectMetadata.version,
          authorId,
          authorName,
          statsAvgTimeMinutes: avgMinutes,
          conceptId: dbConceptId,
          moduleId: moduleId ?? null,
          manifestId: manifest.id as unknown as string,
        },
      });
      templatesCreated++;

      if (verbose) {
         
        logger.info({}, 
          `Created/updated simulation template slug=${slug} for concept=${concept.id as string}`
        );
      }
    } catch (error) {
      errors.push(
        `Failed to persist simulation template for concept=${concept.id as string}: ${
          error instanceof Error ? error.message : String(error)
        }`
      );
    }
  }

  return { manifestsCreated, templatesCreated, warnings, errors };
}

function mapTemplateDifficulty(concept: DomainConcept): "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT" {
  const diff = concept.learningObjectMetadata.difficulty
    ? concept.learningObjectMetadata.difficulty.toLowerCase()
    : "";

  if (diff === "easy" || diff === "beginner") return "BEGINNER";
  if (diff === "intermediate" || diff === "medium") return "INTERMEDIATE";
  if (diff === "hard" || diff === "advanced" || diff === "very_hard") return "ADVANCED";

  switch (concept.level) {
    case "FOUNDATIONAL":
      return "BEGINNER";
    case "INTERMEDIATE":
      return "INTERMEDIATE";
    case "ADVANCED":
      return "ADVANCED";
    case "RESEARCH":
      return "EXPERT";
    default:
      return "INTERMEDIATE";
  }
}

function generateTemplateSlug(concept: DomainConcept, requirementId?: string): string {
  const base = concept.name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .substring(0, 50);
  const idSuffix = (concept.id as unknown as string).toLowerCase().replace(/_/g, "-");
  const reqSuffix = requirementId ? sanitizeSlugSuffix(requirementId) : "";
  return reqSuffix ? `${base}-${idSuffix}-${reqSuffix}` : `${base}-${idSuffix}`;
}

function sanitizeSlugSuffix(id: string): string {
  return id
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .substring(0, 30);
}
