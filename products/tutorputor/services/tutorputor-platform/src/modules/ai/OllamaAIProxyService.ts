/**
 * Ollama AI Proxy Service Implementation
 *
 * Connects to the tutorputor-ai-proxy service (or directly to Ollama)
 * for AI-powered features like tutoring and content generation.
 *
 * @doc.type class
 * @doc.purpose AI proxy service implementation using Ollama
 * @doc.layer platform
 * @doc.pattern Service
 */

import type { AIProxyService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
  TenantId,
  UserId,
  ModuleId,
  TutorResponsePayload,
} from "@ghatana/tutorputor-contracts/v1/types";
import type {
  ParsedIntent,
  IntentParams,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";

export class OllamaAIProxyService implements AIProxyService {
  private baseUrl: string;

  constructor(baseUrl = "http://localhost:3300") {
    this.baseUrl = baseUrl;
  }

  /**
   * Handle tutor query - main AI tutoring endpoint
   */
  async handleTutorQuery(args: {
    tenantId: TenantId;
    userId: UserId;
    moduleId?: ModuleId;
    question: string;
    locale?: string;
  }): Promise<TutorResponsePayload> {
    try {
      const response = await fetch(`${this.baseUrl}/api/ai/generate`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          tenantId: args.tenantId,
          userId: args.userId,
          question: args.question,
          locale: args.locale || "en",
          moduleId: args.moduleId,
        }),
      });

      if (!response.ok) {
        return {
          answer: `AI service returned an error: ${response.statusText}`,
          safety: { blocked: false },
        };
      }

      const data = (await response.json()) as Record<string, unknown>;
      const answer =
        (data.response as string) ||
        (data.content as string) ||
        (data.answer as string) ||
        JSON.stringify(data);

      return {
        answer,
        citations: (data.citations as TutorResponsePayload["citations"]) || [],
        followUpQuestions: (data.followUpQuestions as string[]) || [],
        safety: { blocked: false },
      };
    } catch (error) {
      console.error("Failed to call AI Proxy Service:", error);
      return {
        answer:
          "I'm sorry, I'm having trouble connecting to the AI service. Please ensure the AI Proxy service is running on port 3300.",
        safety: { blocked: false },
      };
    }
  }

  /**
   * Parse simulation intent from user input
   */
  parseSimulationIntent(args: {
    userInput: string;
    context?: string;
  }): Promise<ParsedIntent> {
    void args.context;
    return Promise.resolve({
      type: "unknown" as ParsedIntent["type"],
      confidence: 0,
      params: {} as IntentParams,
      originalInput: args.userInput,
      normalizedInput: args.userInput.toLowerCase().trim(),
    });
  }

  /**
   * Explain simulation behavior
   */
  explainSimulation(args: {
    manifest: unknown;
    query: string;
  }): Promise<string> {
    void args;
    return Promise.resolve(
      "Simulation explanation is not yet available. Please check back later.",
    );
  }

  /**
   * Generate learning unit draft
   */
  generateLearningUnitDraft(args: {
    topic: string;
    targetAudience: string;
    learningObjectives?: string[];
  }): Promise<unknown> {
    void args;
    return Promise.resolve({
      title: "",
      description: "",
      sections: [],
    });
  }

  /**
   * Parse content query for search
   */
  parseContentQuery(query: string): Promise<{
    domain?: string;
    difficulty?: string;
    tags?: string[];
    textSearch?: string;
  }> {
    void query;
    return Promise.resolve({});
  }
}
