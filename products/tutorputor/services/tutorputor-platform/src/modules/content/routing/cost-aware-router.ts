/**
 * Cost-Aware Generation Router
 *
 * @doc.type service
 * @doc.purpose Route generation requests to cost-appropriate models
 * @doc.layer product
 * @doc.pattern Routing
 */

type GenerationRequestConfig = {
  minQualityScore?: number;
  urgent?: boolean;
  maxBudgetUsd?: number;
};

type GenerationRoutingDecision = {
  selectedModel: string;
  provider: string;
  useCache: boolean;
  cacheKey: string;
  estimatedSpendUsd: number;
  warning?: string;
};

export interface BudgetContext {
  remainingDailyBudgetUsd: number;
}

export interface RoutingRequest {
  cacheKey: string;
  estimatedTokens: number;
  requestConfig?: GenerationRequestConfig;
  cacheAvailable: boolean;
}

interface ModelCostProfile {
  provider: string;
  inputCostPer1k: number;
  outputCostPer1k: number;
}

const MODEL_COSTS: Record<string, ModelCostProfile> = {
  "gpt-4": { provider: "openai", inputCostPer1k: 0.03, outputCostPer1k: 0.06 },
  "gpt-4o-mini": {
    provider: "openai",
    inputCostPer1k: 0.00015,
    outputCostPer1k: 0.0006,
  },
  "ollama-local": {
    provider: "local",
    inputCostPer1k: 0,
    outputCostPer1k: 0,
  },
};

export class CostAwareGenerationRouter {
  routeRequest(
    request: RoutingRequest,
    budget: BudgetContext,
  ): GenerationRoutingDecision {
    const minQualityScore = request.requestConfig?.minQualityScore ?? 0.75;
    const urgent = request.requestConfig?.urgent ?? false;
    const maxBudgetUsd = request.requestConfig?.maxBudgetUsd;

    if (request.cacheAvailable && !urgent) {
      return {
        selectedModel: "cache",
        provider: "internal-cache",
        useCache: true,
        cacheKey: request.cacheKey,
        estimatedSpendUsd: 0,
      };
    }

    if (
      minQualityScore >= 0.9 &&
      this.canAfford("gpt-4", request.estimatedTokens, budget, maxBudgetUsd)
    ) {
      return this.useModel("gpt-4", request);
    }

    if (
      minQualityScore >= 0.7 &&
      this.canAfford("gpt-4o-mini", request.estimatedTokens, budget, maxBudgetUsd)
    ) {
      return this.useModel("gpt-4o-mini", request);
    }

    const warning =
      urgent && minQualityScore >= 0.8
        ? "Budget threshold exceeded, using local fallback model"
        : undefined;

    return {
      ...this.useModel("ollama-local", request),
      ...(warning ? { warning } : {}),
    };
  }

  private useModel(
    model: keyof typeof MODEL_COSTS,
    request: RoutingRequest,
  ): GenerationRoutingDecision {
    const profile = MODEL_COSTS[model];
    if (!profile) {
      return {
        selectedModel: "ollama-local",
        provider: "local",
        useCache: false,
        cacheKey: request.cacheKey,
        estimatedSpendUsd: 0,
        warning: `Unknown model profile for ${model}, using local fallback`,
      };
    }
    return {
      selectedModel: model,
      provider: profile.provider,
      useCache: false,
      cacheKey: request.cacheKey,
      estimatedSpendUsd: this.estimateSpendUsd(model, request.estimatedTokens),
    };
  }

  private canAfford(
    model: keyof typeof MODEL_COSTS,
    estimatedTokens: number,
    budget: BudgetContext,
    maxBudgetUsd?: number,
  ): boolean {
    const estimated = this.estimateSpendUsd(model, estimatedTokens);
    if (estimated > budget.remainingDailyBudgetUsd) {
      return false;
    }
    if (maxBudgetUsd !== undefined && estimated > maxBudgetUsd) {
      return false;
    }
    return true;
  }

  private estimateSpendUsd(
    model: keyof typeof MODEL_COSTS,
    estimatedTokens: number,
  ): number {
    const profile = MODEL_COSTS[model];
    if (!profile) {
      return 0;
    }
    const inputTokens = estimatedTokens * 0.65;
    const outputTokens = estimatedTokens * 0.35;

    return Number(
      (
        (inputTokens / 1000) * profile.inputCostPer1k +
        (outputTokens / 1000) * profile.outputCostPer1k
      ).toFixed(4),
    );
  }
}
