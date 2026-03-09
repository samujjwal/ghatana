import OpenAI from "openai";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import type { AIProxyService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
  TutorResponsePayload,
  TenantId,
  UserId,
  ModuleId,
  TutorCitation,
} from "@ghatana/tutorputor-contracts/v1/types";
import type { ParsedIntent } from "@ghatana/tutorputor-contracts/v1/simulation/types";
import { WebSearchService, type WebSearchConfig } from "./web-search";

// Type alias for backwards compatibility
type PrismaClient = TutorPrismaClient;

export interface AIProxyServiceConfig {
  openaiApiKey?: string;
  model?: string;
  embeddingModel?: string;
  webSearchConfig?: Partial<WebSearchConfig>;
  useOllama?: boolean;
  ollamaBaseUrl?: string;
  ollamaModel?: string;
}

/**
 * RAG Context retrieved from the database.
 */
interface RAGContext {
  moduleTitle?: string;
  moduleDescription?: string;
  learningObjectives: string[];
  relevantContent: Array<{
    blockId: string;
    blockType: string;
    textContent: string;
    relevanceScore: number;
  }>;
  citations: TutorCitation[];
}

/**
 * TutorPutor AI Proxy Service with RAG support.
 *
 * @doc.type class
 * @doc.purpose AI tutoring with Retrieval-Augmented Generation from module content
 * @doc.layer product
 * @doc.pattern Service
 */
export class TutorPutorAIProxyService implements AIProxyService {
  private openai: OpenAI | null = null;
  private model: string;
  private embeddingModel: string;
  private prisma: TutorPrismaClient | null = null;
  private webSearchService: WebSearchService;
  private useOllama: boolean = false;
  private ollamaBaseUrl: string = "http://localhost:11434";
  private ollamaModel: string = "mistral";

  constructor(config: AIProxyServiceConfig = {}, prisma?: TutorPrismaClient) {
    // Ollama configuration (preferred for dev)
    this.useOllama = config.useOllama ?? process.env.USE_OLLAMA === "true";
    this.ollamaBaseUrl =
      config.ollamaBaseUrl ||
      process.env.OLLAMA_BASE_URL ||
      "http://localhost:11434";
    this.ollamaModel =
      config.ollamaModel || process.env.OLLAMA_MODEL || "mistral";

    // OpenAI configuration (fallback)
    const apiKey = config.openaiApiKey || process.env.OPENAI_API_KEY;
    if (!this.useOllama && apiKey) {
      this.openai = new OpenAI({ apiKey });
    }

    this.model = config.model || "gpt-4o-mini";
    this.embeddingModel = config.embeddingModel || "text-embedding-3-small";
    this.prisma = prisma || null;
    this.webSearchService = new WebSearchService(config.webSearchConfig);
  }

  /**
   * Check health of all available AI backends.
   * Returns status info for startup diagnostics.
   */
  async getHealthStatus(): Promise<{
    ollama: { available: boolean; baseUrl: string; model: string };
    openai: { available: boolean; model: string };
    webSearch: { available: boolean };
    activeBackend: string;
  }> {
    // Check Ollama
    let ollamaAvailable = false;
    if (this.useOllama) {
      try {
        const response = await fetch(`${this.ollamaBaseUrl}/api/tags`, {
          method: "GET",
        });
        ollamaAvailable = response.ok;
      } catch (error) {
        console.warn("[AI Proxy Health Check] Ollama unavailable:", error);
      }
    }

    // Check OpenAI
    const openaiAvailable = !!this.openai;

    // Determine active backend
    let activeBackend = "demo";
    if (ollamaAvailable) {
      activeBackend = `ollama (${this.ollamaModel})`;
    } else if (openaiAvailable) {
      activeBackend = `openai (${this.model})`;
    } else if (this.webSearchService) {
      activeBackend = "web-search-fallback";
    }

    return {
      ollama: {
        available: ollamaAvailable,
        baseUrl: this.ollamaBaseUrl,
        model: this.ollamaModel,
      },
      openai: {
        available: openaiAvailable,
        model: this.model,
      },
      webSearch: {
        available: !!this.webSearchService,
      },
      activeBackend,
    };
  }

  async handleTutorQuery(args: {
    tenantId: TenantId;
    userId: UserId;
    moduleId?: ModuleId;
    question: string;
    locale?: string;
  }): Promise<TutorResponsePayload> {
    const { tenantId, question, moduleId } = args;

    // Retrieve RAG context from database
    const context = await this.retrieveRAGContext(tenantId, moduleId, question);

    // Build pedagogy-aware prompt with retrieved context
    const prompt = this.buildTutorPrompt(question, context);

    // Call LLM (or stub if no API key)
    const answer = await this.callLLM(prompt, context.moduleTitle);

    // Generate contextual follow-up questions
    const followUpQuestions = await this.generateFollowUpQuestions(
      question,
      answer,
      context,
    );

    return {
      answer,
      followUpQuestions,
      citations: context.citations,
      safety: {
        blocked: false,
      },
    };
  }

  async parseSimulationIntent(args: {
    userInput: string;
    context?: string;
  }): Promise<ParsedIntent> {
    const { userInput, context } = args;

    if (!this.openai) {
      console.warn("OpenAI client not initialized. Returning unknown intent.");
      return {
        type: "unknown",
        confidence: 0,
        params: {},
        originalInput: userInput,
        normalizedInput: userInput.toLowerCase(),
      };
    }

    const systemPrompt = `You are an expert simulation intent parser.
Your task is to classify the user's natural language command into a structured intent for a simulation builder.

Available Intent Types:
- add_entity: Create a new entity (e.g., "add a red node at 10,10", "create a particle")
- remove_entity: Delete an entity (e.g., "delete node A", "remove the last circle")
- modify_entity: Change entity properties (e.g., "move node A to 20,20", "make it blue")
- add_step: Add a simulation step
- remove_step: Remove a step
- modify_step: Change step details
- change_speed: Adjust playback speed
- change_visual: Adjust global visual settings
- add_annotation: Add text/labels
- change_domain_config: Change physics/domain parameters
- explain: User asks for explanation
- clarify: User asks for clarification
- undo: Revert
- redo: Redo
- unknown: Cannot determine intent

Output JSON format:
{
  "type": "IntentType",
  "confidence": number (0-1),
  "params": {
    "targetEntity": "string (id or name)",
    "targetStep": "string or number",
    "newValue": "value",
    "property": "property name",
    "entityType": "type of entity",
    "position": { "x": number, "y": number },
    "visual": { "color": "string", "size": number, "opacity": number, "shape": "string" },
    "text": "string",
    "color": "string"
  }
}

Only include relevant params.
Context about current simulation:
${context || "No context provided."}
`;

    try {
      const completion = await this.openai.chat.completions.create({
        model: this.model,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: userInput },
        ],
        response_format: { type: "json_object" },
        temperature: 0.1,
      });

      const content = completion.choices[0].message.content;
      if (!content) throw new Error("No content in response");

      const parsed = JSON.parse(content);

      return {
        type: parsed.type || "unknown",
        confidence: parsed.confidence || 0,
        params: parsed.params || {},
        originalInput: userInput,
        normalizedInput: userInput.toLowerCase(),
      };
    } catch (error) {
      console.error("Error parsing simulation intent:", error);
      return {
        type: "unknown",
        confidence: 0,
        params: {},
        originalInput: userInput,
        normalizedInput: userInput.toLowerCase(),
      };
    }
  }

  async explainSimulation(args: {
    manifest: any;
    query: string;
  }): Promise<string> {
    if (!this.openai) return "AI service unavailable.";

    const systemPrompt = `You are an expert simulation engineer.
Analyze the provided simulation manifest and answer the user's question.
If the user asks to "explain", summarize what the simulation does based on its entities and steps.
Be concise and helpful.`;

    try {
      const completion = await this.openai.chat.completions.create({
        model: this.model,
        messages: [
          { role: "system", content: systemPrompt },
          {
            role: "user",
            content: `Manifest: ${JSON.stringify(args.manifest)}`,
          },
          { role: "user", content: args.query },
        ],
      });
      return (
        completion.choices[0].message.content ||
        "I couldn't generate an explanation."
      );
    } catch (e) {
      console.error("Error explaining simulation:", e);
      return "Error generating explanation.";
    }
  }

  async generateLearningUnitDraft(args: {
    topic: string;
    targetAudience: string;
    learningObjectives?: string[];
  }): Promise<any> {
    if (!this.openai) throw new Error("AI service unavailable");

    const systemPrompt = `You are an expert instructional designer using the Evidence-Based Learning framework.
Create a Learning Unit Draft based on the user's topic.

Structure Requirements:
1. Intent: Problem, Motivation, Target Misconceptions.
2. Claims: What learners will PROVE (Bloom's taxonomy).
3. Evidence: Observable behaviors that validate claims.
4. Tasks: Activities that generate the evidence.

Output JSON format matching the ModuleDraftInput structure (simplified):
{
  "title": "string",
  "description": "string",
  "domain": "MATH" | "SCIENCE" | "TECH",
  "difficulty": "INTRO" | "INTERMEDIATE" | "ADVANCED",
  "learningObjectives": [ { "text": "string", "bloomLevel": "string" } ],
  "contentBlocks": [
    { "type": "text", "content": "Introduction..." },
    { "type": "simulation", "content": "Simulation description..." }
  ],
  "metadata": {
    "claims": [...],
    "evidence": [...]
  }
}`;

    try {
      const completion = await this.openai.chat.completions.create({
        model: this.model,
        messages: [
          { role: "system", content: systemPrompt },
          {
            role: "user",
            content: `Topic: ${args.topic}. Audience: ${args.targetAudience}. Objectives: ${args.learningObjectives?.join(", ")}`,
          },
        ],
        response_format: { type: "json_object" },
      });

      const content = completion.choices[0].message.content;
      return content ? JSON.parse(content) : null;
    } catch (e) {
      console.error("Error generating draft:", e);
      throw e;
    }
  }

  async parseContentQuery(query: string): Promise<{
    domain?: string;
    difficulty?: string;
    tags?: string[];
    textSearch?: string;
  }> {
    if (!this.openai) return { textSearch: query };

    const systemPrompt = `You are a search query parser for an educational content platform.
Extract filters from the user's natural language query.

Available Domains: MATH, SCIENCE, TECH
Available Difficulties: INTRO, INTERMEDIATE, ADVANCED

Output JSON:
{
  "domain": "string" | null,
  "difficulty": "string" | null,
  "tags": ["string"],
  "textSearch": "string" (the core search terms stripped of filter words)
}`;

    try {
      const completion = await this.openai.chat.completions.create({
        model: this.model,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: query },
        ],
        response_format: { type: "json_object" },
        temperature: 0,
      });

      const content = completion.choices[0].message.content;
      return content ? JSON.parse(content) : { textSearch: query };
    } catch (e) {
      console.error("Error parsing content query:", e);
      return { textSearch: query };
    }
  }

  /**
   * Retrieve relevant content from the database for RAG.
   */
  private async retrieveRAGContext(
    tenantId: TenantId,
    moduleId: ModuleId | undefined,
    question: string,
  ): Promise<RAGContext> {
    if (!this.prisma || !moduleId) {
      return this.buildMinimalContext(moduleId);
    }

    try {
      // Fetch module with learning objectives and content blocks
      const module = await this.prisma.module.findFirst({
        where: { id: moduleId, tenantId },
        include: {
          learningObjectives: true,
          contentBlocks: {
            orderBy: { orderIndex: "asc" },
          },
        },
      });

      if (!module) {
        return this.buildMinimalContext(moduleId);
      }

      // Extract text content from content blocks
      const contentWithScores = module.contentBlocks
        .filter((block: { blockType: string }) =>
          this.isTextualBlock(block.blockType),
        )
        .map((block: { id: string; blockType: string; payload: unknown }) => ({
          blockId: block.id,
          blockType: block.blockType,
          textContent: this.extractTextFromPayload(block.payload),
          relevanceScore: this.computeSimpleRelevance(
            this.extractTextFromPayload(block.payload),
            question,
          ),
        }))
        .filter((c: { textContent: string }) => c.textContent.length > 0)
        .sort(
          (a: { relevanceScore: number }, b: { relevanceScore: number }) =>
            b.relevanceScore - a.relevanceScore,
        )
        .slice(0, 5); // Top 5 most relevant blocks

      // Build citations
      const citations: TutorCitation[] = [
        { type: "module", id: moduleId, label: module.title },
      ];

      module.learningObjectives.forEach(
        (obj: { id: number; label: string }) => {
          citations.push({
            type: "objective",
            id: String(obj.id),
            label: obj.label,
          });
        },
      );

      contentWithScores.forEach(
        (content: { blockId: string; blockType: string }) => {
          citations.push({
            type: "content_block",
            id: content.blockId,
            label: `${content.blockType} content`,
          });
        },
      );

      return {
        moduleTitle: module.title,
        moduleDescription: module.description,
        learningObjectives: module.learningObjectives.map(
          (o: { label: string }) => o.label,
        ),
        relevantContent: contentWithScores,
        citations,
      };
    } catch (error) {
      console.error("RAG context retrieval failed:", error);
      return this.buildMinimalContext(moduleId);
    }
  }

  /**
   * Check if a block type contains textual content.
   */
  private isTextualBlock(blockType: string): boolean {
    return ["text", "rich_text", "example", "exercise"].includes(blockType);
  }

  /**
   * Extract text content from block payload.
   */
  private extractTextFromPayload(payload: unknown): string {
    if (!payload) return "";

    if (typeof payload === "string") {
      return payload;
    }

    if (typeof payload === "object") {
      const p = payload as Record<string, unknown>;
      // Common payload structures
      if (p.content && typeof p.content === "string") {
        return p.content;
      }
      if (p.text && typeof p.text === "string") {
        return p.text;
      }
      if (p.html && typeof p.html === "string") {
        // Strip HTML tags for search
        return (p.html as string).replace(/<[^>]*>/g, " ").trim();
      }
      if (p.markdown && typeof p.markdown === "string") {
        return p.markdown;
      }
      // Recursively extract from nested content
      if (p.blocks && Array.isArray(p.blocks)) {
        return (p.blocks as unknown[])
          .map((b) => this.extractTextFromPayload(b))
          .join(" ");
      }
    }

    return "";
  }

  /**
   * Compute simple keyword-based relevance score.
   * In production, use vector embeddings for semantic similarity.
   */
  private computeSimpleRelevance(content: string, question: string): number {
    const questionWords = question.toLowerCase().split(/\s+/);
    const contentLower = content.toLowerCase();

    let matchCount = 0;
    for (const word of questionWords) {
      if (word.length > 2 && contentLower.includes(word)) {
        matchCount++;
      }
    }

    return matchCount / Math.max(questionWords.length, 1);
  }

  private buildMinimalContext(moduleId?: ModuleId): RAGContext {
    return {
      moduleTitle: undefined,
      moduleDescription: undefined,
      learningObjectives: [],
      relevantContent: [],
      citations: moduleId
        ? [{ type: "module", id: moduleId, label: "Current Module" }]
        : [],
    };
  }

  private buildTutorPrompt(question: string, context: RAGContext): string {
    const contextParts: string[] = [];

    if (context.moduleTitle) {
      contextParts.push(`**Module:** ${context.moduleTitle}`);
    }

    if (context.moduleDescription) {
      contextParts.push(`**Description:** ${context.moduleDescription}`);
    }

    if (context.learningObjectives.length > 0) {
      contextParts.push(
        `**Learning Objectives:**\n${context.learningObjectives.map((o) => `- ${o}`).join("\n")}`,
      );
    }

    if (context.relevantContent.length > 0) {
      contextParts.push(
        `**Relevant Content:**\n${context.relevantContent.map((c) => `[${c.blockType}] ${c.textContent.substring(0, 500)}`).join("\n\n")}`,
      );
    }

    const contextSection =
      contextParts.length > 0
        ? contextParts.join("\n\n")
        : "No specific module context available. Provide a general educational response.";

    return `You are TutorPutor, an expert AI tutor helping students learn mathematics, science, and technology.

## Context
${contextSection}

## Student Question
${question}

## Instructions
1. Use the provided context to give an accurate, curriculum-aligned response
2. Apply the Socratic method: ask guiding questions to help the student discover the answer
3. Reference the learning objectives when relevant
4. Keep your response focused and educational (2-3 paragraphs)
5. If the question is off-topic, politely redirect to the module content
6. Use examples from the context when available

## Response`;
  }

  private async callLLM(prompt: string, moduleTitle?: string): Promise<string> {
    // Try Ollama first (if enabled and available)
    if (this.useOllama) {
      try {
        console.log(
          `[AI Proxy] Using Ollama (${this.ollamaModel}) from ${this.ollamaBaseUrl}`,
        );
        const ollamaResponse = await this.callOllama(prompt);
        if (ollamaResponse) {
          console.log("[AI Proxy] Ollama response successful");
          return ollamaResponse;
        }
      } catch (error) {
        console.warn(
          "[AI Proxy] Ollama call failed, falling back to web search:",
          error,
        );
      }
    }

    // Try OpenAI if available
    if (this.openai) {
      try {
        const completion = await this.openai.chat.completions.create({
          model: this.model,
          messages: [
            {
              role: "user",
              content: prompt,
            },
          ],
          temperature: 0.7,
          max_tokens: 1500,
        });

        const response = completion.choices[0]?.message?.content;
        if (response) {
          console.log("[AI Proxy] OpenAI response successful");
          return response;
        }
      } catch (error) {
        console.warn(
          "[AI Proxy] OpenAI call failed, attempting web search fallback:",
          error,
        );
      }
    }

    // Try web search as fallback
    try {
      console.warn(
        "[AI Proxy] AI service not available, attempting web search fallback",
      );
      const question = this.extractQuestionFromPrompt(prompt);
      const searchResponse = await this.webSearchService.search(question);

      if (
        searchResponse.answer &&
        searchResponse.answer !== "No information found"
      ) {
        console.log("[AI Proxy] Web search fallback successful");
        return searchResponse.answer;
      }
    } catch (error) {
      console.error("[AI Proxy] Web search fallback failed:", error);
    }

    // Final fallback to demo response
    return `[AI Tutor - Demo Mode] I understand your question about ${moduleTitle || "this topic"}. In production with AI service configured (OpenAI, Ollama, or web search), I would provide a detailed, context-aware response using the module's learning objectives and content. For now, I recommend reviewing the module materials and trying the interactive exercises.`;
  }

  /**
   * Call Ollama API (OpenAI-compatible interface).
   * Ollama runs locally and is ideal for development.
   */
  private async callOllama(prompt: string): Promise<string> {
    try {
      const response = await fetch(
        `${this.ollamaBaseUrl}/v1/chat/completions`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            model: this.ollamaModel,
            messages: [
              {
                role: "user",
                content: prompt,
              },
            ],
            temperature: 0.7,
            max_tokens: 1500,
            stream: false,
          }),
        },
      );

      if (!response.ok) {
        throw new Error(
          `Ollama error: ${response.status} ${response.statusText}`,
        );
      }

      const data = (await response.json()) as {
        choices?: Array<{ message?: { content?: string } }>;
      };
      const content = data.choices?.[0]?.message?.content;

      if (!content) {
        throw new Error("No content in Ollama response");
      }

      return content;
    } catch (error) {
      console.error("[AI Proxy] Ollama error:", error);
      throw error;
    }
  }

  /**
   * Extract the main question from a complex prompt
   */
  private extractQuestionFromPrompt(prompt: string): string {
    // Try to extract the student question if present
    const lines = prompt.split("\n");

    // Look for "Student Question:" or similar markers
    const questionLine = lines.find(
      (line) =>
        line.includes("Student Question") ||
        line.includes("Question:") ||
        line.includes("Ask me:"),
    );

    if (questionLine) {
      return questionLine.replace(/.*Question[:\s]+/i, "").trim();
    }

    // Fallback: use first non-empty line that looks like a question or substantial text
    const substantialLine = lines.find(
      (line) =>
        line.trim().length > 10 &&
        !line.includes("Role:") &&
        !line.includes("Context:"),
    );

    return substantialLine?.trim() || "educational question";
  }

  private async generateFollowUpQuestions(
    originalQuestion: string,
    answer: string,
    context: RAGContext,
  ): Promise<string[]> {
    // If no OpenAI, use static follow-ups based on context
    if (!this.openai) {
      const questions = [];
      if (context.learningObjectives.length > 0) {
        questions.push(
          `Would you like to explore: "${context.learningObjectives[0]}"?`,
        );
      }
      questions.push("Do you want to practice a similar problem?");
      questions.push("Would you like me to explain any part in more detail?");
      return questions.slice(0, 3);
    }

    // Use LLM to generate contextual follow-ups
    try {
      const completion = await this.openai.chat.completions.create({
        model: this.model,
        messages: [
          {
            role: "system",
            content:
              "Generate 3 short, engaging follow-up questions for a student based on their question and the tutor's answer. Return only the questions, one per line.",
          },
          {
            role: "user",
            content: `Student asked: ${originalQuestion}\n\nTutor answered: ${answer.substring(0, 500)}\n\nGenerate 3 follow-up questions:`,
          },
        ],
        temperature: 0.8,
        max_tokens: 150,
      });

      const response = completion.choices[0]?.message?.content || "";
      return response
        .split("\n")
        .map((q) => q.replace(/^\d+\.\s*/, "").trim())
        .filter((q) => q.length > 0)
        .slice(0, 3);
    } catch {
      return [
        "Would you like to practice a similar problem?",
        "Do you want to review the underlying concept?",
        "Are there other aspects you'd like to explore?",
      ];
    }
  }

  /**
   * Generate questions from module content (for practice/assessment).
   */
  async generateQuestionsFromContent(args: {
    tenantId: TenantId;
    moduleId: ModuleId;
    count: number;
    difficulty: "easy" | "medium" | "hard";
  }): Promise<
    Array<{
      question: string;
      options?: string[];
      correctAnswer: string;
      explanation: string;
    }>
  > {
    const { tenantId, moduleId, count, difficulty } = args;

    if (!this.prisma) {
      throw new Error(
        "AI_NOT_CONFIGURED: Database connection required for question generation",
      );
    }

    // Fetch module content for question generation
    const context = await this.retrieveRAGContext(tenantId, moduleId, "");

    if (!this.openai) {
      throw new Error(
        "AI_NOT_CONFIGURED: OPENAI_API_KEY is required for question generation",
      );
    }

    if (context.relevantContent.length === 0) {
      throw new Error(
        "NO_CONTENT: No content available in module for question generation",
      );
    }

    try {
      const contentSummary = context.relevantContent
        .map((c) => c.textContent.substring(0, 300))
        .join("\n");

      const completion = await this.openai.chat.completions.create({
        model: this.model,
        messages: [
          {
            role: "system",
            content: `Generate ${count} ${difficulty} difficulty multiple-choice questions based on the provided content. Return as JSON array with: question, options (4 choices), correctAnswer, explanation.`,
          },
          {
            role: "user",
            content: `Module: ${context.moduleTitle}\n\nContent:\n${contentSummary}\n\nGenerate questions as JSON array:`,
          },
        ],
        temperature: 0.7,
        max_tokens: 1500,
        response_format: { type: "json_object" },
      });

      const response = JSON.parse(
        completion.choices[0]?.message?.content || '{"questions":[]}',
      );
      return (response.questions || []).slice(0, count);
    } catch (error) {
      console.error("Question generation failed:", error);
      throw new Error(
        `GENERATION_FAILED: ${error instanceof Error ? error.message : "Unknown error"}`,
      );
    }
  }
}

/**
 * Factory function to create an AI Proxy Service instance.
 */
export function createAIProxyService(
  config?: AIProxyServiceConfig,
  prisma?: TutorPrismaClient,
): AIProxyService {
  return new TutorPutorAIProxyService(config, prisma);
}
