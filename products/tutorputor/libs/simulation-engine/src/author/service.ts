/**
 * Simulation Author Service Implementation
 *
 * @doc.type class
 * @doc.purpose AI-powered simulation manifest generation and refinement
 * @doc.layer product
 * @doc.pattern Service
 */

import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import type {
  SimulationAuthorService,
  GenerateManifestRequest,
  GenerateManifestResult,
  RefineManifestRequest,
  SuggestParametersRequest,
  SuggestParametersResult,
  ManifestValidationResult,
} from "@ghatana/tutorputor-contracts/v1/simulation/services";
import type {
  SimulationManifest,
  SimulationId,
  SimulationDomain,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";
import { getPromptPack } from "./prompt-packs";
import { validateManifest } from "./validation";
import {
  createMultiProviderAIService,
  type AIProviderConfig,
} from "./ai-providers";

/**
 * Configuration for the author service.
 */
export interface SimAuthorConfig {
  providers: Array<{
    name: string;
    config: AIProviderConfig;
    isDefault?: boolean;
  }>;
  maxRetries?: number;
  cacheEnabled?: boolean;
  rateLimit?: {
    requestsPerMinute: number;
    tokensPerMinute: number;
  };
}

/**
 * Rate limiter state.
 */
interface RateLimitState {
  requests: number[];
  tokens: number[];
}

/**
 * Health-aware simulation author service.
 */
export type HealthAwareSimAuthorService = SimulationAuthorService & {
  checkHealth: () => Promise<boolean>;
};

/**
 * Create the simulation author service.
 *
 * @doc.type function
 * @doc.purpose Factory function for simulation author service
 * @doc.layer product
 * @doc.pattern Factory
 */
export function createSimulationAuthorService(
  prisma: TutorPrismaClient,
  config: SimAuthorConfig,
): HealthAwareSimAuthorService {
  const maxRetries = config.maxRetries || 3;
  const rateLimitState: RateLimitState = { requests: [], tokens: [] };

  // Initialize multi-provider AI service
  const aiService = createMultiProviderAIService(config.providers);

  /**
   * Check rate limits.
   */
  function checkRateLimit(): boolean {
    if (!config.rateLimit) return true;

    const now = Date.now();
    const oneMinuteAgo = now - 60000;

    // Clean old entries
    rateLimitState.requests = rateLimitState.requests.filter(
      (t) => t > oneMinuteAgo,
    );
    rateLimitState.tokens = rateLimitState.tokens.filter(
      (t) => t > oneMinuteAgo,
    );

    return rateLimitState.requests.length < config.rateLimit.requestsPerMinute;
  }

  /**
   * Record a request for rate limiting.
   */
  function recordRequest(tokens: number): void {
    const now = Date.now();
    rateLimitState.requests.push(now);
    for (let i = 0; i < tokens; i++) {
      rateLimitState.tokens.push(now);
    }
  }

  /**
   * Call AI provider with retry logic.
   */
  async function callAI(
    systemPrompt: string,
    userPrompt: string,
    retries = maxRetries,
    providerName?: string,
  ): Promise<{ content: string; confidence: number; provider: string }> {
    if (!checkRateLimit()) {
      throw new Error("Rate limit exceeded. Please try again later.");
    }

    let lastError: Error | null = null;

    for (let attempt = 0; attempt < retries; attempt++) {
      try {
        const response = await aiService.generate(
          {
            prompt: userPrompt,
            systemPrompt,
            temperature: 0.7,
            maxTokens: 4000,
          },
          providerName,
        );

        // Estimate confidence based on response characteristics
        const confidence = estimateConfidence(response.content);

        return {
          content: response.content,
          confidence,
          provider: response.provider,
        };
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error));

        // Exponential backoff
        if (attempt < retries - 1) {
          const delay = Math.pow(2, attempt) * 1000;
          await new Promise((resolve) => setTimeout(resolve, delay));
        }
      }
    }

    throw lastError || new Error("AI generation failed after retries");
  }

  /**
   * Estimate confidence based on response characteristics.
   */
  function estimateConfidence(content: string): number {
    try {
      const parsed = JSON.parse(content);
      let confidence = 0.5;

      // Higher confidence if all required fields are present
      if (parsed.id && parsed.title && parsed.domain && parsed.steps) {
        confidence += 0.2;
      }

      // Higher confidence if there are multiple steps
      if (parsed.steps?.length > 3) {
        confidence += 0.1;
      }

      // Higher confidence if entities are defined
      if (parsed.initialEntities?.length > 0) {
        confidence += 0.1;
      }

      // Lower confidence if there are validation issues
      if (parsed.needsReview) {
        confidence -= 0.2;
      }

      return Math.min(0.95, Math.max(0.1, confidence));
    } catch {
      return 0.3;
    }
  }

  /**
   * Generate unique simulation ID.
   */
  function generateSimulationId(): SimulationId {
    const timestamp = Date.now().toString(36);
    const random = Math.random().toString(36).substring(2, 8);
    return `sim_${timestamp}_${random}` as SimulationId;
  }

  /**
   * Build the complete system prompt with few-shot examples.
   */
  function buildSystemPrompt(domain: SimulationDomain): string {
    const promptPack = getPromptPack(domain);
    if (!promptPack) {
      return `You are an expert in creating educational simulations.
Generate a SimulationManifest JSON for the requested topic.
Ensure all required fields are present and the manifest is valid.`;
    }

    let prompt = promptPack.systemPrompt + "\n\n";
    prompt +=
      "Output format: Return ONLY valid JSON matching the SimulationManifest schema.\n\n";
    prompt += "Constraints:\n";
    promptPack.constraints.forEach((c) => {
      prompt += `- ${c}\n`;
    });

    return prompt;
  }

  /**
   * Build user prompt with examples.
   */
  function buildUserPrompt(
    domain: SimulationDomain,
    userPrompt: string,
    constraints?: GenerateManifestRequest["constraints"],
    options?: GenerateManifestRequest["options"],
  ): string {
    const promptPack = getPromptPack(domain);
    let fullPrompt = "";

    // Add few-shot examples if available
    if (promptPack && promptPack.fewShotExamples.length > 0) {
      fullPrompt += "Here are some examples:\n\n";
      promptPack.fewShotExamples.slice(0, 2).forEach((example, i) => {
        fullPrompt += `Example ${i + 1}:\nUser: ${example.userPrompt}\nAssistant: ${example.assistantResponse}\n\n`;
      });
      fullPrompt += "---\n\n";
    }

    fullPrompt += `Now generate a simulation for: ${userPrompt}\n`;

    if (constraints) {
      fullPrompt += "\nConstraints:\n";
      if (constraints.maxSteps) {
        fullPrompt += `- Maximum ${constraints.maxSteps} steps\n`;
      }
      if (constraints.maxEntities) {
        fullPrompt += `- Maximum ${constraints.maxEntities} entities\n`;
      }
      if (constraints.targetDuration) {
        fullPrompt += `- Target duration: ${constraints.targetDuration} seconds\n`;
      }
    }

    if (options) {
      fullPrompt += "\nPreferences:\n";
      if (options.complexity) {
        fullPrompt += `- Complexity level: ${options.complexity}\n`;
      }
      if (options.includeAnnotations) {
        fullPrompt += `- Include educational annotations/labels for key steps\n`;
      }
    }

    return fullPrompt;
  }

  /**
   * Parse and enhance the AI response.
   */
  function parseAndEnhanceManifest(
    content: string,
    request: GenerateManifestRequest,
  ): SimulationManifest {
    const parsed = JSON.parse(content);

    // Ensure required fields
    const manifest: SimulationManifest = {
      id: parsed.id || generateSimulationId(),
      version: parsed.version || "1.0.0",
      title: parsed.title || "Untitled Simulation",
      description: parsed.description,
      domain: request.domain || parsed.domain || "CS_DISCRETE",
      domainMetadata: parsed.domainMetadata,
      authorId: request.userId,
      tenantId: request.tenantId,
      needsReview: parsed.needsReview ?? false,
      moduleId: undefined,
      canvas: parsed.canvas || {
        width: 800,
        height: 600,
        backgroundColor: "#1a1a2e",
      },
      playback: parsed.playback || {
        defaultSpeed: 1,
        allowSpeedChange: true,
      },
      initialEntities: parsed.initialEntities || [],
      steps: parsed.steps || [],
      accessibility: parsed.accessibility,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      schemaVersion: "1.0.0",
    };

    return manifest;
  }

  return {
    async generateManifest(
      request: GenerateManifestRequest,
    ): Promise<GenerateManifestResult> {
      const domain = request.domain || "CS_DISCRETE";
      const systemPrompt = buildSystemPrompt(domain);
      const userPrompt = buildUserPrompt(
        domain,
        request.prompt,
        request.constraints,
        request.options,
      );

      const { content, confidence } = await callAI(systemPrompt, userPrompt);
      const manifest = parseAndEnhanceManifest(content, request);

      // Validate the generated manifest
      const validation = validateManifest(manifest);
      const needsReview = !validation.valid || confidence < 0.7;

      // Update needsReview based on validation and confidence
      manifest.needsReview = needsReview;
      if (needsReview && validation.warnings.length > 0) {
        manifest.reviewNotes = validation.warnings
          .map((w) => w.message)
          .join("; ");
      }

      // Cache the manifest if enabled
      if (config.cacheEnabled) {
        await prisma.simulationManifest.upsert({
          where: { id: manifest.id as string },
          create: {
            id: manifest.id as string,
            tenantId: request.tenantId as string,
            authorId: request.userId as string,
            title: manifest.title,
            domain: manifest.domain,
            manifest: manifest as unknown as Record<string, unknown>,
            needsReview,
            schemaVersion: manifest.schemaVersion,
          },
          update: {
            manifest: manifest as unknown as Record<string, unknown>,
            needsReview,
            updatedAt: new Date(),
          },
        });
      }

      return {
        manifest,
        confidence,
        needsReview,
        suggestions: validation.warnings.map((w) => w.message),
      };
    },

    async refineManifest(
      request: RefineManifestRequest,
    ): Promise<GenerateManifestResult> {
      const domain = request.manifest.domain;
      const systemPrompt =
        buildSystemPrompt(domain) +
        `

You are refining an existing simulation manifest. Preserve the structure and IDs where possible.
Only modify the parts specified by the user's refinement request.`;

      const userPrompt = `Current manifest:
${JSON.stringify(request.manifest, null, 2)}

Refinement request: ${request.refinement}

${request.targetSteps?.length ? `Focus on these steps: ${request.targetSteps.join(", ")}` : ""}

Return the complete refined manifest as JSON.`;

      const { content, confidence } = await callAI(systemPrompt, userPrompt);
      const manifest = parseAndEnhanceManifest(content, {
        tenantId: request.tenantId,
        userId: request.userId,
        prompt: request.refinement,
        domain,
      });

      // Preserve original ID and author
      manifest.id = request.manifest.id;
      manifest.authorId = request.manifest.authorId;
      manifest.createdAt = request.manifest.createdAt;

      const validation = validateManifest(manifest);
      const needsReview = !validation.valid || confidence < 0.7;
      manifest.needsReview = needsReview;

      return {
        manifest,
        confidence,
        needsReview,
        suggestions: validation.warnings.map((w) => w.message),
      };
    },

    async suggestParameters(
      request: SuggestParametersRequest,
    ): Promise<SuggestParametersResult> {
      const promptPack = getPromptPack(request.domain);

      const systemPrompt = `You are an expert in ${request.domain} simulations.
Suggest realistic parameters for the given context.
Return a JSON object with a "suggestions" array.
Each suggestion should have: param (parameter name), value (suggested value), rationale (why this value), confidence (0-1).`;

      const userPrompt = `Domain: ${request.domain}
Context: ${request.context}
${request.currentParams ? `Current parameters: ${JSON.stringify(request.currentParams)}` : ""}

${promptPack ? `Available parameters: ${promptPack.entityTypes.join(", ")}` : ""}

Suggest appropriate parameter values.`;

      const { content } = await callAI(systemPrompt, userPrompt);
      const parsed = JSON.parse(content);

      return {
        suggestions: parsed.suggestions || [],
      };
    },

    async validateManifest(
      manifest: SimulationManifest,
    ): Promise<ManifestValidationResult> {
      return validateManifest(manifest);
    },

    async checkHealth(): Promise<boolean> {
      try {
        await prisma.$queryRaw`SELECT 1`;
        return true;
      } catch {
        return false;
      }
    },
  };
}
