/**
 * Asset Backfill Service
 *
 * Populates canonical ContentAsset records from existing Module and
 * LearningExperience roots. Produces mapping records for cutover
 * verification and flags content that cannot be migrated cleanly.
 *
 * @doc.type class
 * @doc.purpose Backfill canonical assets from legacy content roots
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { Logger } from "pino";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface BackfillResult {
  totalProcessed: number;
  succeeded: number;
  failed: number;
  skipped: number;
  failures: BackfillFailure[];
  mappings: BackfillMapping[];
}

export interface BackfillFailure {
  sourceType: "module" | "experience";
  sourceId: string;
  reason: string;
}

export interface BackfillMapping {
  sourceType: "module" | "experience";
  sourceId: string;
  assetId: string;
  assetSlug: string;
}

export interface BackfillOptions {
  /** Only migrate records belonging to this tenant. */
  tenantId: string;
  /** Skip records already migrated (default: true). */
  skipExisting?: boolean;
  /** Batch size for processing (default: 50). */
  batchSize?: number;
  /** Dry run — validate but do not persist (default: false). */
  dryRun?: boolean;
}

// ---------------------------------------------------------------------------
// Slug helper
// ---------------------------------------------------------------------------

function slugify(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 128);
}

// ---------------------------------------------------------------------------
// Domain mapping helpers
// ---------------------------------------------------------------------------

const MODULE_STATUS_TO_ASSET: Record<string, string> = {
  DRAFT: "DRAFT",
  PUBLISHED: "PUBLISHED",
  ARCHIVED: "ARCHIVED",
};

const EXPERIENCE_STATUS_TO_ASSET: Record<string, string> = {
  DRAFT: "DRAFT",
  REVIEW: "REVIEW",
  PUBLISHED: "PUBLISHED",
  ARCHIVED: "ARCHIVED",
};

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class AssetBackfillService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
  ) {}

  /**
   * Backfill ContentAsset records from Module roots.
   */
  async backfillModules(options: BackfillOptions): Promise<BackfillResult> {
    const result: BackfillResult = {
      totalProcessed: 0,
      succeeded: 0,
      failed: 0,
      skipped: 0,
      failures: [],
      mappings: [],
    };

    const batchSize = options.batchSize ?? 50;
    const skipExisting = options.skipExisting ?? true;
    let cursor: string | undefined;

    while (true) {
      const modules = await this.prisma.module.findMany({
        where: { tenantId: options.tenantId },
        take: batchSize,
        ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
        orderBy: { id: "asc" },
      });

      if (modules.length === 0) break;

      for (const mod of modules) {
        result.totalProcessed++;
        cursor = mod.id;

        try {
          // Check for existing migration
          if (skipExisting) {
            const existing = await this.prisma.contentAsset.findFirst({
              where: { legacyModuleId: mod.id },
            });
            if (existing) {
              result.skipped++;
              continue;
            }
          }

          const slug = slugify(mod.title);
          const assetStatus = MODULE_STATUS_TO_ASSET[mod.status] ?? "DRAFT";

          if (options.dryRun) {
            result.succeeded++;
            result.mappings.push({
              sourceType: "module",
              sourceId: mod.id,
              assetId: `dry-run-${mod.id}`,
              assetSlug: slug,
            });
            continue;
          }

          const asset = await this.prisma.contentAsset.create({
            data: {
              tenantId: mod.tenantId,
              slug,
              title: mod.title,
              assetType: "MODULE",
              domain: mod.domain,
              status: assetStatus,
              currentVersion: mod.version ?? 1,
              targetGrades: [],
              authorId: mod.authorId ?? "system",
              lastEditedBy: mod.updatedBy,
              publishedAt: mod.publishedAt,
              legacyModuleId: mod.id,
              riskLevel: "LOW",
            },
          });

          result.succeeded++;
          result.mappings.push({
            sourceType: "module",
            sourceId: mod.id,
            assetId: asset.id,
            assetSlug: slug,
          });

          this.logger.info(
            { moduleId: mod.id, assetId: asset.id },
            "Backfilled module to canonical asset",
          );
        } catch (err: unknown) {
          result.failed++;
          const message = err instanceof Error ? err.message : String(err);
          result.failures.push({
            sourceType: "module",
            sourceId: mod.id,
            reason: message,
          });
          this.logger.warn(
            { moduleId: mod.id, err: message },
            "Failed to backfill module",
          );
        }
      }
    }

    return result;
  }

  /**
   * Backfill ContentAsset records from LearningExperience roots.
   */
  async backfillExperiences(options: BackfillOptions): Promise<BackfillResult> {
    const result: BackfillResult = {
      totalProcessed: 0,
      succeeded: 0,
      failed: 0,
      skipped: 0,
      failures: [],
      mappings: [],
    };

    const batchSize = options.batchSize ?? 50;
    const skipExisting = options.skipExisting ?? true;
    let cursor: string | undefined;

    while (true) {
      const experiences = await (
        this.prisma
      ).learningExperience.findMany({
        where: { tenantId: options.tenantId },
        take: batchSize,
        ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
        orderBy: { id: "asc" },
        include: {
          claims: true,
          claimSimulations: { include: { simulationManifest: true } },
          claimAnimations: true,
        },
      });

      if (experiences.length === 0) break;

      for (const exp of experiences) {
        result.totalProcessed++;
        cursor = exp.id;

        try {
          if (skipExisting) {
            const existing = await this.prisma.contentAsset.findFirst({
              where: { legacyExperienceId: exp.id },
            });
            if (existing) {
              result.skipped++;
              continue;
            }
          }

          const slug = slugify(exp.title);
          const assetStatus = EXPERIENCE_STATUS_TO_ASSET[exp.status] ?? "DRAFT";

          if (options.dryRun) {
            result.succeeded++;
            result.mappings.push({
              sourceType: "experience",
              sourceId: exp.id,
              assetId: `dry-run-${exp.id}`,
              assetSlug: slug,
            });
            continue;
          }

          const asset = await this.prisma.contentAsset.create({
            data: {
              tenantId: exp.tenantId,
              slug,
              title: exp.title,
              assetType: "EXPLAINER",
              domain: exp.domain,
              conceptId: exp.conceptId,
              status: assetStatus,
              currentVersion: exp.version ?? 1,
              targetGrades: exp.targetGrades ?? [],
              qualityScore: exp.confidenceScore,
              authorId: exp.createdBy,
              lastEditedBy: exp.lastEditedBy,
              publishedAt: exp.publishedAt,
              promptHash: exp.promptHash,
              riskLevel: exp.riskLevel ?? "LOW",
              confidenceScore: exp.confidenceScore,
              legacyExperienceId: exp.id,
            },
          });

          // Create initial revision snapshot
          await this.prisma.contentAssetRevision.create({
            data: {
              assetId: asset.id,
              version: 1,
              changeNote: "Backfilled from LearningExperience",
              snapshot: {
                title: exp.title,
                domain: exp.domain,
                claims:
                  exp.claims?.map((c: Record<string, unknown>) => ({
                    claimRef: c.claimRef,
                    text: c.text,
                    bloomLevel: c.bloomLevel,
                  })) ?? [],
                intentProblem: exp.intentProblem,
                intentMotivation: exp.intentMotivation,
              },
              qualityScore: exp.confidenceScore,
              createdBy: exp.createdBy,
            },
          });

          // Backfill simulation manifests as ArtifactManifest
          for (const claimSim of exp.claimSimulations ?? []) {
            if (claimSim.simulationManifest) {
              await this.prisma.artifactManifest.create({
                data: {
                  assetId: asset.id,
                  manifestType: "SIMULATION",
                  version: claimSim.simulationManifest.version ?? "1.0.0",
                  claimRef: claimSim.claimRef,
                  manifest: claimSim.simulationManifest.manifest ?? {},
                  isValid: true,
                  generatedBy: "ai",
                },
              });
            }
          }

          // Backfill animations as ArtifactManifest
          for (const anim of exp.claimAnimations ?? []) {
            await this.prisma.artifactManifest.create({
              data: {
                assetId: asset.id,
                manifestType: "ANIMATION",
                version: "1.0.0",
                claimRef: anim.claimRef,
                manifest: {
                  title: anim.title,
                  description: anim.description,
                  type: anim.type,
                  duration: anim.duration,
                  config: anim.config,
                },
                isValid: true,
                generatedBy: "ai",
              },
            });
          }

          result.succeeded++;
          result.mappings.push({
            sourceType: "experience",
            sourceId: exp.id,
            assetId: asset.id,
            assetSlug: slug,
          });

          this.logger.info(
            { experienceId: exp.id, assetId: asset.id },
            "Backfilled experience to canonical asset",
          );
        } catch (err: unknown) {
          result.failed++;
          const message = err instanceof Error ? err.message : String(err);
          result.failures.push({
            sourceType: "experience",
            sourceId: exp.id,
            reason: message,
          });
          this.logger.warn(
            { experienceId: exp.id, err: message },
            "Failed to backfill experience",
          );
        }
      }
    }

    return result;
  }

  /**
   * Run a full backfill for both modules and experiences.
   */
  async backfillAll(options: BackfillOptions): Promise<{
    modules: BackfillResult;
    experiences: BackfillResult;
  }> {
    this.logger.info(
      { tenantId: options.tenantId, dryRun: options.dryRun ?? false },
      "Starting full asset backfill",
    );

    const modules = await this.backfillModules(options);
    const experiences = await this.backfillExperiences(options);

    this.logger.info(
      {
        modulesProcessed: modules.totalProcessed,
        modulesSucceeded: modules.succeeded,
        modulesFailed: modules.failed,
        experiencesProcessed: experiences.totalProcessed,
        experiencesSucceeded: experiences.succeeded,
        experiencesFailed: experiences.failed,
      },
      "Asset backfill complete",
    );

    return { modules, experiences };
  }

  /**
   * Generate a migration report without persisting anything.
   */
  async generateReport(tenantId: string): Promise<{
    modules: BackfillResult;
    experiences: BackfillResult;
  }> {
    return this.backfillAll({ tenantId, dryRun: true });
  }
}
