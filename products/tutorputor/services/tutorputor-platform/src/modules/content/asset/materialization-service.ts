/**
 * Asset Materialization Service
 *
 * Turns successful generation-job output into canonical ContentAsset rows so
 * evaluation, review, and publish can operate on governed assets rather than
 * only raw execution payloads.
 *
 * @doc.type class
 * @doc.purpose Materialize generation outputs into canonical content assets
 * @doc.layer product
 * @doc.pattern Service
 */

import crypto from "crypto";
import type { PrismaClient } from "@tutorputor/core/db";
import type {
  ArtifactManifestType,
  ContentAssetType,
  ContentBlockType,
  GenerationJobType,
} from "@tutorputor/contracts/v1/content-studio";
import { validateAnimation, validateAssessment, validateWorkedExample } from "./manifest-validator.js";
import { extractBlockText } from "./text-extraction.js";

type MaterializableJobType = Exclude<GenerationJobType, "claim" | "evaluation">;

export interface MaterializeGenerationJobInput {
  tenantId: string;
  requestId: string;
  jobId: string;
  jobType: MaterializableJobType;
  requestTitle: string;
  requestDescription?: string;
  domain: string;
  conceptId?: string;
  targetGrades: string[];
  requestedBy: string;
  targetRef?: string;
  outputData: Record<string, unknown>;
  existingAssetId?: string;
}

export interface MaterializedGenerationAsset {
  assetId: string;
  assetType: ContentAssetType;
  currentVersion: number;
  blockCount: number;
  manifestCount: number;
  searchableText: string;
  created: boolean;
}

interface MaterializationShape {
  assetType: ContentAssetType;
  title: string;
  difficultyLevel?: string;
  tags: string[];
  blocks: Array<{
    blockRef: string;
    blockType: ContentBlockType;
    orderIndex: number;
    title?: string;
    payload: Record<string, unknown>;
    claimRefs?: string[];
  }>;
  manifests: Array<{
    manifestType: ArtifactManifestType;
    version: string;
    claimRef?: string;
    manifest: Record<string, unknown>;
    generatedBy: "ai";
    isValid: boolean;
    validationErrors?: unknown[];
  }>;
  snapshot: Record<string, unknown>;
}

export class AssetMaterializationService {
  constructor(private readonly prisma: PrismaClient) {}

  async materializeJobOutput(
    input: MaterializeGenerationJobInput,
  ): Promise<MaterializedGenerationAsset> {
    const shape = buildMaterializationShape(input);
    const existingAsset = input.existingAssetId
      ? await (this.prisma as any).contentAsset.findFirst({
          where: { id: input.existingAssetId, tenantId: input.tenantId },
          select: { id: true, currentVersion: true, slug: true },
        })
      : null;

    const assetId = existingAsset?.id ?? null;
    const version = (existingAsset?.currentVersion ?? 0) + 1;
    const slug = existingAsset?.slug ?? buildAssetSlug(input, shape.assetType);
    const searchableText = buildSearchableText(shape);

    if (assetId) {
      await (this.prisma as any).contentAsset.update({
        where: { id: assetId },
        data: {
          slug,
          title: shape.title,
          assetType: shape.assetType.toUpperCase(),
          domain: normalizeAssetDomain(input.domain),
          conceptId: input.conceptId ?? null,
          status: "DRAFT",
          currentVersion: version,
          semanticIndexStatus: "PENDING",
          recommendationStatus: "STALE",
          searchableText,
          tags: shape.tags,
          targetGrades: input.targetGrades,
          difficultyLevel: shape.difficultyLevel ?? null,
          lastEditedBy: input.requestedBy,
          riskLevel: "LOW",
        },
      });

      await (this.prisma as any).contentBlock.deleteMany({
        where: { assetId },
      });
      await (this.prisma as any).artifactManifest.deleteMany({
        where: { assetId },
      });
    }

    const asset =
      existingAsset ??
      (await (this.prisma as any).contentAsset.create({
        data: {
          tenantId: input.tenantId,
          slug,
          title: shape.title,
          assetType: shape.assetType.toUpperCase(),
          domain: normalizeAssetDomain(input.domain),
          conceptId: input.conceptId ?? null,
          status: "DRAFT",
          currentVersion: version,
          semanticIndexStatus: "PENDING",
          recommendationStatus: "STALE",
          searchableText,
          tags: shape.tags,
          targetGrades: input.targetGrades,
          difficultyLevel: shape.difficultyLevel ?? null,
          authorId: input.requestedBy,
          lastEditedBy: input.requestedBy,
          riskLevel: "LOW",
        },
      }));

    for (const block of shape.blocks) {
      await (this.prisma as any).contentBlock.create({
        data: {
          assetId: asset.id,
          blockRef: block.blockRef,
          blockType: block.blockType.toUpperCase(),
          orderIndex: block.orderIndex,
          title: block.title ?? null,
          payload: block.payload,
          claimRefs: block.claimRefs ?? null,
        },
      });
    }

    for (const manifest of shape.manifests) {
      await (this.prisma as any).artifactManifest.create({
        data: {
          assetId: asset.id,
          manifestType: manifest.manifestType.toUpperCase(),
          version: manifest.version,
          claimRef: manifest.claimRef ?? null,
          manifest: manifest.manifest,
          isValid: manifest.isValid,
          validationErrors: manifest.validationErrors ?? null,
          generatedBy: manifest.generatedBy,
          generationId: input.requestId,
        },
      });
    }

    await (this.prisma as any).contentAssetRevision.create({
      data: {
        assetId: asset.id,
        version,
        changeNote: `Generated from ${input.jobType} job ${input.jobId}`,
        snapshot: shape.snapshot,
        createdBy: input.requestedBy,
      },
    });

    return {
      assetId: asset.id,
      assetType: shape.assetType,
      currentVersion: version,
      blockCount: shape.blocks.length,
      manifestCount: shape.manifests.length,
      searchableText,
      created: !existingAsset,
    };
  }
}

function buildMaterializationShape(
  input: MaterializeGenerationJobInput,
): MaterializationShape {
  switch (input.jobType) {
    case "explainer":
      return buildExplainerShape(input);
    case "worked_example":
      return buildWorkedExampleShape(input);
    case "simulation":
      return buildSimulationShape(input);
    case "animation":
      return buildAnimationShape(input);
    case "assessment":
      return buildAssessmentShape(input);
  }
}

function buildExplainerShape(
  input: MaterializeGenerationJobInput,
): MaterializationShape {
  const explainers = readArray(input.outputData.explainers);
  const primary = readObject(explainers[0]) ?? readObject(input.outputData);
  const narrative = readString(primary?.narrative)
    ?? readString(primary?.description)
    ?? readString(input.outputData.metadata)
    ?? input.requestDescription
    ?? input.requestTitle;

  return {
    assetType: "explainer",
    title: readString(primary?.title) ?? input.requestTitle,
    tags: ["generated", input.jobType],
    blocks: [
      {
        blockRef: "B1",
        blockType: "text_explainer",
        orderIndex: 1,
        title: readString(primary?.title) ?? "Explainer",
        payload: {
          text: narrative,
          examples: explainers,
        },
      },
    ],
    manifests: [],
    snapshot: {
      jobType: input.jobType,
      explainers,
      outputData: input.outputData,
    },
  };
}

function buildWorkedExampleShape(
  input: MaterializeGenerationJobInput,
): MaterializationShape {
  const examples = readArray(input.outputData.examples).map((item, index) =>
    readObject(item) ?? { id: `example-${index + 1}` },
  );
  const manifest = {
    title: input.requestTitle,
    examples,
  };
  const validation = validateWorkedExample(manifest as any);

  return {
    assetType: "example_set",
    title: `${input.requestTitle} Worked Examples`,
    tags: ["generated", input.jobType],
    blocks: examples.map((example, index) => ({
      blockRef: `B${index + 1}`,
      blockType: "worked_example",
      orderIndex: index + 1,
      title: readString(example.title) ?? `Worked Example ${index + 1}`,
      payload: {
        prompt: readString(example.title) ?? `Worked Example ${index + 1}`,
        explanation:
          readString(example.solution_content)
          ?? readString(example.content)
          ?? readString(example.description)
          ?? "",
        source: example,
      },
    })),
    manifests: [
      {
        manifestType: "worked_example",
        version: "1.0.0",
        manifest,
        generatedBy: "ai",
        isValid: validation.isValid,
        ...(validation.violations.length > 0
          ? { validationErrors: validation.violations }
          : {}),
      },
    ],
    snapshot: {
      jobType: input.jobType,
      examples,
      outputData: input.outputData,
    },
  };
}

function buildSimulationShape(
  input: MaterializeGenerationJobInput,
): MaterializationShape {
  const simulations = readArray(input.outputData.simulations).map((item, index) =>
    readObject(item) ?? { id: `simulation-${index + 1}` },
  );
  const manifests: MaterializationShape["manifests"] = simulations.map(
    (simulation) => ({
      manifestType: "simulation",
      version: readString(simulation.version) ?? "1.0.0",
      ...(readString(simulation.claimRef)
        ? { claimRef: readString(simulation.claimRef) }
        : {}),
      manifest: simulation,
      generatedBy: "ai",
      isValid:
        typeof simulation.id === "string" || typeof simulation.manifest_id === "string",
    }),
  ) as MaterializationShape["manifests"];

  return {
    assetType: "simulation",
    title: `${input.requestTitle} Simulation`,
    tags: ["generated", input.jobType],
    blocks: simulations.map((simulation, index) => ({
      blockRef: `B${index + 1}`,
      blockType: "simulation_entry",
      orderIndex: index + 1,
      title: readString(simulation.title) ?? `Simulation ${index + 1}`,
      payload: {
        summary:
          readString(simulation.description)
          ?? readString(simulation.objective)
          ?? input.requestTitle,
        manifestRef: readString(simulation.manifest_id) ?? readString(simulation.id),
        simulation,
      },
    })),
    manifests,
    snapshot: {
      jobType: input.jobType,
      simulations,
      outputData: input.outputData,
    },
  };
}

function buildAnimationShape(
  input: MaterializeGenerationJobInput,
): MaterializationShape {
  const animations = readArray(input.outputData.animations).map((item, index) =>
    readObject(item) ?? { id: `animation-${index + 1}` },
  );
  const first = animations[0] ?? {};
  const validation = validateAnimation(first as any);
  const manifests: MaterializationShape["manifests"] = animations.map(
    (animation) => ({
      manifestType: "animation",
      version: readString(animation.version) ?? "1.0.0",
      ...(readString(animation.claimRef)
        ? { claimRef: readString(animation.claimRef) }
        : {}),
      manifest: animation,
      generatedBy: "ai",
      isValid: validation.isValid,
      ...(validation.violations.length > 0
        ? { validationErrors: validation.violations }
        : {}),
    }),
  ) as MaterializationShape["manifests"];

  return {
    assetType: "animation",
    title: `${input.requestTitle} Animation`,
    tags: ["generated", input.jobType],
    blocks: animations.map((animation, index) => ({
      blockRef: `B${index + 1}`,
      blockType: "animation_entry",
      orderIndex: index + 1,
      title: readString(animation.title) ?? `Animation ${index + 1}`,
      payload: {
        summary:
          readString(animation.description)
          ?? readString(animation.narrative)
          ?? input.requestTitle,
        animation,
      },
    })),
    manifests,
    snapshot: {
      jobType: input.jobType,
      animations,
      outputData: input.outputData,
    },
  };
}

function buildAssessmentShape(
  input: MaterializeGenerationJobInput,
): MaterializationShape {
  const assessments = readArray(input.outputData.assessments).map((item, index) =>
    readObject(item) ?? { id: `assessment-${index + 1}` },
  );
  const manifest = {
    title: input.requestTitle,
    questions: assessments,
  };
  const validation = validateAssessment(manifest as any);

  return {
    assetType: "assessment",
    title: `${input.requestTitle} Assessment`,
    tags: ["generated", input.jobType],
    blocks: [
      {
        blockRef: "B1",
        blockType: "question_set",
        orderIndex: 1,
        title: "Assessment Questions",
        payload: {
          questions: assessments,
        },
      },
    ],
    manifests: [
      {
        manifestType: "assessment",
        version: "1.0.0",
        manifest,
        generatedBy: "ai",
        isValid: validation.isValid,
        ...(validation.violations.length > 0
          ? { validationErrors: validation.violations }
          : {}),
      },
    ],
    snapshot: {
      jobType: input.jobType,
      assessments,
      outputData: input.outputData,
    },
  };
}

function buildAssetSlug(
  input: MaterializeGenerationJobInput,
  assetType: ContentAssetType,
): string {
  const titlePart = slugify(input.requestTitle);
  const typePart = slugify(assetType);
  const refPart = slugify(input.targetRef ?? input.jobId).slice(0, 24);
  const hash = crypto
    .createHash("sha1")
    .update(`${input.requestId}:${input.jobId}:${assetType}`)
    .digest("hex")
    .slice(0, 8);
  return `${titlePart}-${typePart}-${refPart}-${hash}`.slice(0, 120);
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

function normalizeAssetDomain(domain: string): "MATH" | "SCIENCE" | "TECH" {
  const normalized = domain.trim().toUpperCase();
  if (normalized.includes("MATH") || normalized.includes("ALGEBRA") || normalized.includes("CALCULUS") || normalized.includes("GEOMETRY")) {
    return "MATH";
  }
  if (
    normalized.includes("SCIENCE")
    || normalized.includes("PHYSICS")
    || normalized.includes("CHEM")
    || normalized.includes("BIO")
    || normalized.includes("MED")
    || normalized.includes("ASTRO")
  ) {
    return "SCIENCE";
  }
  return "TECH";
}

function buildSearchableText(shape: MaterializationShape): string {
  return [
    shape.title,
    ...shape.blocks.map((block) => extractBlockText(block.payload)),
    ...shape.manifests.map((manifest) => JSON.stringify(manifest.manifest)),
  ]
    .filter((value) => typeof value === "string" && value.trim().length > 0)
    .join("\n\n")
    .slice(0, 20_000);
}

function readArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function readObject(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function readString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0
    ? value.trim()
    : undefined;
}
