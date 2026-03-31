/**
 * Content Variation Service
 *
 * Deterministic content adaptations for difficulty, modality, and explanation depth.
 *
 * @doc.type service
 * @doc.purpose Generate reusable content variants for adaptive delivery
 * @doc.layer product
 * @doc.pattern Content Variants
 */

import type { ArtifactManifest } from "@tutorputor/contracts/v1/content-studio";
import { ContentAssetReadService } from "../asset/read-service.js";
import {
  extractBlockText,
  keepFirstSentences,
} from "../asset/text-extraction.js";

export interface AdaptedContentVariant {
  variantId: string;
  assetId: string;
  family: "difficulty" | "modality" | "explanation";
  key: string;
  title: string;
  summary: string;
  blocks: Array<{
    blockRef: string;
    text: string;
    annotations: Record<string, string>;
  }>;
  manifests: ArtifactManifest[];
  metadata: {
    sourceAssetId: string;
    generatedAt: string;
    strategy: string;
  };
}

export interface DifficultyVariantSet {
  easy: AdaptedContentVariant;
  medium: AdaptedContentVariant;
  hard: AdaptedContentVariant;
  expert: AdaptedContentVariant;
}

export interface ModalityVariantSet {
  visual: AdaptedContentVariant;
  auditory: AdaptedContentVariant;
  kinesthetic: AdaptedContentVariant;
  reading: AdaptedContentVariant;
}

export interface ExplanationVariantSet {
  minimal: AdaptedContentVariant;
  standard: AdaptedContentVariant;
  detailed: AdaptedContentVariant;
  scaffolded: AdaptedContentVariant;
}

export class ContentVariationService {
  constructor(private readonly readService: ContentAssetReadService) {}

  async generateDifficultyVariants(
    tenantId: string,
    assetId: string,
  ): Promise<DifficultyVariantSet> {
    const detail = await this.requireAssetDetail(tenantId, assetId);

    return {
      easy: this.createVariant(detail, "difficulty", "easy", {
        summaryPrefix: "Simplified walkthrough",
        transform: simplifyText,
      }),
      medium: this.createVariant(detail, "difficulty", "medium", {
        summaryPrefix: "Balanced explanation",
        transform: (text) => text,
      }),
      hard: this.createVariant(detail, "difficulty", "hard", {
        summaryPrefix: "Challenge-focused explanation",
        transform: (text) =>
          `${text}\n\nChallenge prompt: explain why the core idea still holds in a less familiar scenario.`,
      }),
      expert: this.createVariant(detail, "difficulty", "expert", {
        summaryPrefix: "Advanced extension",
        transform: (text) =>
          `${text}\n\nExpert extension: derive assumptions, edge cases, and a transfer application.`,
      }),
    };
  }

  async generateModalityVariants(
    tenantId: string,
    assetId: string,
  ): Promise<ModalityVariantSet> {
    const detail = await this.requireAssetDetail(tenantId, assetId);

    return {
      visual: this.createVariant(detail, "modality", "visual", {
        summaryPrefix: "Diagram-first explanation",
        transform: (text) =>
          `Visual cue: sketch the relationships before reading.\n${text}`,
      }),
      auditory: this.createVariant(detail, "modality", "auditory", {
        summaryPrefix: "Talk-through explanation",
        transform: (text) =>
          `Say this aloud in your own words, then compare to the explanation.\n${text}`,
      }),
      kinesthetic: this.createVariant(detail, "modality", "kinesthetic", {
        summaryPrefix: "Action-oriented explanation",
        transform: (text) =>
          `Try it: interact with an example, manipulate a variable, or act out the process.\n${text}`,
      }),
      reading: this.createVariant(detail, "modality", "reading", {
        summaryPrefix: "Text-first explanation",
        transform: (text) =>
          `${text}\n\nReading check: highlight the sentence that best states the core rule.`,
      }),
    };
  }

  async generateExplanationVariants(
    tenantId: string,
    assetId: string,
  ): Promise<ExplanationVariantSet> {
    const detail = await this.requireAssetDetail(tenantId, assetId);

    return {
      minimal: this.createVariant(detail, "explanation", "minimal", {
        summaryPrefix: "Core concept only",
        transform: (text) => keepFirstSentences(text, 2),
      }),
      standard: this.createVariant(detail, "explanation", "standard", {
        summaryPrefix: "Standard explanation",
        transform: (text) => text,
      }),
      detailed: this.createVariant(detail, "explanation", "detailed", {
        summaryPrefix: "Detailed explanation",
        transform: (text) =>
          `${text}\n\nWhy it matters: connect the idea to a second example and a misconception check.`,
      }),
      scaffolded: this.createVariant(detail, "explanation", "scaffolded", {
        summaryPrefix: "Step-by-step scaffold",
        transform: scaffoldText,
      }),
    };
  }

  private async requireAssetDetail(tenantId: string, assetId: string) {
    const detail = await this.readService.getAssetDetail(tenantId, assetId);
    if (!detail) {
      throw new Error(`Content asset ${assetId} not found`);
    }
    return detail;
  }

  private createVariant(
    detail: Awaited<ReturnType<ContentAssetReadService["getAssetDetail"]>> extends infer T
      ? NonNullable<T>
      : never,
    family: AdaptedContentVariant["family"],
    key: string,
    options: {
      summaryPrefix: string;
      transform: (text: string) => string;
    },
  ): AdaptedContentVariant {
    return {
      variantId: `${detail.asset.id}:${family}:${key}`,
      assetId: detail.asset.id,
      family,
      key,
      title: `${detail.asset.title} (${key})`,
      summary: `${options.summaryPrefix} for ${detail.asset.title}`,
      blocks: detail.blocks.map((block) => {
        const sourceText = extractBlockText(block.payload);
        return {
          blockRef: block.blockRef,
          text: options.transform(sourceText),
          annotations: {
            family,
            key,
            sourceBlockType: block.blockType,
          },
        };
      }),
      manifests: detail.manifests,
      metadata: {
        sourceAssetId: detail.asset.id,
        generatedAt: new Date().toISOString(),
        strategy: `${family}:${key}`,
      },
    };
  }
}

function simplifyText(text: string): string {
  const trimmed = keepFirstSentences(text, 3);
  return `Key idea: ${trimmed}\n\nCheckpoint: restate the rule in one short sentence.`;
}

function scaffoldText(text: string): string {
  const steps = keepFirstSentences(text, 3)
    .split(/[.!?]\s+/)
    .map((step) => step.trim())
    .filter(Boolean);

  if (steps.length === 0) {
    return "1. Read the prompt carefully.\n2. Identify the core idea.\n3. Check your understanding.";
  }

  return steps
    .map((step, index) => `${index + 1}. ${step}`)
    .join("\n");
}
