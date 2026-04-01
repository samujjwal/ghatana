/**
 * Intelligent Content Cache
 *
 * Shared caching layer for planning and adaptive content decisions.
 *
 * @doc.type service
 * @doc.purpose Intelligent caching for generation planning and adaptive delivery
 * @doc.layer product
 * @doc.pattern Cache
 */

import crypto from "node:crypto";
import type Redis from "ioredis";

import type {
  GenerationRequestConfig,
  PlannedAssetDescriptor,
  ReviewPath,
  GenerationCostEstimate,
  GenerationRoutingDecision,
} from "../types.js";
import type { RiskLevel } from "@tutorputor/contracts/v1/types";

export interface PlanningCacheRequest {
  domain: string;
  title: string;
  conceptId?: string;
  targetGrades?: string[];
  requestConfig?: GenerationRequestConfig;
}

export interface CachedPlanningBlueprint {
  plannedAssets: PlannedAssetDescriptor[];
  artifactNeeds: Record<string, number>;
  riskLevel: RiskLevel;
  riskFactors: string[];
  reviewPath: ReviewPath;
  estimatedCost: GenerationCostEstimate;
  routingDecision: GenerationRoutingDecision;
}

interface CacheEnvelope {
  createdAt: string;
  blueprint: CachedPlanningBlueprint;
}

export class IntelligentContentCache {
  private readonly fallback = new Map<string, CacheEnvelope>();

  constructor(private readonly redis?: Redis) {}

  generatePlanningCacheKey(request: PlanningCacheRequest): string {
    const learnerArchetype =
      request.requestConfig?.learnerArchetype ??
      this.classifyLearnerArchetype(request.requestConfig);

    return crypto
      .createHash("sha256")
      .update(
        JSON.stringify({
          domain: request.domain.toLowerCase(),
          title: request.title.trim().toLowerCase(),
          conceptId: request.conceptId ?? null,
          targetGrades: [...(request.targetGrades ?? [])].sort(),
          minQualityScore: request.requestConfig?.minQualityScore ?? 0.75,
          urgent: request.requestConfig?.urgent ?? false,
          learnerArchetype,
        }),
      )
      .digest("hex");
  }

  classifyLearnerArchetype(
    requestConfig?: GenerationRequestConfig,
  ): string {
    if (requestConfig?.learnerArchetype) {
      return requestConfig.learnerArchetype;
    }

    const quality = requestConfig?.minQualityScore ?? 0.75;
    const urgent = requestConfig?.urgent ?? false;

    if (quality >= 0.9 && urgent) return "high-rigor-urgent";
    if (quality >= 0.9) return "high-rigor";
    if (urgent) return "rapid-turnaround";
    return "balanced";
  }

  async getPlanningBlueprint(
    cacheKey: string,
  ): Promise<CachedPlanningBlueprint | null> {
    if (this.redis) {
      const raw = await this.redis.get(`content:plan:${cacheKey}`);
      if (!raw) return null;
      return parseEnvelope(raw)?.blueprint ?? null;
    }

    return this.fallback.get(cacheKey)?.blueprint ?? null;
  }

  async setPlanningBlueprint(
    cacheKey: string,
    blueprint: CachedPlanningBlueprint,
    ttlSeconds = 3600,
  ): Promise<void> {
    const envelope: CacheEnvelope = {
      createdAt: new Date().toISOString(),
      blueprint,
    };

    if (this.redis) {
      await this.redis.set(
        `content:plan:${cacheKey}`,
        JSON.stringify(envelope),
        "EX",
        ttlSeconds,
      );
      return;
    }

    this.fallback.set(cacheKey, envelope);
  }

  shouldCacheBlueprint(blueprint: CachedPlanningBlueprint): boolean {
    const estimatedSpend = blueprint.routingDecision.estimatedSpendUsd;
    const cacheCost = 0.001;
    const expectedSavings = Math.max(estimatedSpend - cacheCost, 0);
    return expectedSavings >= 0.01 || blueprint.estimatedCost.totalTokens >= 4000;
  }
}

function parseEnvelope(raw: string): CacheEnvelope | null {
  try {
    return JSON.parse(raw) as CacheEnvelope;
  } catch {
    return null;
  }
}
