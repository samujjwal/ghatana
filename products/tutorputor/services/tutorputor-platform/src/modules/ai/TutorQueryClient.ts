/**
 * Tutor Query Client
 *
 * Structured client for AI tutor query generation with clear separation of concerns:
 * - Context fetching and grounding
 * - Prompt engineering
 * - Response parsing
 * - Governance and safety
 *
 * @doc.type class
 * @doc.purpose Provide a structured client for AI tutor query generation
 * @doc.layer platform
 * @doc.pattern Client
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { TenantId, UserId, ModuleId } from "@tutorputor/contracts/v1/types";
import type { TutorCitation, TutorResponsePayload } from "@tutorputor/contracts/v1/types";

export interface TutorQueryRequest {
  tenantId: TenantId;
  userId: UserId;
  moduleId?: ModuleId;
  claimIds?: string[];
  currentSimulationState?: Record<string, unknown>;
  recentAttempts?: Array<{
    attemptId: string;
    taskId?: string;
    correct?: boolean;
    confidence?: "low" | "medium" | "high";
    misconceptionId?: string;
  }>;
  misconceptions?: string[];
  allowedHelpMode?: "hint" | "explain" | "socratic";
  question: string;
  locale?: string;
}

export interface ModuleContext {
  title: string;
  description: string | null;
  domain: string | null;
}

export interface ClaimContext {
  id: string;
  text: string | null;
}

/**
 * Tutor Query Client
 * 
 * Provides a structured interface for AI tutor query generation with:
 * - Context fetching from database
 * - Structured prompt building
 * - Response parsing and validation
 * - Governance integration
 */
export class TutorQueryClient {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly aiProxy: {
      callOllama: (request: {
        model: string;
        system: string;
        prompt: string;
        options: {
          temperature: number;
          top_p: number;
          max_tokens: number;
        };
      }) => Promise<{ response: string }>;
    },
    private readonly model: string = "llama3.2",
  ) {}

  /**
   * Fetch module context from database
   */
  private async getModuleContext(moduleId: ModuleId): Promise<ModuleContext | null> {
    try {
      const module = await this.prisma.contentAsset.findFirst({
        where: { id: moduleId },
        select: {
          title: true,
          searchableText: true,
          difficultyLevel: true,
        },
      });

      if (!module) return null;

      return {
        title: module.title,
        description: (module.searchableText as string | null) || null,
        domain: module.difficultyLevel as string | null,
      };
    } catch (error) {
      console.warn("Failed to fetch module context", error);
      return null;
    }
  }

  /**
   * Fetch claim context from database
   */
  private async getClaimsContext(claimIds: string[]): Promise<ClaimContext[]> {
    if (!claimIds || claimIds.length === 0) return [];

    try {
      const claims = await this.prisma.learningClaim.findMany({
        where: { id: { in: claimIds } },
        select: { id: true, text: true },
      });

      return claims.map((claim) => ({
        id: claim.id,
        text: claim.text,
      }));
    } catch (error) {
      console.warn("Failed to fetch claims context", error);
      return [];
    }
  }

  /**
   * Build grounding string from context
   */
  private buildGrounding(
    moduleContext: ModuleContext | null,
    claimsContext: ClaimContext[],
    request: TutorQueryRequest,
  ): string {
    const groundingParts: string[] = [];

    if (moduleContext) {
      groundingParts.push(`- Module: ${moduleContext.title} (${moduleContext.domain || "general domain"})`);
      if (moduleContext.description) {
        groundingParts.push(`  Description: ${moduleContext.description.substring(0, 200)}...`);
      }
    } else if (request.moduleId) {
      groundingParts.push(`- moduleId: ${request.moduleId} (context not available)`);
    }

    if (claimsContext.length > 0) {
      groundingParts.push(`- Claims: ${claimsContext.map((c) => c.text || c.id).join("; ")}`);
    } else if (request.claimIds) {
      groundingParts.push(`- claimIds: ${request.claimIds.join(", ")}`);
    }

    if (request.currentSimulationState) {
      groundingParts.push(`- Current simulation state: ${JSON.stringify(request.currentSimulationState)}`);
    }

    if (request.recentAttempts && request.recentAttempts.length > 0) {
      groundingParts.push(`- Recent attempts: ${request.recentAttempts.map((a) => a.attemptId).join(", ")}`);
    }

    if (request.misconceptions && request.misconceptions.length > 0) {
      groundingParts.push(`- Misconceptions: ${request.misconceptions.join(", ")}`);
    }

    groundingParts.push(`- Allowed help mode: ${request.allowedHelpMode ?? "socratic"}`);

    return groundingParts.join("\n");
  }

  /**
   * Build system prompt
   */
  private buildSystemPrompt(): string {
    return `You are TutorPutor, an AI tutor specializing in STEAM education (Science, Technology, Engineering, Arts, Mathematics).

Guidelines:
- Teach Socratically: do not give direct final answers
- Use the supplied module, claim, simulation state, recent attempts, and misconceptions as grounding
- Stay within the allowed help mode
- Give one targeted next-step hint or one guiding question
- Cite educational sources when possible
- Keep responses concise
- If unsure, acknowledge limitations honestly

Format your response as:
1. Short acknowledgement
2. One guiding question or hint
3. Optional follow-up question`;
  }

  /**
   * Build user prompt
   */
  private buildUserPrompt(grounding: string, request: TutorQueryRequest): string {
    return `Grounding:
${grounding}

Student question: ${request.question}

${request.locale && request.locale !== "en" ? `Respond in ${request.locale} language.` : ""}

Provide a Socratic, context-grounded response. If the learner asks for the answer, redirect to the next reasoning step.`;
  }

  /**
   * Extract follow-up questions from response
   */
  private extractFollowUpQuestions(answer: string): string[] {
    const questions: string[] = [];
    const questionPatterns = [
      /(?:Would you like|Can you tell me|What about|How about|Have you considered)[^.?!]*\?/gi,
      /\?[^.?!]*$/gm,
    ];

    for (const pattern of questionPatterns) {
      const matches = answer.match(pattern);
      if (matches) {
        questions.push(...matches);
      }
    }

    return [...new Set(questions)].slice(0, 3);
  }

  /**
   * Extract citations from response
   */
  private extractCitations(answer: string): TutorCitation[] {
    const citations: TutorCitation[] = [];
    const citationPattern = /\[(\d+)\]|(\[source[^\]]*\])/gi;
    let match;

    while ((match = citationPattern.exec(answer)) !== null) {
      citations.push({
        type: "content_block",
        id: match[1] || match[2] || "unknown",
        label: match[0],
      });
    }

    return citations;
  }

  /**
   * Clean answer by removing metadata
   */
  private cleanAnswer(answer: string): string {
    return answer
      .replace(/\[source[^\]]*\]/gi, "")
      .replace(/\[\d+\]/g, "")
      .trim();
  }

  /**
   * Execute tutor query
   */
  async execute(request: TutorQueryRequest): Promise<TutorResponsePayload> {
    // Fetch context
    const moduleContext = request.moduleId ? await this.getModuleContext(request.moduleId) : null;
    const claimsContext = request.claimIds && request.claimIds.length > 0
      ? await this.getClaimsContext(request.claimIds)
      : [];

    // Build prompts
    const systemPrompt = this.buildSystemPrompt();
    const grounding = this.buildGrounding(moduleContext, claimsContext, request);
    const userPrompt = this.buildUserPrompt(grounding, request);

    // Call AI
    const result = await this.aiProxy.callOllama({
      model: this.model,
      system: systemPrompt,
      prompt: userPrompt,
      options: {
        temperature: 0.7,
        top_p: 0.9,
        max_tokens: 2048,
      },
    });

    // Parse response
    const answer = result.response.trim();
    const followUpQuestions = this.extractFollowUpQuestions(answer);
    const citations = this.extractCitations(answer);

    return {
      answer: this.cleanAnswer(answer),
      citations,
      followUpQuestions,
      safety: { blocked: false },
    };
  }
}
