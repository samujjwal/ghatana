/**
 * Simulation Template Library Service
 *
 * Governs starter-backed and normalized-auto-backed template seeding,
 * cloning, review submission, approval, and deprecation without creating
 * a second template persistence model.
 *
 * @doc.type class
 * @doc.purpose Manage simulation template lifecycle over canonical manifests
 * @doc.layer product
 * @doc.pattern Service
 */

import { Prisma, type PrismaClient } from "@tutorputor/core/db";
import type { SimulationManifest } from "@tutorputor/contracts/v1/simulation/types";
import slugify from "slugify";
import {
  createSimulationStarterManifest,
  exportSimulationStarterPackage,
  listSimulationStarters,
  resolveSimulationStarterReference,
  validateManifest,
} from "@tutorputor/simulation/engine";
import {
  bootstrapCompatibleAutoPreset,
  exportCompatibleAutoPreset,
  listCompatibleAutoPresets,
  resolveCompatibleAutoPreset,
} from "@tutorputor/simulation/engine/auto/preset-compatibility";

type TemplateGovernanceStatus =
  | "draft"
  | "submitted"
  | "approved"
  | "rejected"
  | "deprecated";

type TemplateGovernanceSource =
  | "starter"
  | "auto_preset"
  | "clone"
  | "generated"
  | "refined";

interface TemplateGovernanceRecord {
  reviewStatus: TemplateGovernanceStatus;
  source: TemplateGovernanceSource;
  starterId?: string;
  autoPresetId?: string;
  parentTemplateId?: string;
  requestedBy?: string;
  requestedAt?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewerNotes?: string;
  deprecationReason?: string;
}

interface TemplateListOptions {
  domain?: string;
  status?: string;
  q?: string;
}

interface CreateTemplateInput {
  title?: string;
  description?: string;
  difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
  tags?: string[];
  conceptId?: string;
  moduleId?: string;
}

interface CreateTemplateFromManifestInput extends CreateTemplateInput {
  title?: string;
  description?: string;
}

interface BulkTemplateActionResult<T = unknown> {
  processed: number;
  items: T[];
}

interface SimulationTemplateCoverageSummary {
  starters: {
    total: number;
    covered: number;
    uncovered: number;
    byDomain: Record<
      string,
      { total: number; covered: number; uncovered: number }
    >;
    byAudience: Record<
      string,
      { total: number; covered: number; uncovered: number }
    >;
  };
  autoPresets: {
    total: number;
    covered: number;
    uncovered: number;
    byDomain: Record<
      string,
      { total: number; covered: number; uncovered: number }
    >;
    bySource: Record<
      string,
      { total: number; covered: number; uncovered: number }
    >;
  };
}

interface LegacyAutoRetirementPlan {
  totalLegacyAutoPresets: number;
  readyToRetire: number;
  awaitingPublish: number;
  needsTemplate: number;
  items: Array<{
    presetId: string;
    name: string;
    domain: string;
    status: "ready_to_retire" | "awaiting_publish" | "needs_template";
    templateId?: string;
    templateStatus?: string;
  }>;
}

interface CoverageBacklogExecutionSummary {
  createdStarterTemplates: number;
  createdLegacyAutoTemplates: number;
  submittedForReview: number;
  published: number;
  skippedCuratedAutoAliases: number;
  templateIds: string[];
}

import type { SimulationTemplateWithManifest } from "../content/types.js";

interface TemplateCoverageActionPlan {
  starters: Array<{
    starterId: string;
    name: string;
    domain: string;
    audience: string;
    priority: number;
    recommendedAction: "seed_template" | "seed_and_submit";
  }>;
  legacyAutoPresets: Array<{
    presetId: string;
    name: string;
    domain: string;
    priority: number;
    recommendedAction: "create_template" | "create_and_publish";
  }>;
}

interface TemplateCoverageActionPlanExecutionSummary {
  seededStarterTemplates: number;
  submittedStarterTemplates: number;
  createdLegacyTemplates: number;
  publishedLegacyTemplates: number;
  templateIds: string[];
}

interface TemplateCoverageCampaignPlan {
  generatedAt: string;
  phases: Array<{
    phase: "starter_foundation" | "review_ready_starters" | "legacy_retirement";
    actions: number;
    domains: string[];
    audiences: string[];
  }>;
}

interface TemplateCoverageCampaignExecutionSummary {
  generatedAt: string;
  processedPhases: string[];
  seededStarterTemplates: number;
  submittedStarterTemplates: number;
  createdLegacyTemplates: number;
  publishedLegacyTemplates: number;
  templateIds: string[];
}

interface DomainCatalogBacklog {
  domain: string;
  audience?: "k12" | "undergraduate" | "graduate" | "professional";
  uncoveredStarters: Array<{
    starterId: string;
    name: string;
    audience: string;
  }>;
  legacyRuntimePresetsNeedingTemplates: Array<{
    presetId: string;
    name: string;
    status: "ready_to_retire" | "awaiting_publish" | "needs_template";
  }>;
  totalActions: number;
}

interface DomainCatalogSeedSummary {
  domain: string;
  audience?: "k12" | "undergraduate" | "graduate" | "professional";
  seededStarterTemplates: number;
  submittedStarterTemplates: number;
  createdLegacyTemplates: number;
  submittedLegacyTemplates: number;
  publishedLegacyTemplates: number;
  templateIds: string[];
}

interface CatalogProgressMatrix {
  generatedAt: string;
  domains: DomainCatalogBacklog[];
  totals: {
    uncoveredStarters: number;
    legacyRuntimePresetsNeedingTemplates: number;
    totalActions: number;
  };
}

interface MultiDomainCatalogSeedSummary {
  generatedAt: string;
  processedDomains: string[];
  totalSeededStarterTemplates: number;
  totalSubmittedStarterTemplates: number;
  totalCreatedLegacyTemplates: number;
  totalSubmittedLegacyTemplates: number;
  totalPublishedLegacyTemplates: number;
  templateIds: string[];
  domains: DomainCatalogSeedSummary[];
}

interface LegacyAutoRetirementExecutionSummary {
  createdTemplates: number;
  submittedForReview: number;
  published: number;
  failed: Array<{
    presetId: string;
    reason: string;
  }>;
  templateIds: string[];
  remainingPlan: LegacyAutoRetirementPlan;
}

function normalizeGovernance(
  value: unknown,
  fallback: TemplateGovernanceRecord,
): TemplateGovernanceRecord {
  if (!value || typeof value !== "object") {
    return fallback;
  }

  const record = value as Record<string, unknown>;
  const reviewStatus =
    typeof record.reviewStatus === "string"
      ? record.reviewStatus
      : fallback.reviewStatus;
  const source =
    typeof record.source === "string" ? record.source : fallback.source;

  return {
    reviewStatus: reviewStatus as TemplateGovernanceStatus,
    source: source as TemplateGovernanceRecord["source"],
    ...(typeof record.starterId === "string"
      ? { starterId: record.starterId }
      : {}),
    ...(typeof record.autoPresetId === "string"
      ? { autoPresetId: record.autoPresetId }
      : {}),
    ...(typeof record.parentTemplateId === "string"
      ? { parentTemplateId: record.parentTemplateId }
      : {}),
    ...(typeof record.requestedBy === "string"
      ? { requestedBy: record.requestedBy }
      : {}),
    ...(typeof record.requestedAt === "string"
      ? { requestedAt: record.requestedAt }
      : {}),
    ...(typeof record.reviewedBy === "string"
      ? { reviewedBy: record.reviewedBy }
      : {}),
    ...(typeof record.reviewedAt === "string"
      ? { reviewedAt: record.reviewedAt }
      : {}),
    ...(typeof record.reviewerNotes === "string"
      ? { reviewerNotes: record.reviewerNotes }
      : {}),
    ...(typeof record.deprecationReason === "string"
      ? { deprecationReason: record.deprecationReason }
      : {}),
  };
}

function withGovernance(
  manifest: Record<string, unknown>,
  governance: TemplateGovernanceRecord,
): Record<string, unknown> {
  return {
    ...manifest,
    templateGovernance: governance,
  };
}

async function ensureUniqueTemplateSlug(
  prisma: PrismaClient,
  tenantId: string,
  baseTitle: string,
): Promise<string> {
  const baseSlug =
    slugify(baseTitle, { lower: true, strict: true }) || "simulation-template";
  let candidate = baseSlug;
  let suffix = 1;

  for (;;) {
    const existing = await prisma.simulationTemplate.findFirst({
      where: { tenantId, slug: candidate },
      select: { id: true },
    });
    if (!existing) {
      return candidate;
    }
    suffix += 1;
    candidate = `${baseSlug}-${suffix}`;
  }
}

export class SimulationTemplateLibraryService {
  constructor(private readonly prisma: PrismaClient) {}

  async createTemplateFromGeneratedManifest(
    tenantId: string,
    userId: string,
    manifest: SimulationManifest,
    input: CreateTemplateFromManifestInput = {},
  ) {
    const normalizedManifest = structuredClone(manifest);
    normalizedManifest.tenantId = tenantId as never;
    normalizedManifest.authorId = userId as never;
    normalizedManifest.updatedAt = new Date().toISOString();
    if (input.title) {
      normalizedManifest.title = input.title;
    }
    if (input.description) {
      normalizedManifest.description = input.description;
    }

    const governance: TemplateGovernanceRecord = {
      reviewStatus: "draft",
      source: "generated",
      requestedBy: userId,
      requestedAt: new Date().toISOString(),
    };
    const slug = await ensureUniqueTemplateSlug(
      this.prisma,
      tenantId,
      input.title ?? normalizedManifest.title,
    );

    return this.prisma.$transaction(async (tx) => {
      const manifestRecord = await tx.simulationManifest.create({
        data: {
          id: normalizedManifest.id,
          tenantId,
          title: input.title ?? normalizedManifest.title,
          description:
            input.description ?? normalizedManifest.description ?? null,
          version: normalizedManifest.version,
          domain: normalizedManifest.domain,
          manifest: toInputJsonValue(
            withGovernance(
              normalizedManifest as unknown as Record<string, unknown>,
              governance,
            ),
          ),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
      });

      return tx.simulationTemplate.create({
        data: {
          tenantId,
          slug,
          title: input.title ?? normalizedManifest.title,
          description:
            input.description ??
            normalizedManifest.description ??
            "AI-generated simulation template",
          domain: normalizedManifest.domain,
          difficulty: input.difficulty ?? "INTERMEDIATE",
          tags: JSON.stringify(input.tags ?? ["ai-generated"]),
          license: "FREE",
          isPremium: false,
          isVerified: false,
          version: normalizedManifest.version,
          authorId: userId,
          status: "DRAFT",
          manifestId: manifestRecord.id,
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
        include: { manifest: true },
      });
    });
  }

  async reviseTemplateFromManifest(
    tenantId: string,
    userId: string,
    templateId: string,
    manifest: SimulationManifest,
    input: {
      changeNote?: string;
      description?: string;
      title?: string;
    } = {},
  ) {
    const template = await this.getTemplateById(tenantId, templateId);
    if (!template.manifestId || !template.manifest) {
      throw new Error("Template manifest not found");
    }

    const currentManifestPayload = template.manifest.manifest as Record<
      string,
      unknown
    >;
    const currentGovernance = normalizeGovernance(
      currentManifestPayload.templateGovernance,
      {
        reviewStatus: "draft",
        source: "generated",
      },
    );
    const nextVersion = incrementSemver(template.version);
    const nextManifest = structuredClone(manifest);
    nextManifest.id = template.manifestId as never;
    nextManifest.tenantId = tenantId as never;
    nextManifest.authorId = userId as never;
    nextManifest.version = nextVersion;
    nextManifest.updatedAt = new Date().toISOString();
    if (input.title) {
      nextManifest.title = input.title;
    }
    if (input.description) {
      nextManifest.description = input.description;
    }

    const nextGovernance: TemplateGovernanceRecord = {
      ...currentGovernance,
      reviewStatus: "draft",
      source: "refined",
      ...(input.changeNote
        ? { reviewerNotes: input.changeNote }
        : currentGovernance.reviewerNotes
          ? { reviewerNotes: currentGovernance.reviewerNotes }
          : {}),
    };
    delete nextGovernance.reviewedBy;
    delete nextGovernance.reviewedAt;

    return this.prisma.$transaction(async (tx) => {
      await tx.simulationManifestVersion.create({
        data: {
          manifestId: template.manifestId!,
          version: template.version,
          manifestJson: toInputJsonValue(currentManifestPayload),
          safetyConstraints: Prisma.JsonNull,
          pedagogicalTags: Prisma.JsonNull,
          instrumentationPlan: Prisma.JsonNull,
          status: "DRAFT",
          createdBy: userId,
        },
      });

      await tx.simulationManifest.update({
        where: { id: template.manifestId! },
        data: {
          title: input.title ?? nextManifest.title,
          description: input.description ?? nextManifest.description ?? null,
          version: nextVersion,
          domain: nextManifest.domain,
          manifest: toInputJsonValue(
            withGovernance(
              nextManifest as unknown as Record<string, unknown>,
              nextGovernance,
            ),
          ),
        },
      });

      return tx.simulationTemplate.update({
        where: { id: template.id },
        data: {
          title: input.title ?? template.title,
          description: input.description ?? template.description,
          version: nextVersion,
          status: "DRAFT",
          isVerified: false,
          publishedAt: null,
        },
        include: { manifest: true },
      });
    });
  }

  async listTemplates(tenantId: string, options: TemplateListOptions = {}) {
    const where: Record<string, unknown> = { tenantId };
    if (options.domain) where.domain = options.domain;
    if (options.status) where.status = options.status;
    if (options.q?.trim()) {
      const query = options.q.trim();
      where.OR = [
        { title: { contains: query, mode: "insensitive" } },
        { description: { contains: query, mode: "insensitive" } },
      ];
    }

    return this.prisma.simulationTemplate.findMany({
      where,
      orderBy: { updatedAt: "desc" },
      include: {
        manifest: {
          select: {
            id: true,
            manifest: true,
          },
        },
      },
    });
  }

  async getTemplateSummary(tenantId: string) {
    const [total, published, verified, byDomain] = await Promise.all([
      this.prisma.simulationTemplate.count({ where: { tenantId } }),
      this.prisma.simulationTemplate.count({
        where: { tenantId, status: "PUBLISHED" },
      }),
      this.prisma.simulationTemplate.count({
        where: { tenantId, isVerified: true },
      }),
      this.prisma.simulationTemplate.groupBy({
        by: ["domain"],
        where: { tenantId },
        _count: { id: true },
      }),
    ]);

    return {
      total,
      published,
      verified,
      byDomain: byDomain.reduce<Record<string, number>>((acc, item) => {
        acc[item.domain] = item._count.id;
        return acc;
      }, {}),
    };
  }

  async getStarterCoverage(tenantId: string) {
    const templates = await this.prisma.simulationTemplate.findMany({
      where: { tenantId },
      include: {
        manifest: {
          select: {
            manifest: true,
          },
        },
      },
    });

    const coveredStarterIds = new Set<string>();
    for (const template of templates) {
      const governance = normalizeGovernance(
        (template.manifest?.manifest as Record<string, unknown> | undefined)
          ?.templateGovernance,
        { reviewStatus: "draft", source: "starter" },
      );
      if (governance.starterId) {
        coveredStarterIds.add(governance.starterId);
      }
    }

    const starters = listSimulationStarters();
    const uncovered = starters.filter(
      (starter) => !coveredStarterIds.has(starter.id),
    );

    return {
      totalStarters: starters.length,
      coveredStarters: coveredStarterIds.size,
      uncoveredStarterIds: uncovered.map((starter) => starter.id),
      uncoveredStarters: uncovered.map((starter) => ({
        id: starter.id,
        name: starter.name,
        domain: starter.domain,
        difficulty: starter.difficulty,
        audience: starter.audience,
      })),
    };
  }

  async getAutoPresetCoverage(tenantId: string) {
    const templates = await this.prisma.simulationTemplate.findMany({
      where: { tenantId },
      include: {
        manifest: {
          select: {
            manifest: true,
          },
        },
      },
    });

    const coveredPresetIds = new Set<string>();
    for (const template of templates) {
      const governance = normalizeGovernance(
        (template.manifest?.manifest as Record<string, unknown> | undefined)
          ?.templateGovernance,
        { reviewStatus: "draft", source: "starter" },
      );
      if (governance.autoPresetId) {
        coveredPresetIds.add(governance.autoPresetId);
      }
    }

    const presets = listCompatibleAutoPresets();
    const uncovered = presets.filter(
      (preset) => !coveredPresetIds.has(preset.id),
    );

    return {
      totalAutoPresets: presets.length,
      coveredAutoPresets: coveredPresetIds.size,
      uncoveredAutoPresetIds: uncovered.map((preset) => preset.id),
      uncoveredAutoPresets: uncovered.map((preset) => ({
        id: preset.id,
        name: preset.name,
        domain: preset.domain,
        source: preset.source,
        starterId: preset.starterId ?? null,
        bootstrapSupported: preset.bootstrapSupported,
      })),
    };
  }

  async getCoverageSummary(
    tenantId: string,
  ): Promise<SimulationTemplateCoverageSummary> {
    const [starterCoverage, autoCoverage] = await Promise.all([
      this.getStarterCoverage(tenantId),
      this.getAutoPresetCoverage(tenantId),
    ]);
    const allStarters = listSimulationStarters();
    const allAutoPresets = listCompatibleAutoPresets();
    const uncoveredStarterIds = new Set(starterCoverage.uncoveredStarterIds);
    const uncoveredAutoPresetIds = new Set(autoCoverage.uncoveredAutoPresetIds);

    const starterByDomain = allStarters.reduce<
      Record<string, { total: number; covered: number; uncovered: number }>
    >((acc, starter) => {
      const bucket = acc[starter.domain] ?? {
        total: 0,
        covered: 0,
        uncovered: 0,
      };
      bucket.total += 1;
      if (uncoveredStarterIds.has(starter.id)) {
        bucket.uncovered += 1;
      } else {
        bucket.covered += 1;
      }
      acc[starter.domain] = bucket;
      return acc;
    }, {});

    const starterByAudience = allStarters.reduce<
      Record<string, { total: number; covered: number; uncovered: number }>
    >((acc, starter) => {
      const bucket = acc[starter.audience] ?? {
        total: 0,
        covered: 0,
        uncovered: 0,
      };
      bucket.total += 1;
      if (uncoveredStarterIds.has(starter.id)) {
        bucket.uncovered += 1;
      } else {
        bucket.covered += 1;
      }
      acc[starter.audience] = bucket;
      return acc;
    }, {});

    const autoByDomain = allAutoPresets.reduce<
      Record<string, { total: number; covered: number; uncovered: number }>
    >((acc, preset) => {
      const bucket = acc[preset.domain] ?? {
        total: 0,
        covered: 0,
        uncovered: 0,
      };
      bucket.total += 1;
      if (uncoveredAutoPresetIds.has(preset.id)) {
        bucket.uncovered += 1;
      } else {
        bucket.covered += 1;
      }
      acc[preset.domain] = bucket;
      return acc;
    }, {});

    const autoBySource = allAutoPresets.reduce<
      Record<string, { total: number; covered: number; uncovered: number }>
    >((acc, preset) => {
      const bucket = acc[preset.source] ?? {
        total: 0,
        covered: 0,
        uncovered: 0,
      };
      bucket.total += 1;
      if (uncoveredAutoPresetIds.has(preset.id)) {
        bucket.uncovered += 1;
      } else {
        bucket.covered += 1;
      }
      acc[preset.source] = bucket;
      return acc;
    }, {});

    return {
      starters: {
        total: allStarters.length,
        covered: starterCoverage.coveredStarters,
        uncovered: starterCoverage.uncoveredStarterIds.length,
        byDomain: starterByDomain,
        byAudience: starterByAudience,
      },
      autoPresets: {
        total: allAutoPresets.length,
        covered: autoCoverage.coveredAutoPresets,
        uncovered: autoCoverage.uncoveredAutoPresetIds.length,
        byDomain: autoByDomain,
        bySource: autoBySource,
      },
    };
  }

  async getCoverageActionPlan(
    tenantId: string,
    input: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      limit?: number;
    } = {},
  ): Promise<TemplateCoverageActionPlan> {
    const [starterCoverage, retirementPlan] = await Promise.all([
      this.getStarterCoverage(tenantId),
      this.getLegacyAutoRetirementPlan(tenantId),
    ]);

    const starters = starterCoverage.uncoveredStarters
      .filter((starter) =>
        input.domain ? starter.domain === input.domain : true,
      )
      .filter((starter) =>
        input.audience ? starter.audience === input.audience : true,
      )
      .map((starter) => ({
        starterId: starter.id,
        name: starter.name,
        domain: starter.domain,
        audience: starter.audience,
        priority:
          starter.audience === "k12"
            ? 90
            : starter.audience === "undergraduate"
              ? 75
              : 65,
        recommendedAction:
          starter.audience === "k12"
            ? ("seed_and_submit" as const)
            : ("seed_template" as const),
      }))
      .sort((left, right) => right.priority - left.priority)
      .slice(0, input.limit ?? starterCoverage.uncoveredStarterIds.length);

    const legacyAutoPresets = retirementPlan.items
      .filter((item) => (input.domain ? item.domain === input.domain : true))
      .map((item) => ({
        presetId: item.presetId,
        name: item.name,
        domain: item.domain,
        priority:
          item.status === "needs_template"
            ? 95
            : item.status === "awaiting_publish"
              ? 80
              : 50,
        recommendedAction:
          item.status === "awaiting_publish"
            ? ("create_and_publish" as const)
            : ("create_template" as const),
      }))
      .sort((left, right) => right.priority - left.priority)
      .slice(0, input.limit ?? retirementPlan.items.length);

    return {
      starters,
      legacyAutoPresets,
    };
  }

  async executeCoverageActionPlan(
    tenantId: string,
    userId: string,
    input: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      limit?: number;
    } = {},
  ): Promise<TemplateCoverageActionPlanExecutionSummary> {
    const plan = await this.getCoverageActionPlan(tenantId, input);
    const limit =
      input.limit ??
      Math.max(plan.starters.length, plan.legacyAutoPresets.length);
    const templateIds: string[] = [];
    let seededStarterTemplates = 0;
    let submittedStarterTemplates = 0;
    let createdLegacyTemplates = 0;
    let publishedLegacyTemplates = 0;

    for (const starter of plan.starters.slice(0, limit)) {
      const created = await this.createTemplateFromStarter(
        tenantId,
        userId,
        starter.starterId,
      );
      templateIds.push(created.id);
      seededStarterTemplates++;

      if (starter.recommendedAction === "seed_and_submit") {
        await this.submitTemplateForReview(
          tenantId,
          created.id,
          userId,
          "Coverage action-plan execution",
        );
        submittedStarterTemplates++;
      }
    }

    for (const preset of plan.legacyAutoPresets.slice(0, limit)) {
      const created = await this.createTemplateFromAutoPreset(
        tenantId,
        userId,
        preset.presetId,
      );
      templateIds.push(created.id);
      createdLegacyTemplates++;

      if (preset.recommendedAction === "create_and_publish") {
        await this.publishTemplate(tenantId, created.id);
        publishedLegacyTemplates++;
      }
    }

    return {
      seededStarterTemplates,
      submittedStarterTemplates,
      createdLegacyTemplates,
      publishedLegacyTemplates,
      templateIds,
    };
  }

  async getCoverageCampaignPlan(
    tenantId: string,
    input: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      limitPerPhase?: number;
    } = {},
  ): Promise<TemplateCoverageCampaignPlan> {
    const plan = await this.getCoverageActionPlan(tenantId, {
      ...(input.domain ? { domain: input.domain } : {}),
      ...(input.audience ? { audience: input.audience } : {}),
      ...(input.limitPerPhase ? { limit: input.limitPerPhase } : {}),
    });

    const starterFoundation = plan.starters.filter(
      (starter) => starter.recommendedAction === "seed_template",
    );
    const reviewReadyStarters = plan.starters.filter(
      (starter) => starter.recommendedAction === "seed_and_submit",
    );

    return {
      generatedAt: new Date().toISOString(),
      phases: [
        {
          phase: "starter_foundation",
          actions: starterFoundation.length,
          domains: [
            ...new Set(starterFoundation.map((starter) => starter.domain)),
          ],
          audiences: [
            ...new Set(starterFoundation.map((starter) => starter.audience)),
          ],
        },
        {
          phase: "review_ready_starters",
          actions: reviewReadyStarters.length,
          domains: [
            ...new Set(reviewReadyStarters.map((starter) => starter.domain)),
          ],
          audiences: [
            ...new Set(reviewReadyStarters.map((starter) => starter.audience)),
          ],
        },
        {
          phase: "legacy_retirement",
          actions: plan.legacyAutoPresets.length,
          domains: [
            ...new Set(plan.legacyAutoPresets.map((preset) => preset.domain)),
          ],
          audiences: [],
        },
      ],
    };
  }

  async getDomainCatalogBacklog(
    tenantId: string,
    input: {
      domain: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
    },
  ): Promise<DomainCatalogBacklog> {
    const [starterCoverage, retirementPlan] = await Promise.all([
      this.getStarterCoverage(tenantId),
      this.getLegacyAutoRetirementPlan(tenantId),
    ]);

    const uncoveredStarters = starterCoverage.uncoveredStarters
      .filter((starter) => starter.domain === input.domain)
      .filter((starter) =>
        input.audience ? starter.audience === input.audience : true,
      )
      .map((starter) => ({
        starterId: starter.id,
        name: starter.name,
        audience: starter.audience,
      }));

    const legacyRuntimePresetsNeedingTemplates = retirementPlan.items
      .filter((item) => item.domain === input.domain.toLowerCase())
      .filter((item) => item.status !== "ready_to_retire")
      .map((item) => ({
        presetId: item.presetId,
        name: item.name,
        status: item.status,
      }));

    return {
      domain: input.domain,
      ...(input.audience ? { audience: input.audience } : {}),
      uncoveredStarters,
      legacyRuntimePresetsNeedingTemplates,
      totalActions:
        uncoveredStarters.length + legacyRuntimePresetsNeedingTemplates.length,
    };
  }

  async getCatalogProgressMatrix(
    tenantId: string,
    input: {
      domains?: string[];
      audiences?: Array<"k12" | "undergraduate" | "graduate" | "professional">;
    } = {},
  ): Promise<CatalogProgressMatrix> {
    const domains = input.domains ?? [
      "PHYSICS",
      "CHEMISTRY",
      "BIOLOGY",
      "MEDICINE",
      "CS_DISCRETE",
      "MATHEMATICS",
    ];
    const audiences = input.audiences?.length ? input.audiences : [undefined];
    const rows: DomainCatalogBacklog[] = [];

    for (const domain of domains) {
      for (const audience of audiences) {
        rows.push(
          await this.getDomainCatalogBacklog(tenantId, {
            domain,
            ...(audience ? { audience } : {}),
          }),
        );
      }
    }

    return {
      generatedAt: new Date().toISOString(),
      domains: rows,
      totals: {
        uncoveredStarters: rows.reduce(
          (sum, row) => sum + row.uncoveredStarters.length,
          0,
        ),
        legacyRuntimePresetsNeedingTemplates: rows.reduce(
          (sum, row) => sum + row.legacyRuntimePresetsNeedingTemplates.length,
          0,
        ),
        totalActions: rows.reduce((sum, row) => sum + row.totalActions, 0),
      },
    };
  }

  async seedDomainCatalogCoverage(
    tenantId: string,
    userId: string,
    input: {
      domain: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      autoSubmitStarters?: boolean;
      autoSubmitLegacy?: boolean;
      autoPublishLegacy?: boolean;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
      limit?: number;
    },
  ): Promise<DomainCatalogSeedSummary> {
    const backlog = await this.getDomainCatalogBacklog(tenantId, {
      domain: input.domain,
      ...(input.audience ? { audience: input.audience } : {}),
    });
    const templateIds: string[] = [];
    let seededStarterTemplates = 0;
    let submittedStarterTemplates = 0;
    let createdLegacyTemplates = 0;
    let submittedLegacyTemplates = 0;
    let publishedLegacyTemplates = 0;

    const starters = backlog.uncoveredStarters.slice(
      0,
      input.limit ?? backlog.uncoveredStarters.length,
    );
    for (const starter of starters) {
      const created = await this.createTemplateFromStarter(
        tenantId,
        userId,
        starter.starterId,
        {
          ...(input.difficulty ? { difficulty: input.difficulty } : {}),
          ...(input.tags ? { tags: input.tags } : {}),
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
      );
      templateIds.push(created.id);
      seededStarterTemplates++;

      if (input.autoSubmitStarters) {
        await this.submitTemplateForReview(
          tenantId,
          created.id,
          userId,
          "Domain catalog starter seeding",
        );
        submittedStarterTemplates++;
      }
    }

    const legacyPresets = backlog.legacyRuntimePresetsNeedingTemplates.slice(
      0,
      input.limit ?? backlog.legacyRuntimePresetsNeedingTemplates.length,
    );
    for (const preset of legacyPresets) {
      const created = await this.createTemplateFromAutoPreset(
        tenantId,
        userId,
        preset.presetId,
        {
          ...(input.difficulty ? { difficulty: input.difficulty } : {}),
          ...(input.tags ? { tags: input.tags } : {}),
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
      );
      templateIds.push(created.id);
      createdLegacyTemplates++;

      if (input.autoSubmitLegacy) {
        await this.submitTemplateForReview(
          tenantId,
          created.id,
          userId,
          "Domain catalog legacy-runtime seeding",
        );
        submittedLegacyTemplates++;
      }
      if (input.autoPublishLegacy || preset.status === "awaiting_publish") {
        await this.publishTemplate(tenantId, created.id);
        publishedLegacyTemplates++;
      }
    }

    return {
      domain: input.domain,
      ...(input.audience ? { audience: input.audience } : {}),
      seededStarterTemplates,
      submittedStarterTemplates,
      createdLegacyTemplates,
      submittedLegacyTemplates,
      publishedLegacyTemplates,
      templateIds,
    };
  }

  async seedCatalogProgressMatrix(
    tenantId: string,
    userId: string,
    input: {
      domains?: string[];
      audiences?: Array<"k12" | "undergraduate" | "graduate" | "professional">;
      autoSubmitStarters?: boolean;
      autoSubmitLegacy?: boolean;
      autoPublishLegacy?: boolean;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
      limitPerDomain?: number;
    } = {},
  ): Promise<MultiDomainCatalogSeedSummary> {
    const domains = input.domains ?? [
      "PHYSICS",
      "CHEMISTRY",
      "BIOLOGY",
      "MEDICINE",
      "CS_DISCRETE",
      "MATHEMATICS",
    ];
    const audiences = input.audiences?.length ? input.audiences : [undefined];
    const results: DomainCatalogSeedSummary[] = [];
    const templateIds: string[] = [];

    for (const domain of domains) {
      for (const audience of audiences) {
        const result = await this.seedDomainCatalogCoverage(tenantId, userId, {
          domain,
          ...(audience ? { audience } : {}),
          ...(input.autoSubmitStarters !== undefined
            ? { autoSubmitStarters: input.autoSubmitStarters }
            : {}),
          ...(input.autoSubmitLegacy !== undefined
            ? { autoSubmitLegacy: input.autoSubmitLegacy }
            : {}),
          ...(input.autoPublishLegacy !== undefined
            ? { autoPublishLegacy: input.autoPublishLegacy }
            : {}),
          ...(input.difficulty ? { difficulty: input.difficulty } : {}),
          ...(input.tags ? { tags: input.tags } : {}),
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
          ...(input.limitPerDomain !== undefined
            ? { limit: input.limitPerDomain }
            : {}),
        });
        results.push(result);
        templateIds.push(...result.templateIds);
      }
    }

    return {
      generatedAt: new Date().toISOString(),
      processedDomains: results.map((result) =>
        result.audience ? `${result.domain}:${result.audience}` : result.domain,
      ),
      totalSeededStarterTemplates: results.reduce(
        (sum, result) => sum + result.seededStarterTemplates,
        0,
      ),
      totalSubmittedStarterTemplates: results.reduce(
        (sum, result) => sum + result.submittedStarterTemplates,
        0,
      ),
      totalCreatedLegacyTemplates: results.reduce(
        (sum, result) => sum + result.createdLegacyTemplates,
        0,
      ),
      totalSubmittedLegacyTemplates: results.reduce(
        (sum, result) => sum + result.submittedLegacyTemplates,
        0,
      ),
      totalPublishedLegacyTemplates: results.reduce(
        (sum, result) => sum + result.publishedLegacyTemplates,
        0,
      ),
      templateIds,
      domains: results,
    };
  }

  async executeCoverageCampaign(
    tenantId: string,
    userId: string,
    input: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      phases?: Array<
        "starter_foundation" | "review_ready_starters" | "legacy_retirement"
      >;
      limitPerPhase?: number;
    } = {},
  ): Promise<TemplateCoverageCampaignExecutionSummary> {
    const phases = new Set(
      input.phases ?? [
        "starter_foundation",
        "review_ready_starters",
        "legacy_retirement",
      ],
    );
    const plan = await this.getCoverageActionPlan(tenantId, {
      ...(input.domain ? { domain: input.domain } : {}),
      ...(input.audience ? { audience: input.audience } : {}),
      ...(input.limitPerPhase ? { limit: input.limitPerPhase } : {}),
    });
    const templateIds: string[] = [];
    let seededStarterTemplates = 0;
    let submittedStarterTemplates = 0;
    let createdLegacyTemplates = 0;
    let publishedLegacyTemplates = 0;

    if (phases.has("starter_foundation")) {
      for (const starter of plan.starters.filter(
        (item) => item.recommendedAction === "seed_template",
      )) {
        const created = await this.createTemplateFromStarter(
          tenantId,
          userId,
          starter.starterId,
        );
        templateIds.push(created.id);
        seededStarterTemplates++;
      }
    }

    if (phases.has("review_ready_starters")) {
      for (const starter of plan.starters.filter(
        (item) => item.recommendedAction === "seed_and_submit",
      )) {
        const created = await this.createTemplateFromStarter(
          tenantId,
          userId,
          starter.starterId,
        );
        templateIds.push(created.id);
        seededStarterTemplates++;
        await this.submitTemplateForReview(
          tenantId,
          created.id,
          userId,
          "Coverage campaign review-ready starter execution",
        );
        submittedStarterTemplates++;
      }
    }

    if (phases.has("legacy_retirement")) {
      for (const preset of plan.legacyAutoPresets) {
        const created = await this.createTemplateFromAutoPreset(
          tenantId,
          userId,
          preset.presetId,
        );
        templateIds.push(created.id);
        createdLegacyTemplates++;
        if (preset.recommendedAction === "create_and_publish") {
          await this.publishTemplate(tenantId, created.id);
          publishedLegacyTemplates++;
        }
      }
    }

    return {
      generatedAt: new Date().toISOString(),
      processedPhases: [...phases],
      seededStarterTemplates,
      submittedStarterTemplates,
      createdLegacyTemplates,
      publishedLegacyTemplates,
      templateIds,
    };
  }

  async getLegacyAutoRetirementPlan(
    tenantId: string,
  ): Promise<LegacyAutoRetirementPlan> {
    const [legacyAutoPresets, templates] = await Promise.all([
      Promise.resolve(listCompatibleAutoPresets({ source: "legacy_auto" })),
      this.prisma.simulationTemplate.findMany({
        where: { tenantId },
        include: {
          manifest: {
            select: {
              manifest: true,
            },
          },
        },
      }),
    ]);

    const items = legacyAutoPresets.map((preset) => {
      const matchingTemplate = templates.find((template) => {
        const governance = normalizeGovernance(
          (template.manifest?.manifest as Record<string, unknown> | undefined)
            ?.templateGovernance,
          { reviewStatus: "draft", source: "starter" },
        );
        return governance.autoPresetId === preset.id;
      });

      const status = !matchingTemplate
        ? ("needs_template" as const)
        : matchingTemplate.status === "PUBLISHED"
          ? ("ready_to_retire" as const)
          : ("awaiting_publish" as const);

      return {
        presetId: preset.id,
        name: preset.name,
        domain: preset.domain,
        status,
        ...(matchingTemplate ? { templateId: matchingTemplate.id } : {}),
        ...(matchingTemplate
          ? { templateStatus: matchingTemplate.status }
          : {}),
      };
    });

    return {
      totalLegacyAutoPresets: items.length,
      readyToRetire: items.filter((item) => item.status === "ready_to_retire")
        .length,
      awaitingPublish: items.filter(
        (item) => item.status === "awaiting_publish",
      ).length,
      needsTemplate: items.filter((item) => item.status === "needs_template")
        .length,
      items,
    };
  }

  async seedMissingStarterTemplates(
    tenantId: string,
    userId: string,
    input: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
      limit?: number;
    } = {},
  ) {
    const coverage = await this.getStarterCoverage(tenantId);
    const starterIds = coverage.uncoveredStarters
      .filter((starter) =>
        input.domain ? starter.domain === input.domain : true,
      )
      .filter((starter) =>
        input.audience ? starter.audience === input.audience : true,
      )
      .map((starter) => starter.id)
      .slice(0, input.limit ?? coverage.uncoveredStarterIds.length);

    return this.bulkCreateTemplatesFromStarters(tenantId, userId, {
      starterIds,
      ...(input.difficulty ? { difficulty: input.difficulty } : {}),
      ...(input.tags ? { tags: input.tags } : {}),
      ...(input.conceptId ? { conceptId: input.conceptId } : {}),
      ...(input.moduleId ? { moduleId: input.moduleId } : {}),
    });
  }

  async seedMissingAutoPresetTemplates(
    tenantId: string,
    userId: string,
    input: {
      domain?: string;
      source?: "legacy_auto" | "curated_starter";
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
      limit?: number;
    } = {},
  ) {
    const coverage = await this.getAutoPresetCoverage(tenantId);
    const presetIds = coverage.uncoveredAutoPresets
      .filter((preset) =>
        input.domain ? preset.domain === input.domain : true,
      )
      .filter((preset) =>
        input.source ? preset.source === input.source : true,
      )
      .map((preset) => preset.id)
      .slice(0, input.limit ?? coverage.uncoveredAutoPresetIds.length);

    return this.bulkCreateTemplatesFromAutoPresets(tenantId, userId, {
      presetIds,
      ...(input.difficulty ? { difficulty: input.difficulty } : {}),
      ...(input.tags ? { tags: input.tags } : {}),
      ...(input.conceptId ? { conceptId: input.conceptId } : {}),
      ...(input.moduleId ? { moduleId: input.moduleId } : {}),
    });
  }

  async seedCoverageBacklog(
    tenantId: string,
    userId: string,
    input: {
      domain?: string;
      audience?: "k12" | "undergraduate" | "graduate" | "professional";
      includeStarters?: boolean;
      includeLegacyAutoPresets?: boolean;
      autoSubmitForReview?: boolean;
      autoPublish?: boolean;
      limit?: number;
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      conceptId?: string;
      moduleId?: string;
    } = {},
  ): Promise<CoverageBacklogExecutionSummary> {
    const createdTemplateIds: string[] = [];
    let createdStarterTemplates = 0;
    let createdLegacyAutoTemplates = 0;
    let submittedForReview = 0;
    let published = 0;

    if (input.includeStarters !== false) {
      const starterItems = await this.seedMissingStarterTemplates(
        tenantId,
        userId,
        {
          ...(input.domain ? { domain: input.domain } : {}),
          ...(input.audience ? { audience: input.audience } : {}),
          ...(input.limit !== undefined ? { limit: input.limit } : {}),
          ...(input.difficulty ? { difficulty: input.difficulty } : {}),
          ...(input.tags ? { tags: input.tags } : {}),
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
      );
      createdStarterTemplates += starterItems.length;
      createdTemplateIds.push(...starterItems.map((item) => item.id));
    }

    if (input.includeLegacyAutoPresets !== false) {
      const legacyAutoItems = await this.seedMissingAutoPresetTemplates(
        tenantId,
        userId,
        {
          ...(input.domain ? { domain: input.domain } : {}),
          source: "legacy_auto",
          ...(input.limit !== undefined ? { limit: input.limit } : {}),
          ...(input.difficulty ? { difficulty: input.difficulty } : {}),
          ...(input.tags ? { tags: input.tags } : {}),
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
      );
      createdLegacyAutoTemplates += legacyAutoItems.length;
      createdTemplateIds.push(...legacyAutoItems.map((item) => item.id));
    }

    if (input.autoSubmitForReview && createdTemplateIds.length > 0) {
      const submitted = await this.bulkSubmitTemplatesForReview(
        tenantId,
        userId,
        {
          templateIds: createdTemplateIds,
          notes: "Coverage backlog seeding",
        },
      );
      submittedForReview = submitted.processed;
    }

    if (input.autoPublish && createdTemplateIds.length > 0) {
      const publishResult = await this.bulkPublishTemplates(tenantId, {
        templateIds: createdTemplateIds,
      });
      published = publishResult.processed;
    }

    const coverageSummary = await this.getCoverageSummary(tenantId);

    return {
      createdStarterTemplates,
      createdLegacyAutoTemplates,
      submittedForReview,
      published,
      skippedCuratedAutoAliases:
        coverageSummary.autoPresets.bySource.curated_starter?.total ?? 0,
      templateIds: createdTemplateIds,
    };
  }

  async executeLegacyAutoRetirement(
    tenantId: string,
    userId: string,
    input: {
      domain?: string;
      limit?: number;
      autoSubmitForReview?: boolean;
      autoPublish?: boolean;
    } = {},
  ): Promise<LegacyAutoRetirementExecutionSummary> {
    const plan = await this.getLegacyAutoRetirementPlan(tenantId);
    const items = plan.items
      .filter((item) => (input.domain ? item.domain === input.domain : true))
      .slice(0, input.limit ?? plan.items.length);

    const createdTemplateIds: string[] = [];
    const failed: LegacyAutoRetirementExecutionSummary["failed"] = [];
    let createdTemplates = 0;
    let submittedForReview = 0;
    let published = 0;

    for (const item of items) {
      try {
        let templateId = item.templateId;

        if (item.status === "needs_template") {
          const created = await this.createTemplateFromAutoPreset(
            tenantId,
            userId,
            item.presetId,
          );
          templateId = created.id;
          createdTemplateIds.push(created.id);
          createdTemplates++;
        }

        if (!templateId) {
          continue;
        }

        if (input.autoSubmitForReview && item.status !== "ready_to_retire") {
          await this.submitTemplateForReview(
            tenantId,
            templateId,
            userId,
            "Legacy auto retirement execution",
          );
          submittedForReview++;
        }

        if (input.autoPublish && item.status !== "ready_to_retire") {
          await this.publishTemplate(tenantId, templateId);
          published++;
        }
      } catch (error) {
        failed.push({
          presetId: item.presetId,
          reason:
            error instanceof Error
              ? error.message
              : "Unknown retirement failure",
        });
      }
    }

    return {
      createdTemplates,
      submittedForReview,
      published,
      failed,
      templateIds: createdTemplateIds,
      remainingPlan: await this.getLegacyAutoRetirementPlan(tenantId),
    };
  }

  async getGovernanceSummary(tenantId: string) {
    const templates = await this.prisma.simulationTemplate.findMany({
      where: { tenantId },
      include: {
        manifest: {
          select: {
            manifest: true,
          },
        },
      },
    });

    const counts = {
      draft: 0,
      submitted: 0,
      approved: 0,
      rejected: 0,
      deprecated: 0,
    } satisfies Record<TemplateGovernanceStatus, number>;

    for (const template of templates) {
      const governance = normalizeGovernance(
        (template.manifest?.manifest as Record<string, unknown> | undefined)
          ?.templateGovernance,
        { reviewStatus: "draft", source: "starter" },
      );
      counts[governance.reviewStatus]++;
    }

    return counts;
  }

  async getTemplateById(tenantId: string, templateId: string) {
    const template = await this.prisma.simulationTemplate.findFirst({
      where: { id: templateId, tenantId },
      include: {
        manifest: true,
      },
    });
    if (!template) {
      throw new Error("Template not found");
    }
    return template;
  }

  async validateTemplate(tenantId: string, templateId: string) {
    const template = await this.getTemplateById(tenantId, templateId);
    const manifestPayload = template.manifest?.manifest as
      | Record<string, unknown>
      | undefined;
    if (!manifestPayload) {
      throw new Error("Template manifest not found");
    }

    // Base manifest validation
    const baseValidation = validateManifest(manifestPayload as never);

    // Enhanced quality validation
    const normalizedTemplate = {
      ...template,
      createdAt: (template.createdAt instanceof Date
        ? template.createdAt
        : new Date()
      ).toISOString(),
      updatedAt: (template.updatedAt instanceof Date
        ? template.updatedAt
        : new Date()
      ).toISOString(),
    };

    const qualityChecks = this.performQualityValidation(
      normalizedTemplate,
      manifestPayload,
    );

    // Governance validation for publish eligibility
    const governanceCheck = this.validateGovernanceForPublish(
      normalizedTemplate,
      manifestPayload,
    );

    const baseIssues = extractValidationIssues(baseValidation);

    const allIssues = [
      ...baseIssues,
      ...qualityChecks.issues,
      ...governanceCheck.issues,
    ];

    return {
      valid:
        baseValidation.valid && qualityChecks.valid && governanceCheck.valid,
      score: qualityChecks.qualityScore,
      issues: allIssues,
      canPublish: governanceCheck.canPublish,
      publishBlocked: governanceCheck.publishBlocked,
      qualityTier: qualityChecks.qualityTier,
      details: {
        base: baseValidation,
        quality: qualityChecks,
        governance: governanceCheck,
      },
    };
  }

  private performQualityValidation(
    template: SimulationTemplateWithManifest,
    manifest: Record<string, unknown>,
  ) {
    const issues: string[] = [];
    let qualityScore = 1.0;

    // Content quality checks
    const title = String(template.title ?? "").trim();
    const description = String(template.description ?? "").trim();

    if (title.length < 5) {
      issues.push("Title is too short (minimum 5 characters)");
      qualityScore -= 0.15;
    }

    if (description.length < 20) {
      issues.push("Description is too short (minimum 20 characters)");
      qualityScore -= 0.1;
    }

    // Manifest structure validation
    const steps = Array.isArray(manifest.steps) ? manifest.steps : [];
    const entities = Array.isArray(
      (manifest as { entities?: unknown[] }).entities,
    )
      ? (manifest as { entities: unknown[] }).entities
      : [];

    if (steps.length === 0) {
      issues.push("Manifest has no simulation steps");
      qualityScore -= 0.25;
    } else if (steps.length < 2) {
      issues.push(
        "Manifest should have at least 2 steps for meaningful simulation",
      );
      qualityScore -= 0.1;
    }

    if (entities.length === 0) {
      issues.push("Manifest has no entities");
      qualityScore -= 0.25;
    }

    // Check for step quality
    const stepsWithNarration = steps.filter(
      (step: { narration?: string }) =>
        step.narration && String(step.narration).length > 10,
    ).length;

    if (steps.length > 0 && stepsWithNarration < steps.length * 0.5) {
      issues.push("Many steps lack educational narration");
      qualityScore -= 0.1;
    }

    // Domain appropriateness
    const domain = String(template.domain ?? "").toUpperCase();
    const validDomains = [
      "PHYSICS",
      "CHEMISTRY",
      "BIOLOGY",
      "MEDICINE",
      "ECONOMICS",
      "CS_DISCRETE",
      "ENGINEERING",
      "MATHEMATICS",
    ];
    if (!validDomains.includes(domain)) {
      issues.push(`Domain "${domain}" is not in approved list`);
      qualityScore -= 0.15;
    }

    // Check for duplicate/similar templates
    const similarityScore = this.checkTemplateSimilarity(template, manifest);
    if (similarityScore > 0.85) {
      issues.push(
        "Template is highly similar to existing templates (possible duplicate)",
      );
      qualityScore -= 0.2;
    }

    // Quality tier determination
    let qualityTier: "high" | "medium" | "low" = "low";
    if (qualityScore >= 0.85) {
      qualityTier = "high";
    } else if (qualityScore >= 0.65) {
      qualityTier = "medium";
    }

    return {
      valid: qualityScore >= 0.5,
      qualityScore: Math.max(0, qualityScore),
      issues,
      qualityTier,
      metrics: {
        stepCount: steps.length,
        entityCount: entities.length,
        stepsWithNarration,
        titleLength: title.length,
        descriptionLength: description.length,
        similarityScore,
      },
    };
  }

  private validateGovernanceForPublish(
    template: SimulationTemplateWithManifest,
    manifest: Record<string, unknown>,
  ) {
    const issues: string[] = [];
    let canPublish = true;
    let publishBlocked = false;

    const governance = normalizeGovernance(manifest.templateGovernance, {
      reviewStatus: "draft",
      source: "starter",
    });

    // Check review status
    if (governance.reviewStatus !== "approved") {
      issues.push(
        `Template must be approved before publishing (current: ${governance.reviewStatus})`,
      );
      canPublish = false;
    }

    // Check source quality for auto-generated templates
    if (governance.source === "auto_preset") {
      // Auto presets require extra scrutiny
      const qualityMetrics = this.performQualityValidation(template, manifest);

      if (qualityMetrics.qualityScore < 0.7) {
        issues.push(
          "Auto-generated templates must have quality score >= 0.7 to publish",
        );
        canPublish = false;
        publishBlocked = true;
      }

      if (qualityMetrics.qualityTier === "low") {
        issues.push(
          "Low-quality auto-generated templates cannot be published (use curated starters instead)",
        );
        publishBlocked = true;
      }
    }

    // Check if already published
    if (template.status === "PUBLISHED") {
      issues.push("Template is already published");
      canPublish = false;
    }

    // Check if deprecated
    if (governance.reviewStatus === "deprecated") {
      issues.push("Deprecated templates cannot be published");
      publishBlocked = true;
    }

    return {
      valid: !publishBlocked,
      canPublish,
      publishBlocked,
      issues,
      governance,
    };
  }

  private checkTemplateSimilarity(
    template: SimulationTemplateWithManifest,
    manifest: Record<string, unknown>,
  ): number {
    const title = String(template.title ?? "")
      .toLowerCase()
      .trim();
    const description = String(template.description ?? "")
      .toLowerCase()
      .trim();

    // Extract key terms from manifest
    const steps = Array.isArray(manifest.steps) ? manifest.steps : [];
    const stepTitles = steps
      .map((s: { title?: string }) => String(s.title ?? "").toLowerCase())
      .join(" ");

    const fingerprint = [title, description, stepTitles]
      .filter((value) => value.length > 0)
      .join(" ");

    if (!fingerprint) {
      return 0;
    }

    const uniqueTerms = new Set(fingerprint.split(/\s+/).filter(Boolean));
    return Math.min(uniqueTerms.size / 50, 1);
  }

  async validateTemplatesBulk(
    tenantId: string,
    input: { templateIds?: string[] } = {},
  ): Promise<
    BulkTemplateActionResult<{
      templateId: string;
      validation: Awaited<
        ReturnType<SimulationTemplateLibraryService["validateTemplate"]>
      >;
    }>
  > {
    const templates = await this.prisma.simulationTemplate.findMany({
      where: {
        tenantId,
        ...(input.templateIds?.length ? { id: { in: input.templateIds } } : {}),
      },
      include: { manifest: true },
      orderBy: { updatedAt: "desc" },
    });

    const items = [];
    for (const template of templates) {
      items.push({
        templateId: template.id,
        validation: await this.validateTemplate(tenantId, template.id),
      });
    }

    return {
      processed: items.length,
      items,
    };
  }

  async previewTemplateFromStarter(
    tenantId: string,
    userId: string,
    starterId: string,
    input: CreateTemplateInput = {},
  ) {
    const resolved = resolveSimulationStarterReference(starterId);
    if (!resolved) {
      throw new Error("Starter not found");
    }

    const manifest = createSimulationStarterManifest({
      starterRef: resolved.starter.id,
      tenantId: tenantId as never,
      authorId: userId as never,
      ...(input.title ? { title: input.title } : {}),
      ...(input.description ? { description: input.description } : {}),
    });
    if (!manifest) {
      throw new Error("Starter manifest bootstrap failed");
    }

    return {
      source: "starter",
      starterId: resolved.starter.id,
      title: input.title ?? resolved.starter.name,
      description: input.description ?? resolved.starter.summary,
      difficulty:
        input.difficulty ?? toTemplateDifficulty(resolved.starter.difficulty),
      tags: input.tags ?? resolved.starter.tags,
      governance: {
        reviewStatus: "draft",
        source: "starter",
        starterId: resolved.starter.id,
      } satisfies TemplateGovernanceRecord,
      manifest,
    };
  }

  async previewTemplateFromAutoPreset(
    tenantId: string,
    userId: string,
    presetId: string,
    input: CreateTemplateInput = {},
  ) {
    const resolved = resolveCompatibleAutoPreset(presetId);
    if (!resolved) {
      throw new Error("Auto preset not found");
    }

    const manifest = bootstrapCompatibleAutoPreset({
      presetRef: presetId,
      tenantId: tenantId as never,
      authorId: userId as never,
      ...(input.title ? { title: input.title } : {}),
      ...(input.description ? { description: input.description } : {}),
    });
    if (!manifest) {
      throw new Error("Auto preset bootstrap failed");
    }

    return {
      source: "auto_preset",
      autoPresetId: presetId,
      ...(resolved.starterId ? { starterId: resolved.starterId } : {}),
      title: input.title ?? resolved.name,
      description: input.description ?? resolved.description,
      difficulty: input.difficulty ?? "INTERMEDIATE",
      tags: input.tags ?? [],
      governance: {
        reviewStatus: "draft",
        source: "auto_preset",
        autoPresetId: presetId,
        ...(resolved.starterId ? { starterId: resolved.starterId } : {}),
      } satisfies TemplateGovernanceRecord,
      manifest,
    };
  }

  async createTemplateFromStarter(
    tenantId: string,
    userId: string,
    starterId: string,
    input: CreateTemplateInput = {},
  ) {
    const resolved = resolveSimulationStarterReference(starterId);
    if (!resolved) {
      throw new Error("Starter not found");
    }

    const manifest = createSimulationStarterManifest({
      starterRef: resolved.starter.id,
      tenantId: tenantId as never,
      authorId: userId as never,
      ...(input.title ? { title: input.title } : {}),
      ...(input.description ? { description: input.description } : {}),
    });
    if (!manifest) {
      throw new Error("Starter manifest bootstrap failed");
    }

    const governance: TemplateGovernanceRecord = {
      reviewStatus: "draft",
      source: "starter",
      starterId: resolved.starter.id,
    };
    const slug = await ensureUniqueTemplateSlug(
      this.prisma,
      tenantId,
      input.title ?? resolved.starter.name,
    );

    return this.prisma.$transaction(async (tx) => {
      const manifestRecord = await tx.simulationManifest.create({
        data: {
          id: manifest.id,
          tenantId,
          title: manifest.title,
          description: manifest.description ?? null,
          version: manifest.version,
          domain: manifest.domain,
          manifest: toInputJsonValue(
            withGovernance(
              manifest as unknown as Record<string, unknown>,
              governance,
            ),
          ),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
      });

      return tx.simulationTemplate.create({
        data: {
          tenantId,
          slug,
          title: input.title ?? resolved.starter.name,
          description: input.description ?? resolved.starter.summary,
          domain: resolved.starter.domain,
          difficulty:
            input.difficulty ??
            toTemplateDifficulty(resolved.starter.difficulty),
          tags: JSON.stringify(input.tags ?? resolved.starter.tags),
          license: "FREE",
          isPremium: false,
          isVerified: false,
          version: "1.0.0",
          authorId: userId,
          status: "DRAFT",
          manifestId: manifestRecord.id,
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
        include: {
          manifest: true,
        },
      });
    });
  }

  async createTemplateFromAutoPreset(
    tenantId: string,
    userId: string,
    presetId: string,
    input: CreateTemplateInput = {},
  ) {
    const resolved = resolveCompatibleAutoPreset(presetId);
    if (!resolved) {
      throw new Error("Auto preset not found");
    }

    const manifest = bootstrapCompatibleAutoPreset({
      presetRef: presetId,
      tenantId: tenantId as never,
      authorId: userId as never,
      ...(input.title ? { title: input.title } : {}),
      ...(input.description ? { description: input.description } : {}),
    });
    if (!manifest) {
      throw new Error("Auto preset bootstrap failed");
    }

    const slug = await ensureUniqueTemplateSlug(
      this.prisma,
      tenantId,
      input.title ?? resolved.name,
    );
    const governance: TemplateGovernanceRecord = {
      reviewStatus: "draft",
      source: "auto_preset",
      autoPresetId: presetId,
      ...(resolved.starterId ? { starterId: resolved.starterId } : {}),
    };

    return this.prisma.$transaction(async (tx) => {
      const manifestRecord = await tx.simulationManifest.create({
        data: {
          id: manifest.id,
          tenantId,
          title: manifest.title,
          description: manifest.description ?? null,
          version: manifest.version,
          domain: manifest.domain,
          manifest: toInputJsonValue(
            withGovernance(
              manifest as unknown as Record<string, unknown>,
              governance,
            ),
          ),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
      });

      return tx.simulationTemplate.create({
        data: {
          tenantId,
          slug,
          title: input.title ?? resolved.name,
          description: input.description ?? resolved.description,
          domain: manifest.domain,
          difficulty: input.difficulty ?? "INTERMEDIATE",
          tags: JSON.stringify(input.tags ?? []),
          license: "FREE",
          isPremium: false,
          isVerified: false,
          version: "1.0.0",
          authorId: userId,
          status: "DRAFT",
          manifestId: manifestRecord.id,
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
        },
        include: {
          manifest: true,
        },
      });
    });
  }

  async cloneTemplate(
    tenantId: string,
    userId: string,
    templateId: string,
    input: { title?: string; description?: string } = {},
  ) {
    const existing = await this.prisma.simulationTemplate.findFirst({
      where: { id: templateId, tenantId },
      include: { manifest: true },
    });
    if (!existing || !existing.manifest) {
      throw new Error("Template not found");
    }

    const manifestPayload = existing.manifest.manifest as Record<
      string,
      unknown
    >;
    const manifestClone = structuredClone(manifestPayload);
    const newManifestId = `${existing.manifest.id}-clone-${Date.now()}`;
    manifestClone.id = newManifestId;
    manifestClone.updatedAt = new Date().toISOString();
    manifestClone.authorId = userId;
    manifestClone.tenantId = tenantId;

    const governance = normalizeGovernance(manifestClone.templateGovernance, {
      reviewStatus: "draft",
      source: "clone",
      parentTemplateId: templateId,
    });
    governance.reviewStatus = "draft";
    governance.source = "clone";
    governance.parentTemplateId = templateId;

    const title = input.title ?? `${existing.title} Copy`;
    const slug = await ensureUniqueTemplateSlug(this.prisma, tenantId, title);

    return this.prisma.$transaction(async (tx) => {
      const manifestRecord = await tx.simulationManifest.create({
        data: {
          id: newManifestId,
          tenantId,
          title,
          description: input.description ?? existing.description,
          version: existing.version,
          domain: existing.domain,
          manifest: toInputJsonValue(withGovernance(manifestClone, governance)),
          ...(existing.moduleId ? { moduleId: existing.moduleId } : {}),
        },
      });

      return tx.simulationTemplate.create({
        data: {
          tenantId,
          slug,
          title,
          description: input.description ?? existing.description,
          domain: existing.domain,
          difficulty: existing.difficulty,
          tags: existing.tags,
          thumbnailUrl: existing.thumbnailUrl,
          license: existing.license,
          isPremium: existing.isPremium,
          isVerified: false,
          version: incrementSemver(existing.version),
          authorId: userId,
          authorName: existing.authorName,
          authorAvatarUrl: existing.authorAvatarUrl,
          organization: existing.organization,
          status: "DRAFT",
          manifestId: manifestRecord.id,
          ...(existing.moduleId ? { moduleId: existing.moduleId } : {}),
          ...(existing.conceptId ? { conceptId: existing.conceptId } : {}),
        },
        include: { manifest: true },
      });
    });
  }

  async submitTemplateForReview(
    tenantId: string,
    templateId: string,
    userId: string,
    notes?: string,
  ) {
    return this.updateTemplateGovernance(
      tenantId,
      templateId,
      (governance) => ({
        ...governance,
        reviewStatus: "submitted",
        requestedBy: userId,
        requestedAt: new Date().toISOString(),
        ...(notes ? { reviewerNotes: notes } : {}),
      }),
    );
  }

  async listPendingReviewTemplates(tenantId: string) {
    const templates = await this.prisma.simulationTemplate.findMany({
      where: { tenantId },
      include: {
        manifest: {
          select: {
            id: true,
            manifest: true,
          },
        },
      },
      orderBy: { updatedAt: "desc" },
    });

    return templates.filter((template) => {
      const manifestPayload = template.manifest?.manifest as
        | Record<string, unknown>
        | undefined;
      return (
        manifestPayload?.templateGovernance &&
        normalizeGovernance(manifestPayload.templateGovernance, {
          reviewStatus: "draft",
          source: "starter",
        }).reviewStatus === "submitted"
      );
    });
  }

  async getTemplateReviewHistory(tenantId: string, templateId: string) {
    const template = await this.getTemplateById(tenantId, templateId);
    const governance = normalizeGovernance(
      (template.manifest?.manifest as Record<string, unknown> | undefined)
        ?.templateGovernance,
      { reviewStatus: "draft", source: "starter" },
    );

    return [
      {
        templateId: template.id,
        status: governance.reviewStatus,
        requestedBy: governance.requestedBy ?? null,
        requestedAt: governance.requestedAt ?? null,
        reviewedBy: governance.reviewedBy ?? null,
        reviewedAt: governance.reviewedAt ?? null,
        reviewerNotes: governance.reviewerNotes ?? null,
      },
    ];
  }

  async getTemplateLineage(tenantId: string, templateId: string) {
    const template = await this.getTemplateById(tenantId, templateId);
    const governance = normalizeGovernance(
      (template.manifest?.manifest as Record<string, unknown> | undefined)
        ?.templateGovernance,
      { reviewStatus: "draft", source: "starter" },
    );

    const derivedTemplates = await this.prisma.simulationTemplate.findMany({
      where: { tenantId },
      include: {
        manifest: {
          select: {
            manifest: true,
          },
        },
      },
    });

    const children = derivedTemplates
      .filter((candidate) => {
        const candidateGovernance = normalizeGovernance(
          (candidate.manifest?.manifest as Record<string, unknown> | undefined)
            ?.templateGovernance,
          { reviewStatus: "draft", source: "starter" },
        );
        return candidateGovernance.parentTemplateId === templateId;
      })
      .map((candidate) => ({
        id: candidate.id,
        title: candidate.title,
        status: candidate.status,
      }));

    return {
      templateId: template.id,
      source: governance.source,
      starterId: governance.starterId ?? null,
      autoPresetId: governance.autoPresetId ?? null,
      parentTemplateId: governance.parentTemplateId ?? null,
      children,
    };
  }

  async exportTemplate(
    tenantId: string,
    templateId: string,
    format: "manifest" | "webxr" | "unity" = "manifest",
  ) {
    const template = await this.getTemplateById(tenantId, templateId);
    const governance = normalizeGovernance(
      (template.manifest?.manifest as Record<string, unknown> | undefined)
        ?.templateGovernance,
      { reviewStatus: "draft", source: "starter" },
    );

    if (governance.source === "auto_preset" && governance.autoPresetId) {
      const exported = exportCompatibleAutoPreset({
        presetRef: governance.autoPresetId,
        format,
        tenantId: tenantId as never,
        title: template.title,
        ...(template.description ? { description: template.description } : {}),
      });
      if (exported) {
        return exported;
      }
    }

    if (governance.starterId) {
      const exported = exportSimulationStarterPackage({
        starterRef: governance.starterId,
        format,
        tenantId: tenantId as never,
        title: template.title,
        ...(template.description ? { description: template.description } : {}),
      });
      if (exported) {
        return exported;
      }
    }

    return {
      templateId: template.id,
      format,
      manifest: template.manifest?.manifest ?? null,
    };
  }

  async bulkCreateTemplatesFromStarters(
    tenantId: string,
    userId: string,
    input: {
      starterIds: string[];
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      moduleId?: string;
      conceptId?: string;
    },
  ) {
    const results = [];
    for (const starterId of input.starterIds) {
      results.push(
        await this.createTemplateFromStarter(tenantId, userId, starterId, {
          ...(input.difficulty ? { difficulty: input.difficulty } : {}),
          ...(input.tags ? { tags: input.tags } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
        }),
      );
    }
    return results;
  }

  async bulkCreateTemplatesFromAutoPresets(
    tenantId: string,
    userId: string,
    input: {
      presetIds: string[];
      difficulty?: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
      tags?: string[];
      moduleId?: string;
      conceptId?: string;
    },
  ) {
    const results = [];
    for (const presetId of input.presetIds) {
      results.push(
        await this.createTemplateFromAutoPreset(tenantId, userId, presetId, {
          ...(input.difficulty ? { difficulty: input.difficulty } : {}),
          ...(input.tags ? { tags: input.tags } : {}),
          ...(input.moduleId ? { moduleId: input.moduleId } : {}),
          ...(input.conceptId ? { conceptId: input.conceptId } : {}),
        }),
      );
    }
    return results;
  }

  async bulkSubmitTemplatesForReview(
    tenantId: string,
    userId: string,
    input: { templateIds: string[]; notes?: string },
  ): Promise<BulkTemplateActionResult> {
    const items = [];
    for (const templateId of input.templateIds) {
      items.push(
        await this.submitTemplateForReview(
          tenantId,
          templateId,
          userId,
          input.notes,
        ),
      );
    }
    return { processed: items.length, items };
  }

  async bulkReviewTemplates(
    tenantId: string,
    reviewerId: string,
    input: {
      templateIds: string[];
      action: "approve" | "reject";
      notes?: string;
      publish?: boolean;
    },
  ): Promise<BulkTemplateActionResult> {
    const items = [];
    for (const templateId of input.templateIds) {
      items.push(
        await this.reviewTemplate(
          tenantId,
          templateId,
          reviewerId,
          input.action,
          {
            ...(input.notes ? { notes: input.notes } : {}),
            ...(input.publish !== undefined ? { publish: input.publish } : {}),
          },
        ),
      );
    }
    return { processed: items.length, items };
  }

  async bulkDeprecateTemplates(
    tenantId: string,
    input: { templateIds: string[]; reason: string },
  ): Promise<BulkTemplateActionResult> {
    const items = [];
    for (const templateId of input.templateIds) {
      items.push(
        await this.deprecateTemplate(tenantId, templateId, input.reason),
      );
    }
    return { processed: items.length, items };
  }

  async bulkPublishTemplates(
    tenantId: string,
    input: { templateIds: string[] },
  ): Promise<BulkTemplateActionResult> {
    const items = [];
    for (const templateId of input.templateIds) {
      items.push(await this.publishTemplate(tenantId, templateId));
    }
    return { processed: items.length, items };
  }

  async reviewTemplate(
    tenantId: string,
    templateId: string,
    reviewerId: string,
    action: "approve" | "reject",
    input: { notes?: string; publish?: boolean } = {},
  ) {
    const template = await this.updateTemplateGovernance(
      tenantId,
      templateId,
      (governance) => ({
        ...governance,
        reviewStatus: action === "approve" ? "approved" : "rejected",
        reviewedBy: reviewerId,
        reviewedAt: new Date().toISOString(),
        ...(input.notes ? { reviewerNotes: input.notes } : {}),
      }),
    );

    const status =
      action === "approve" && input.publish ? "PUBLISHED" : template.status;
    const isVerified = action === "approve";

    return this.prisma.simulationTemplate.update({
      where: { id: template.id },
      data: {
        isVerified,
        status,
        publishedAt: status === "PUBLISHED" ? new Date() : template.publishedAt,
      },
      include: { manifest: true },
    });
  }

  async publishTemplate(tenantId: string, templateId: string) {
    const validation = await this.validateTemplate(tenantId, templateId);
    if (!validation.valid) {
      throw new Error("Template manifest is not valid enough to publish");
    }

    return this.prisma.simulationTemplate.update({
      where: { id: templateId },
      data: {
        status: "PUBLISHED",
        isVerified: true,
        publishedAt: new Date(),
      },
      include: { manifest: true },
    });
  }

  async deprecateTemplate(
    tenantId: string,
    templateId: string,
    reason: string,
  ) {
    await this.updateTemplateGovernance(tenantId, templateId, (governance) => ({
      ...governance,
      reviewStatus: "deprecated",
      deprecationReason: reason,
    }));

    return this.prisma.simulationTemplate.update({
      where: { id: templateId },
      data: {
        status: "ARCHIVED",
      },
      include: { manifest: true },
    });
  }

  private async updateTemplateGovernance(
    tenantId: string,
    templateId: string,
    updater: (current: TemplateGovernanceRecord) => TemplateGovernanceRecord,
  ) {
    const template = await this.prisma.simulationTemplate.findFirst({
      where: { id: templateId, tenantId },
      include: { manifest: true },
    });
    if (!template || !template.manifest) {
      throw new Error("Template not found");
    }

    const manifestPayload = structuredClone(
      template.manifest.manifest as Record<string, unknown>,
    );
    const governance = normalizeGovernance(manifestPayload.templateGovernance, {
      reviewStatus: "draft",
      source: "starter",
    });

    await this.prisma.simulationManifest.update({
      where: { id: template.manifest.id },
      data: {
        manifest: toInputJsonValue(
          withGovernance(manifestPayload, updater(governance)),
        ),
      },
    });

    return template;
  }
}

function incrementSemver(version: string): string {
  const parts = version.split(".");
  if (parts.length !== 3) {
    return "1.0.1";
  }

  const patch = Number(parts[2]);
  if (!Number.isFinite(patch)) {
    return "1.0.1";
  }

  return `${parts[0]}.${parts[1]}.${patch + 1}`;
}

function toInputJsonValue(
  value: Record<string, unknown>,
): Prisma.InputJsonValue {
  return value as Prisma.InputJsonValue;
}

function extractValidationIssues(validation: unknown): string[] {
  if (!validation || typeof validation !== "object") {
    return [];
  }
  const record = validation as { issues?: unknown; errors?: unknown };
  const issueSource = Array.isArray(record.issues)
    ? record.issues
    : Array.isArray(record.errors)
      ? record.errors
      : [];

  return issueSource.map(String);
}

function toTemplateDifficulty(
  value: string,
): "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT" {
  switch (value.toUpperCase()) {
    case "BEGINNER":
      return "BEGINNER";
    case "ADVANCED":
      return "ADVANCED";
    default:
      return "INTERMEDIATE";
  }
}
