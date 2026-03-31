/**
 * Modality Conversion Service
 *
 * Canonical cross-modal conversions derived from content assets and manifests.
 *
 * @doc.type service
 * @doc.purpose Convert content assets across text, visual, audio, and simulation modalities
 * @doc.layer product
 * @doc.pattern Content Transformation
 */

import type {
  AnimationManifest,
  SimulationManifest,
} from "@tutorputor/contracts/v1/artifact-manifests";
import type { ArtifactManifest } from "@tutorputor/contracts/v1/content-studio";
import type { SimulationDomain, SimEntity } from "@tutorputor/contracts/v1/simulation";
import {
  estimateCompletionTime,
  inferDifficulty,
  inferSkills,
} from "../../../../../../libs/tutorputor-core/src/kernel/path/simulation-adapter.ts";
import { ContentAssetReadService } from "../asset/read-service.js";
import {
  extractBlockText,
  keepFirstSentences,
  splitIntoSentences,
} from "../asset/text-extraction.js";

export type ContentModality = "text" | "visual" | "audio" | "simulation";

export interface AvailableConversion {
  targetModality: ContentModality;
  supported: boolean;
  reason: string;
  usesExistingManifest: boolean;
}

export interface ConvertedContentBlock {
  blockRef: string;
  title: string;
  content: string;
  cues?: string[];
}

export interface ConversionSimulationSummary {
  manifest: SimulationManifest;
  inferredDifficulty: string;
  estimatedTimeMinutes: number;
  skillNames: string[];
}

export interface ConversionResult {
  assetId: string;
  title: string;
  sourceModalities: ContentModality[];
  targetModality: ContentModality;
  summary: string;
  blocks: ConvertedContentBlock[];
  manifests: ArtifactManifest[];
  simulation?: ConversionSimulationSummary;
  metadata: {
    sourceAssetType: string;
    strategy: string;
    manifestTypes: string[];
    generatedAt: string;
  };
}

export class ModalityConversionService {
  constructor(private readonly readService: ContentAssetReadService) {}

  async listAvailableConversions(
    tenantId: string,
    assetId: string,
  ): Promise<AvailableConversion[]> {
    const detail = await this.requireAssetDetail(tenantId, assetId);
    const manifestTypes = getManifestTypes(detail.manifests);

    return [
      {
        targetModality: "text",
        supported: true,
        reason: manifestTypes.includes("simulation")
          ? "Can narrativize simulation steps into guided text."
          : "Can reuse canonical content blocks as text-first output.",
        usesExistingManifest: manifestTypes.includes("simulation"),
      },
      {
        targetModality: "visual",
        supported: true,
        reason: manifestTypes.includes("animation")
          ? "Can reuse existing animation keyframes as storyboard output."
          : "Can derive diagram/storyboard cues from canonical text blocks.",
        usesExistingManifest: manifestTypes.includes("animation"),
      },
      {
        targetModality: "audio",
        supported: true,
        reason:
          "Can generate narration script from canonical blocks and structured manifests.",
        usesExistingManifest: manifestTypes.includes("animation"),
      },
      {
        targetModality: "simulation",
        supported: true,
        reason: manifestTypes.includes("simulation")
          ? "Can reuse the existing simulation manifest."
          : "Can derive a scaffold simulation manifest from canonical text and domain metadata.",
        usesExistingManifest: manifestTypes.includes("simulation"),
      },
    ];
  }

  async convertAsset(
    tenantId: string,
    assetId: string,
    targetModality: ContentModality,
  ): Promise<ConversionResult> {
    const detail = await this.requireAssetDetail(tenantId, assetId);
    const sourceModalities = inferSourceModalities(detail.manifests, detail.blocks);
    const manifestTypes = getManifestTypes(detail.manifests);

    switch (targetModality) {
      case "text":
        return this.convertToText(detail, sourceModalities, manifestTypes);
      case "visual":
        return this.convertToVisual(detail, sourceModalities, manifestTypes);
      case "audio":
        return this.convertToAudio(detail, sourceModalities, manifestTypes);
      case "simulation":
        return this.convertToSimulation(detail, sourceModalities, manifestTypes);
    }
  }

  private async requireAssetDetail(tenantId: string, assetId: string) {
    const detail = await this.readService.getAssetDetail(tenantId, assetId);
    if (!detail) {
      throw new Error(`Content asset ${assetId} not found`);
    }
    return detail;
  }

  private convertToText(
    detail: Awaited<ReturnType<ContentAssetReadService["getAssetDetail"]>> extends infer T
      ? NonNullable<T>
      : never,
    sourceModalities: ContentModality[],
    manifestTypes: string[],
  ): ConversionResult {
    const simulationManifest = findSimulationManifest(detail.manifests);

    const blocks = simulationManifest
      ? simulationManifest.steps.map((step: any, index: any) => ({
          blockRef: step.id,
          title: step.title ?? `Step ${index + 1}`,
          content:
            step.narration ??
            step.description ??
            `Step ${index + 1}: ${detail.asset.title}`,
          cues: step.annotations?.map((annotation: any) => annotation.text) ?? [],
        }))
      : detail.blocks.map((block: any, index: any) => ({
          blockRef: block.blockRef,
          title: block.title ?? `Section ${index + 1}`,
          content: extractBlockText(block.payload),
          cues: [`source:${block.blockType}`],
        }));

    return this.buildResult(
      detail,
      sourceModalities,
      "text",
      manifestTypes,
      blocks,
      simulationManifest
        ? "simulation-to-text"
        : "canonical-block-text",
      `Text-first conversion for ${detail.asset.title}`,
      simulationManifest,
    );
  }

  private convertToVisual(
    detail: Awaited<ReturnType<ContentAssetReadService["getAssetDetail"]>> extends infer T
      ? NonNullable<T>
      : never,
    sourceModalities: ContentModality[],
    manifestTypes: string[],
  ): ConversionResult {
    const animationManifest = findAnimationManifest(detail.manifests);

    const blocks = animationManifest
      ? animationManifest.keyframes.map((keyframe: any, index: any) => ({
          blockRef: `frame-${index + 1}`,
          title: `Storyboard frame ${index + 1}`,
          content:
            keyframe.narration ??
            `Visualize the state change at ${keyframe.timeMs}ms.`,
          cues: [
            `entities:${Object.keys(keyframe.entities).length}`,
            `pause:${String(Boolean(keyframe.pause))}`,
          ],
        }))
      : detail.blocks.map((block: any, index: any) => {
          const text = extractBlockText(block.payload);
          const keySentences = splitIntoSentences(text).slice(0, 2);

          return {
            blockRef: block.blockRef,
            title: block.title ?? `Visual panel ${index + 1}`,
            content: keepFirstSentences(text, 2),
            cues: keySentences.map((sentence: any, sentenceIndex: any) =>
              `panel-${sentenceIndex + 1}:${sentence}`,
            ),
          };
        });

    return this.buildResult(
      detail,
      sourceModalities,
      "visual",
      manifestTypes,
      blocks,
      animationManifest
        ? "animation-storyboard"
        : "text-to-visual-storyboard",
      `Visual storyboard conversion for ${detail.asset.title}`,
    );
  }

  private convertToAudio(
    detail: Awaited<ReturnType<ContentAssetReadService["getAssetDetail"]>> extends infer T
      ? NonNullable<T>
      : never,
    sourceModalities: ContentModality[],
    manifestTypes: string[],
  ): ConversionResult {
    const animationManifest = findAnimationManifest(detail.manifests);

    const blocks = detail.blocks.map((block: any, index: any) => {
      const text = extractBlockText(block.payload);
      const intro = index === 0 ? `Lesson title: ${detail.asset.title}. ` : "";
      return {
        blockRef: block.blockRef,
        title: block.title ?? `Narration segment ${index + 1}`,
        content: `${intro}${text}`,
        cues: [
          `pace:${animationManifest?.playback.allowScrub ? "conversational" : "guided"}`,
          `duration_hint:${Math.max(15, Math.ceil(text.length / 18))}s`,
        ],
      };
    });

    if (blocks.length === 0 && animationManifest) {
      blocks.push({
        blockRef: "narration-fallback",
        title: "Narration",
        content:
          animationManifest.accessibility?.altText ??
          `Guided narration for ${detail.asset.title}.`,
        cues: [`duration_hint:${Math.ceil(animationManifest.durationMs / 1000)}s`],
      });
    }

    return this.buildResult(
      detail,
      sourceModalities,
      "audio",
      manifestTypes,
      blocks,
      "narration-script",
      `Audio narration script for ${detail.asset.title}`,
    );
  }

  private convertToSimulation(
    detail: Awaited<ReturnType<ContentAssetReadService["getAssetDetail"]>> extends infer T
      ? NonNullable<T>
      : never,
    sourceModalities: ContentModality[],
    manifestTypes: string[],
  ): ConversionResult {
    const simulationManifest =
      findSimulationManifest(detail.manifests) ??
      buildDerivedSimulationManifest(detail);

    const simulation = {
      manifest: simulationManifest,
      inferredDifficulty: inferDifficulty(simulationManifest),
      estimatedTimeMinutes: Math.max(
        1,
        Math.ceil(estimateCompletionTime(simulationManifest) / 60),
      ),
      skillNames: inferSkills(simulationManifest).map((skill: any) => skill.name),
    };

    const blocks = simulationManifest.steps.map((step: any, index: any) => ({
      blockRef: step.id,
      title: step.title ?? `Simulation step ${index + 1}`,
      content:
        step.description ??
        step.narration ??
        `Interact with ${detail.asset.title} at step ${index + 1}.`,
      cues: [
        `checkpoint:${String(Boolean(step.checkpoint))}`,
        `actions:${step.actions.length}`,
      ],
    }));

    return this.buildResult(
      detail,
      sourceModalities,
      "simulation",
      manifestTypes,
      blocks,
      findSimulationManifest(detail.manifests)
        ? "existing-simulation-manifest"
        : "text-to-simulation-scaffold",
      `Simulation conversion for ${detail.asset.title}`,
      simulationManifest,
      simulation,
    );
  }

  private buildResult(
    detail: Awaited<ReturnType<ContentAssetReadService["getAssetDetail"]>> extends infer T
      ? NonNullable<T>
      : never,
    sourceModalities: ContentModality[],
    targetModality: ContentModality,
    manifestTypes: string[],
    blocks: ConvertedContentBlock[],
    strategy: string,
    summary: string,
    simulationManifest?: SimulationManifest,
    simulation?: ConversionSimulationSummary,
  ): ConversionResult {
    return {
      assetId: detail.asset.id,
      title: detail.asset.title,
      sourceModalities,
      targetModality,
      summary,
      blocks,
      manifests: simulationManifest
        ? ensureSimulationManifest(detail.manifests, simulationManifest)
        : detail.manifests,
      ...(simulation ? { simulation } : {}),
      metadata: {
        sourceAssetType: detail.asset.assetType,
        strategy,
        manifestTypes,
        generatedAt: new Date().toISOString(),
      },
    };
  }
}

function getManifestTypes(manifests: ArtifactManifest[]): string[] {
  return manifests.map((manifest: any) => manifest.manifestType);
}

function inferSourceModalities(
  manifests: Array<{ manifestType: string }>,
  blocks: Array<{ payload: Record<string, unknown> }>,
): ContentModality[] {
  const modalities = new Set<ContentModality>();

  if (blocks.length > 0) {
    modalities.add("text");
  }

  for (const manifest of manifests) {
    switch (manifest.manifestType) {
      case "animation":
        modalities.add("visual");
        break;
      case "simulation":
        modalities.add("simulation");
        break;
      case "worked_example":
        modalities.add("text");
        break;
      default:
        break;
    }
  }

  if (blocks.some((block: any) => typeof block.payload.narration === "string")) {
    modalities.add("audio");
  }

  return Array.from(modalities);
}

function findSimulationManifest(
  manifests: ArtifactManifest[],
): SimulationManifest | undefined {
  const manifest = manifests.find((item: any) => item.manifestType === "simulation");
  return manifest?.manifest as SimulationManifest | undefined;
}

function findAnimationManifest(
  manifests: ArtifactManifest[],
): AnimationManifest | undefined {
  const manifest = manifests.find((item: any) => item.manifestType === "animation");
  return manifest?.manifest as AnimationManifest | undefined;
}

function ensureSimulationManifest(
  manifests: ArtifactManifest[],
  simulationManifest: SimulationManifest,
): ArtifactManifest[] {
  if (manifests.some((manifest: any) => manifest.manifestType === "simulation")) {
    return manifests;
  }

  return [
    ...manifests,
    {
      id: `${simulationManifest.id}:derived-manifest`,
      assetId: simulationManifest.blockId ?? simulationManifest.id,
      manifestType: "simulation",
      manifest: simulationManifest,
      createdAt: simulationManifest.createdAt,
      updatedAt: simulationManifest.updatedAt,
    },
  ];
}

function buildDerivedSimulationManifest(
  detail: Awaited<ReturnType<ContentAssetReadService["getAssetDetail"]>> extends infer T
    ? NonNullable<T>
    : never,
): SimulationManifest {
  const now = new Date().toISOString();
  const domain = mapAssetDomainToSimulationDomain(detail.asset.domain);
  const entityId = `${detail.asset.id}-entity` as SimulationManifest["initialEntities"][number]["id"];
  const initialEntity = createSeedEntity(domain, entityId, detail.asset.title);
  const text = detail.blocks.map((block: any) => extractBlockText(block.payload)).join(" ");
  const steps = splitIntoSentences(text).slice(0, 4);

  return {
    id: `${detail.asset.id}-simulation` as SimulationManifest["id"],
    version: "1.0.0",
    title: `${detail.asset.title} Interactive Model`,
    description: keepFirstSentences(text, 2),
    domain,
    authorId: detail.asset.authorId as SimulationManifest["authorId"],
    tenantId: detail.asset.tenantId as SimulationManifest["tenantId"],
    moduleId: detail.asset.legacyModuleId as SimulationManifest["moduleId"],
    blockId: detail.blocks[0]?.id,
    canvas: {
      width: 1280,
      height: 720,
      backgroundColor: "#f8fafc",
      gridEnabled: true,
      zoomEnabled: true,
      panEnabled: true,
    },
    playback: {
      defaultSpeed: 1,
      allowSpeedChange: true,
      minSpeed: 0.5,
      maxSpeed: 2,
      allowScrubbing: true,
      autoPlay: false,
      loop: false,
    },
    initialEntities: [initialEntity],
    steps: (steps.length > 0 ? steps : [`Explore ${detail.asset.title}.`]).map(
      (sentence: any, index: any) => ({
        id: `${detail.asset.id}-step-${index + 1}` as SimulationManifest["steps"][number]["id"],
        orderIndex: index,
        title: `Step ${index + 1}`,
        description: sentence,
        narration: sentence,
        checkpoint: index === 0 || index === steps.length - 1,
        actions: [
          {
            action: "HIGHLIGHT",
            targetIds: [entityId],
            style: "primary",
          },
          {
            action: "ANNOTATE",
            targetId: entityId,
            text: sentence,
            position: "right",
          },
        ],
      }),
    ),
    accessibility: {
      altText: `Derived simulation scaffold for ${detail.asset.title}`,
      screenReaderNarration: true,
    },
    safety: {
      parameterBounds: { enforced: true, maxIterations: 500 },
      executionLimits: { maxSteps: 25, maxRuntimeMs: 30000 },
    },
    replay: {
      deterministic: true,
      seedStrategy: "perSession",
    },
    rendering: {
      requiredCapabilities: ["2d"],
      optionalCapabilities: ["3d"],
    },
    compliance: {
      dataRetentionDays: 30,
      analyticsConsentRequired: false,
      auditLevel: "basic",
    },
    createdAt: now,
    updatedAt: now,
    schemaVersion: "1.0.0",
  };
}

function mapAssetDomainToSimulationDomain(domain: string): SimulationDomain {
  switch (domain.toLowerCase()) {
    case "physics":
      return "PHYSICS";
    case "economics":
      return "ECONOMICS";
    case "chemistry":
      return "CHEMISTRY";
    case "biology":
      return "BIOLOGY";
    case "medicine":
      return "MEDICINE";
    case "engineering":
      return "ENGINEERING";
    case "mathematics":
    case "math":
      return "MATHEMATICS";
    case "computer_science":
    case "cs_discrete":
    case "cs":
      return "CS_DISCRETE";
    default:
      return "PHYSICS";
  }
}

function createSeedEntity(
  domain: SimulationDomain,
  entityId: SimulationManifest["initialEntities"][number]["id"],
  title: string,
): SimEntity {
  switch (domain) {
    case "BIOLOGY":
      return {
        id: entityId,
        type: "cell",
        x: 320,
        y: 240,
        width: 160,
        height: 160,
        radius: 80,
        label: title,
      };
    case "CHEMISTRY":
      return {
        id: entityId,
        type: "atom",
        element: "C",
        x: 320,
        y: 240,
        width: 48,
        height: 48,
        label: title,
      };
    case "ECONOMICS":
      return {
        id: entityId,
        type: "stock",
        value: 100,
        x: 320,
        y: 240,
        width: 220,
        height: 96,
        label: title,
      };
    default:
      return {
        id: entityId,
        type: "rigidBody",
        mass: 1,
        x: 320,
        y: 240,
        width: 120,
        height: 60,
        label: title,
        shape: "rect",
      };
  }
}
