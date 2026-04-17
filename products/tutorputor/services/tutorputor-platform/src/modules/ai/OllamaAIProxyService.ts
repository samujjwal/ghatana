/**
 * Ollama AI Proxy Service Implementation
 *
 * Production-grade AI service connecting directly to Ollama for:
 * - Tutoring responses
 * - Intent parsing
 * - Simulation explanation
 * - Content generation
 * - Query parsing
 *
 * @doc.type class
 * @doc.purpose AI proxy service implementation using Ollama
 * @doc.layer platform
 * @doc.pattern Service
 */

import type { AIProxyService } from "@tutorputor/contracts/v1/services";
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'OllamaAIProxyService' });

type Citation = {
  title: string;
  url: string;
  source: string;
};

import type {
  TenantId,
  UserId,
  ModuleId,
  TutorResponsePayload,
} from "@tutorputor/contracts/v1/types";
import type {
  ParsedIntent,
  IntentParams,
} from "@tutorputor/contracts/v1/simulation/types";

// Configuration
const DEFAULT_MODEL = process.env.OLLAMA_MODEL || "llama3.2";
const DEFAULT_TIMEOUT_MS = 30000;
const MAX_RETRIES = 2;

// Domain keywords for intent classification
const DOMAIN_KEYWORDS: Record<string, string[]> = {
  physics: ["physics", "force", "motion", "gravity", "velocity", "acceleration", "energy", "mass", "newton", "einstein"],
  chemistry: ["chemistry", "molecule", "atom", "reaction", "element", "compound", "bond", "acid", "base", "ph"],
  biology: ["biology", "cell", "organism", "dna", "protein", "gene", "ecosystem", "evolution", "photosynthesis"],
  mathematics: ["math", "equation", "calculus", "algebra", "geometry", "derivative", "integral", "function"],
  engineering: ["engineering", "circuit", "robot", "mechanism", "structure", "design", "system"],
};

interface OllamaGenerateRequest {
  model: string;
  prompt: string;
  system?: string;
  stream?: boolean;
  options?: {
    temperature?: number;
    top_p?: number;
    max_tokens?: number;
  };
}

interface OllamaGenerateResponse {
  response: string;
  done: boolean;
  context?: number[];
  total_duration?: number;
  load_duration?: number;
  prompt_eval_count?: number;
  eval_count?: number;
}

export class OllamaAIProxyService implements AIProxyService {
  private baseUrl: string;
  private model: string;
  private timeoutMs: number;

  constructor(baseUrl = "http://localhost:11434", model = DEFAULT_MODEL) {
    this.baseUrl = baseUrl;
    this.model = model;
    this.timeoutMs = DEFAULT_TIMEOUT_MS;
  }

  /**
   * Make a request to Ollama with retry logic
   */
  private async callOllama(
    request: OllamaGenerateRequest,
    retries = MAX_RETRIES,
  ): Promise<OllamaGenerateResponse> {
    const url = `${this.baseUrl}/api/generate`;
    
    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.timeoutMs);

        const response = await fetch(url, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            ...request,
            stream: false,
          }),
          signal: controller.signal,
        });

        clearTimeout(timeoutId);

        if (!response.ok) {
          throw new Error(`Ollama returned ${response.status}: ${response.statusText}`);
        }

        const data = await response.json() as OllamaGenerateResponse;
        
        logger.debug({
          message: "Ollama request successful",
          model: this.model,
          promptTokens: data.prompt_eval_count,
          responseTokens: data.eval_count,
          duration: data.total_duration,
        });

        return data;
      } catch (error) {
        const isRetryable = error instanceof Error && 
          (error.name === "AbortError" || error.message.includes("fetch failed"));
        
        if (attempt < retries && isRetryable) {
          logger.warn({
            message: `Ollama request failed, retrying (${attempt + 1}/${retries})`,
            error: error instanceof Error ? error.message : String(error),
          });
          await new Promise(resolve => setTimeout(resolve, 1000 * (attempt + 1)));
          continue;
        }
        
        throw error;
      }
    }
    
    throw new Error("Max retries exceeded");
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
      const systemPrompt = `You are TutorPutor, an AI tutor specializing in STEAM education (Science, Technology, Engineering, Arts, Mathematics).

Guidelines:
- Provide clear, accurate educational explanations
- Use age-appropriate language based on the question context
- Encourage critical thinking with follow-up questions
- Cite educational sources when possible
- Keep responses concise but thorough (2-4 paragraphs)
- If unsure, acknowledge limitations honestly

Format your response as:
1. Direct answer to the question
2. Brief explanation with examples if helpful
3. 2-3 follow-up questions to deepen understanding`;

      const userPrompt = `Student question: ${args.question}

${args.locale && args.locale !== "en" ? `Respond in ${args.locale} language.` : ""}

Provide a helpful educational response.`;

      const result = await this.callOllama({
        model: this.model,
        system: systemPrompt,
        prompt: userPrompt,
        options: {
          temperature: 0.7,
          top_p: 0.9,
          max_tokens: 2048,
        },
      });

      // Parse response to extract follow-up questions
      const answer = result.response.trim();
      const followUpQuestions = this.extractFollowUpQuestions(answer);
      const citations = this.extractCitations(answer);

      return {
        answer: this.cleanAnswer(answer),
        citations,
        followUpQuestions,
        safety: { blocked: false },
      };
    } catch (error) {
      logger.error({
        message: "Failed to handle tutor query",
        error: error instanceof Error ? error.message : String(error),
        tenantId: args.tenantId,
        userId: args.userId,
      });
      
      return {
        answer: "I'm sorry, I'm having trouble connecting to the AI service. Please try again in a moment.",
        citations: [],
        followUpQuestions: [],
        safety: { blocked: false },
      };
    }
  }

  /**
   * Parse simulation intent from user input
   */
  async parseSimulationIntent(args: {
    userInput: string;
    context?: string;
  }): Promise<ParsedIntent> {
    try {
      const normalized = args.userInput.toLowerCase().trim();
      
      // First, try rule-based classification for speed
      const ruleBased = this.classifyIntentRuleBased(normalized, args.context);
      if (ruleBased.confidence > 0.8) {
        return ruleBased;
      }

      // Fall back to AI classification for complex cases
      const systemPrompt = `You are an intent classifier for a simulation platform. 
Classify the user's input into one of these intent types:
- CREATE_SIMULATION: User wants to create a new simulation
- MODIFY_SIMULATION: User wants to change an existing simulation
- RUN_SIMULATION: User wants to run/execute a simulation
- ANALYZE_SIMULATION: User wants to analyze results
- EXPLAIN_CONCEPT: User wants to understand a concept
- UNKNOWN: Cannot determine intent

Respond ONLY with a JSON object in this format:
{
  "type": "CREATE_SIMULATION",
  "confidence": 0.95,
  "params": {
    "domain": "physics",
    "topic": "pendulum"
  }
}`;

      const userPrompt = `User input: "${args.userInput}"
${args.context ? `Context: ${args.context}` : ""}

Classify this intent.`;

      const result = await this.callOllama({
        model: this.model,
        system: systemPrompt,
        prompt: userPrompt,
        options: {
          temperature: 0.1,
          max_tokens: 512,
        },
      });

      const parsed = this.parseIntentResponse(result.response);
      return parsed;
    } catch (error) {
      logger.error({
        message: "Failed to parse simulation intent",
        error: error instanceof Error ? error.message : String(error),
        userInput: args.userInput,
      });
      
      return {
        type: "unknown",
        confidence: 0,
        params: {} as IntentParams,
        originalInput: args.userInput,
        normalizedInput: args.userInput.toLowerCase().trim(),
      };
    }
  }

  /**
   * Rule-based intent classification for common patterns
   */
  private classifyIntentRuleBased(input: string, context?: string): ParsedIntent {
    const createPatterns = ["create", "make", "build", "new simulation", "generate", "design"];
    const modifyPatterns = ["change", "modify", "update", "edit", "adjust", "tweak"];
    const runPatterns = ["run", "start", "execute", "play", "launch", "begin"];
    const analyzePatterns = ["analyze", "results", "data", "graph", "plot", "statistics"];
    const explainPatterns = ["explain", "what is", "how does", "why", "concept", "meaning"];

    const scores: Record<string, number> = {
      CREATE_SIMULATION: 0,
      MODIFY_SIMULATION: 0,
      RUN_SIMULATION: 0,
      ANALYZE_SIMULATION: 0,
      EXPLAIN_CONCEPT: 0,
    };

    createPatterns.forEach(p => { if (input.includes(p)) scores.CREATE_SIMULATION += 0.3; });
    modifyPatterns.forEach(p => { if (input.includes(p)) scores.MODIFY_SIMULATION += 0.3; });
    runPatterns.forEach(p => { if (input.includes(p)) scores.RUN_SIMULATION += 0.3; });
    analyzePatterns.forEach(p => { if (input.includes(p)) scores.ANALYZE_SIMULATION += 0.3; });
    explainPatterns.forEach(p => { if (input.includes(p)) scores.EXPLAIN_CONCEPT += 0.3; });

    // Extract domain
    const domain = this.extractDomain(input);
    if (domain) scores.CREATE_SIMULATION += 0.2;

    const maxType = Object.entries(scores).reduce((a, b) => a[1] > b[1] ? a : b);
    
    if (maxType[1] > 0) {
      return {
        type: maxType[0] as ParsedIntent["type"],
        confidence: Math.min(maxType[1], 1),
        params: {
          domain,
          topic: this.extractTopic(input),
        } as IntentParams,
        originalInput: args.userInput,
        normalizedInput: input,
      };
    }

    return {
      type: "unknown",
      confidence: 0,
      params: {} as IntentParams,
      originalInput: args.userInput,
      normalizedInput: input,
    };
  }

  /**
   * Explain simulation behavior based on manifest
   */
  async explainSimulation(args: {
    manifest: unknown;
    query: string;
  }): Promise<string> {
    try {
      const manifest = args.manifest as Record<string, unknown>;
      const manifestJson = JSON.stringify(manifest, null, 2);

      const systemPrompt = `You are a simulation explainer for an educational platform.
Explain how the simulation works in clear, educational terms.
Focus on:
- The scientific/mathematical principles being demonstrated
- How the simulation models real-world behavior
- Key parameters and their effects
- Educational value and learning outcomes

Keep explanations accessible but scientifically accurate.`;

      const userPrompt = `Simulation manifest:
${manifestJson}

User question: ${args.query}

Provide a clear explanation of how this simulation works.`;

      const result = await this.callOllama({
        model: this.model,
        system: systemPrompt,
        prompt: userPrompt,
        options: {
          temperature: 0.5,
          max_tokens: 2048,
        },
      });

      return result.response.trim();
    } catch (error) {
      logger.error({
        message: "Failed to explain simulation",
        error: error instanceof Error ? error.message : String(error),
        query: args.query,
      });
      
      return "I apologize, but I'm unable to explain this simulation at the moment. Please try again later.";
    }
  }

  /**
   * Generate learning unit draft
   */
  async generateLearningUnitDraft(args: {
    topic: string;
    targetAudience: string;
    learningObjectives?: string[];
  }): Promise<{ title: string; description: string; sections: unknown[] }> {
    try {
      const objectivesText = args.learningObjectives?.join("\n") || "Understand core concepts and apply them";

      const systemPrompt = `You are a curriculum designer creating learning units.
Generate a well-structured learning unit with clear sections.

Respond ONLY with a JSON object in this format:
{
  "title": "Clear, engaging title",
  "description": "Brief overview of the unit",
  "sections": [
    {
      "type": "introduction|concept|example|exercise|summary",
      "title": "Section title",
      "content": "Section content description",
      "estimatedMinutes": 15
    }
  ]
}`;

      const userPrompt = `Create a learning unit for:
Topic: ${args.topic}
Target Audience: ${args.targetAudience}
Learning Objectives:
${objectivesText}

Generate a complete learning unit structure.`;

      const result = await this.callOllama({
        model: this.model,
        system: systemPrompt,
        prompt: userPrompt,
        options: {
          temperature: 0.7,
          max_tokens: 4096,
        },
      });

      const parsed = this.parseLearningUnitResponse(result.response);
      return parsed;
    } catch (error) {
      logger.error({
        message: "Failed to generate learning unit draft",
        error: error instanceof Error ? error.message : String(error),
        topic: args.topic,
      });
      
      return {
        title: args.topic,
        description: `Learning unit about ${args.topic} for ${args.targetAudience}`,
        sections: [
          { type: "introduction", title: "Introduction", content: "Overview of the topic", estimatedMinutes: 10 },
          { type: "concept", title: "Core Concepts", content: "Key ideas and principles", estimatedMinutes: 20 },
          { type: "example", title: "Examples", content: "Illustrative examples", estimatedMinutes: 15 },
          { type: "exercise", title: "Practice", content: "Hands-on exercises", estimatedMinutes: 20 },
          { type: "summary", title: "Summary", content: "Review and key takeaways", estimatedMinutes: 5 },
        ],
      };
    }
  }

  /**
   * Parse content query for search
   */
  async parseContentQuery(query: string): Promise<{
    domain?: string;
    difficulty?: string;
    tags?: string[];
    textSearch?: string;
  }> {
    try {
      // Extract domain using keyword matching
      const domain = this.extractDomain(query);
      
      // Extract difficulty
      const difficulty = this.extractDifficulty(query);
      
      // Extract tags (subject-specific terms)
      const tags = this.extractTags(query);
      
      // Clean text search (remove domain/difficulty keywords)
      let textSearch = query.toLowerCase();
      if (domain) {
        textSearch = textSearch.replace(new RegExp(domain, "gi"), "").trim();
      }
      if (difficulty) {
        const diffKeywords = ["beginner", "easy", "intermediate", "advanced", "hard", "expert"];
        diffKeywords.forEach(k => {
          textSearch = textSearch.replace(new RegExp(`\\b${k}\\b`, "gi"), "").trim();
        });
      }
      textSearch = textSearch.replace(/\s+/g, " ").trim();

      return {
        domain,
        difficulty,
        tags,
        textSearch: textSearch || query,
      };
    } catch (error) {
      logger.error({
        message: "Failed to parse content query",
        error: error instanceof Error ? error.message : String(error),
        query,
      });
      
      return { textSearch: query };
    }
  }

  /**
   * Generate assessment questions from content
   */
  async generateQuestionsFromContent(args: {
    tenantId: string;
    moduleId: string;
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
    try {
      const systemPrompt = `You are an educational assessment generator. Generate ${args.count} ${args.difficulty} multiple-choice questions based on the provided content.

Each question must:
- Test understanding of key concepts
- Have exactly 4 options (A, B, C, D)
- Include the correct answer
- Provide a brief explanation

Respond ONLY with valid JSON in this format:
[
  {
    "question": "Question text here",
    "options": ["Option A", "Option B", "Option C", "Option D"],
    "correctAnswer": "Option A",
    "explanation": "Brief explanation of why this is correct"
  }
]`;

      const userPrompt = `Generate ${args.count} ${args.difficulty} questions for module ${args.moduleId} in tenant ${args.tenantId}.`;

      const result = await this.callOllama({
        model: this.model,
        system: systemPrompt,
        prompt: userPrompt,
        options: {
          temperature: 0.7,
          max_tokens: 2048,
        },
      });

      // Parse the JSON response
      const questions = JSON.parse(result.response);
      
      logger.info({
        message: "Generated questions from content",
        tenantId: args.tenantId,
        moduleId: args.moduleId,
        count: questions.length,
        difficulty: args.difficulty,
      });

      return questions;
    } catch (error) {
      logger.error({
        message: "Failed to generate questions from content",
        error: error instanceof Error ? error.message : String(error),
        tenantId: args.tenantId,
        moduleId: args.moduleId,
      });

      // Return empty array on error instead of throwing
      return [];
    }
  }

  // Helper methods

  private extractFollowUpQuestions(answer: string): string[] {
    const lines = answer.split("\n");
    const questions: string[] = [];
    let inQuestions = false;

    for (const line of lines) {
      const trimmed = line.trim();
      
      if (trimmed.match(/^(follow.?up|questions|try these|consider):?/i)) {
        inQuestions = true;
        continue;
      }
      
      if (inQuestions) {
        const match = trimmed.match(/^\d+[.\)]\s*(.+)$/);
        if (match) {
          questions.push(match[1]);
        } else if (trimmed.endsWith("?")) {
          questions.push(trimmed);
        }
      }
    }

    return questions.slice(0, 3);
  }

  private extractCitations(answer: string): Citation[] {
    const citations: Citation[] = [];
    const citationRegex = /\[([^\]]+)\]\(([^)]+)\)/g;
    let match;

    while ((match = citationRegex.exec(answer)) !== null) {
      citations.push({
        title: match[1],
        url: match[2],
        source: "reference",
      });
    }

    return citations;
  }

  private cleanAnswer(answer: string): string {
    // Remove the follow-up questions section from the main answer
    const lines = answer.split("\n");
    const cleaned: string[] = [];
    let inQuestions = false;

    for (const line of lines) {
      const trimmed = line.trim();
      
      if (trimmed.match(/^(follow.?up|questions|try these|consider):?/i)) {
        inQuestions = true;
        break;
      }
      
      if (!inQuestions) {
        cleaned.push(line);
      }
    }

    return cleaned.join("\n").trim();
  }

  private extractDomain(input: string): string | undefined {
    const domains = Object.entries(DOMAIN_KEYWORDS);
    for (const [domain, keywords] of domains) {
      for (const keyword of keywords) {
        if (input.toLowerCase().includes(keyword)) {
          return domain;
        }
      }
    }
    return undefined;
  }

  private extractTopic(input: string): string {
    // Remove common stop words and extract likely topic
    const stopWords = ["the", "a", "an", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "will", "would", "could", "should"];
    const words = input.toLowerCase()
      .replace(/[^\w\s]/g, "")
      .split(/\s+/)
      .filter(w => w.length > 2 && !stopWords.includes(w));
    
    return words.slice(0, 3).join(" ") || "general";
  }

  private extractDifficulty(query: string): string | undefined {
    const difficulties: Record<string, string[]> = {
      beginner: ["beginner", "easy", "simple", "basic", "introduction", "intro"],
      intermediate: ["intermediate", "medium", "moderate"],
      advanced: ["advanced", "hard", "difficult", "expert", "complex"],
    };

    const lower = query.toLowerCase();
    for (const [level, keywords] of Object.entries(difficulties)) {
      for (const keyword of keywords) {
        if (lower.includes(keyword)) return level;
      }
    }
    return undefined;
  }

  private extractTags(query: string): string[] {
    const tags: string[] = [];
    const allKeywords = Object.values(DOMAIN_KEYWORDS).flat();
    
    const words = query.toLowerCase().split(/\s+/);
    for (const word of words) {
      const clean = word.replace(/[^\w]/g, "");
      if (allKeywords.includes(clean) && !tags.includes(clean)) {
        tags.push(clean);
      }
    }

    return tags.slice(0, 5);
  }

  private parseIntentResponse(response: string): ParsedIntent {
    try {
      // Extract JSON from response
      const jsonMatch = response.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        const parsed = JSON.parse(jsonMatch[0]);
        return {
          type: parsed.type || "unknown",
          confidence: parsed.confidence || 0.5,
          params: parsed.params || {},
          originalInput: parsed.originalInput || "",
          normalizedInput: parsed.normalizedInput || "",
        };
      }
    } catch {
      // Fall through to default
    }

    return {
      type: "unknown",
      confidence: 0,
      params: {} as IntentParams,
      originalInput: "",
      normalizedInput: "",
    };
  }

  private parseLearningUnitResponse(response: string): { title: string; description: string; sections: unknown[] } {
    try {
      const jsonMatch = response.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        const parsed = JSON.parse(jsonMatch[0]);
        return {
          title: parsed.title || "Untitled Unit",
          description: parsed.description || "",
          sections: Array.isArray(parsed.sections) ? parsed.sections : [],
        };
      }
    } catch {
      // Fall through to default
    }

    return {
      title: "Untitled Unit",
      description: "",
      sections: [],
    };
  }
}
