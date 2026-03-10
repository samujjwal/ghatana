/**
 * Simulation Tutor Routes
 *
 * HTTP routes for AI tutoring during simulations.
 * Wraps the core tutor pipeline with simulation context.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for simulation-aware AI tutoring
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import OpenAI from "openai";
import { readFileSync } from "fs";
import { join } from "path";
import {
  SimulationContextDeriver,
  createSimulationContextDeriver,
  type SimulationTutorContext,
  type TrackedUserAction,
} from "../simulation-context-deriver";
import type {
  SimulationManifest,
  SimKeyframe,
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type { TenantId, UserId } from "@ghatana/tutorputor-contracts/v1";

// =============================================================================
// Types
// =============================================================================

interface SimulationTutorRequest {
  tenantId: TenantId;
  userId: UserId;
  simulationId: string;
  question: string;
  manifest: SimulationManifest;
  currentKeyframe?: SimKeyframe;
  recentActions?: TrackedUserAction[];
  locale?: string;
}

interface SimulationTutorResponse {
  answer: string;
  followUpQuestions: string[];
  hints: string[];
  relatedConcepts: string[];
  suggestedActions: string[];
  confidence: number;
  /** True if response was generated without AI (context-based fallback) */
  isFallback: boolean;
  /** Reason for fallback, if applicable */
  fallbackReason?: string;
}

interface StreamChunk {
  type: "text" | "hint" | "suggestion" | "done";
  content?: string;
}

// =============================================================================
// Context Cache
// =============================================================================

// Simple in-memory cache for context derivers (in production, use Redis)
const contextCache = new Map<string, SimulationContextDeriver>();

function getOrCreateDeriver(
  simulationId: string,
  manifest: SimulationManifest,
): SimulationContextDeriver {
  let deriver = contextCache.get(simulationId);

  if (!deriver) {
    deriver = createSimulationContextDeriver(manifest);
    contextCache.set(simulationId, deriver);

    // Clean up old entries (simple LRU-ish behavior)
    if (contextCache.size > 1000) {
      const oldest = contextCache.keys().next().value;
      if (oldest) contextCache.delete(oldest);
    }
  }

  return deriver;
}

// =============================================================================
// Prompt Building
// =============================================================================

// Load the template at startup
let promptTemplate: string;
try {
  promptTemplate = readFileSync(
    join(__dirname, "prompts", "simulation-tutor.template.txt"),
    "utf-8",
  );
} catch {
  // Fallback template if file not found
  promptTemplate = `You are an AI tutor helping with a {{domain}} simulation called "{{simulationTitle}}".

Current step: {{currentStepIndex}} of {{totalSteps}}

Student question: {{studentQuestion}}

Provide a helpful, Socratic response that guides the student without giving away the answer directly.`;
}

/**
 * Build the prompt from template and context.
 */
function buildPrompt(
  context: SimulationTutorContext,
  question: string,
): string {
  let prompt = promptTemplate;

  // Simple template replacement (in production, use a proper template engine)
  prompt = prompt
    .replace(
      "{{simulationTitle}}",
      context.simulationSummary.split('"')[1] ?? "Simulation",
    )
    .replace("{{domain}}", context.domainContext.domain)
    .replace(/\{\{domain\}\}/g, context.domainContext.domain)
    .replace(
      "{{currentStepIndex}}",
      String((context.currentStep?.index ?? 0) + 1),
    )
    .replace("{{totalSteps}}", "N")
    .replace(
      "{{stepObjective}}",
      context.currentStep?.objective ?? "Explore the simulation",
    )
    .replace("{{entityCount}}", String(context.entities.length))
    .replace(
      "{{domainContextSummary}}",
      JSON.stringify(context.domainContext, null, 2),
    )
    .replace("{{studentQuestion}}", question);

  // Handle arrays with simple formatting
  const entitiesStr = context.entities
    .slice(0, 5)
    .map((e) => `- **${e.label}** (${e.type})`)
    .join("\n");
  prompt = prompt.replace(
    /\{\{#each entities\}\}[\s\S]*?\{\{\/each\}\}/,
    entitiesStr,
  );

  const paramsStr = context.parameters
    .slice(0, 5)
    .map(
      (p) => `- **${p.name}:** ${p.currentValue}${p.unit ? ` ${p.unit}` : ""}`,
    )
    .join("\n");
  prompt = prompt.replace(
    /\{\{#each parameters\}\}[\s\S]*?\{\{\/each\}\}/,
    paramsStr,
  );

  const metricsStr = context.metrics
    .slice(0, 5)
    .map((m) => `- **${m.name}:** ${m.value} - ${m.interpretation}`)
    .join("\n");
  prompt = prompt.replace(
    /\{\{#each metrics\}\}[\s\S]*?\{\{\/each\}\}/,
    metricsStr,
  );

  const actionsStr = context.userActions
    .slice(0, 5)
    .map((a) => `- ${a.description}`)
    .join("\n");
  prompt = prompt.replace(
    /\{\{#each userActions\}\}[\s\S]*?\{\{\/each\}\}/,
    actionsStr || "- No recent actions",
  );

  const hintsStr = context.hints.map((h) => `- ${h}`).join("\n");
  prompt = prompt.replace(
    /\{\{#each hints\}\}[\s\S]*?\{\{\/each\}\}/,
    hintsStr || "- Explore the simulation",
  );

  return prompt;
}

/**
 * Generate follow-up questions based on context and answer.
 */
function generateFollowUpQuestions(
  context: SimulationTutorContext,
  answer: string,
): string[] {
  const questions: string[] = [];

  // Add domain-specific follow-ups
  switch (context.domainContext.domain) {
    case "PHYSICS":
      questions.push("What would happen if we changed the mass?");
      questions.push("How does this relate to energy conservation?");
      break;
    case "CHEMISTRY":
      questions.push("What would happen with a different solvent?");
      questions.push("Can you identify the rate-determining step?");
      break;
    case "MEDICINE":
      questions.push("How would changing the dose affect the concentration?");
      questions.push("What happens if we miss a dose?");
      break;
    case "ECONOMICS":
      questions.push("What would happen if this flow rate increased?");
      questions.push("Is the system approaching equilibrium?");
      break;
    case "CS_DISCRETE":
      questions.push("What's the time complexity of this operation?");
      questions.push("Can we optimize this further?");
      break;
    default:
      questions.push("What do you observe in the current state?");
      questions.push("What would you like to try next?");
  }

  return questions.slice(0, 3);
}

/**
 * Generate suggested actions based on context.
 */
function generateSuggestedActions(context: SimulationTutorContext): string[] {
  const suggestions: string[] = [];

  if (context.currentStep) {
    suggestions.push(`Continue to step ${context.currentStep.index + 2}`);
  }

  // Add domain-specific suggestions
  switch (context.domainContext.domain) {
    case "PHYSICS":
      suggestions.push("Try applying a force to an object");
      suggestions.push("Observe the velocity vectors");
      break;
    case "CHEMISTRY":
      suggestions.push("Examine the bond formation");
      suggestions.push("Check the energy profile");
      break;
    case "MEDICINE":
      suggestions.push("Administer another dose");
      suggestions.push("Observe the elimination curve");
      break;
    default:
      suggestions.push("Explore different parameters");
  }

  return suggestions.slice(0, 3);
}

// =============================================================================
// Route Registration
// =============================================================================

/**
 * Register simulation tutor routes on a Fastify instance.
 *
 * @param app - Fastify instance
 * @param openaiApiKey - OpenAI API key (optional, for real LLM calls)
 */
export function registerSimulationTutorRoutes(
  app: FastifyInstance,
  openaiApiKey?: string,
): void {
  const openai = openaiApiKey ? new OpenAI({ apiKey: openaiApiKey }) : null;

  // POST /tutor/simulation - Ask the tutor a question about a simulation
  app.post(
    "/tutor/simulation",
    {
      schema: {
        body: {
          type: "object",
          required: ["simulationId", "question", "manifest"],
          properties: {
            simulationId: { type: "string" },
            question: { type: "string", minLength: 1 },
            manifest: { type: "object" },
            currentKeyframe: { type: "object" },
            recentActions: {
              type: "array",
              items: { type: "object" },
            },
            locale: { type: "string" },
          },
        },
      },
    },
    async (
      request: FastifyRequest<{
        Body: Omit<SimulationTutorRequest, "tenantId" | "userId">;
      }>,
      reply: FastifyReply,
    ) => {
      const tenantId = request.headers["x-tenant-id"] as TenantId;
      const userId = request.headers["x-user-id"] as UserId;
      const {
        simulationId,
        question,
        manifest,
        currentKeyframe,
        recentActions,
      } = request.body;

      // Get or create context deriver
      const deriver = getOrCreateDeriver(
        simulationId,
        manifest as SimulationManifest,
      );

      // Update state if keyframe provided
      if (currentKeyframe) {
        deriver.updateState(currentKeyframe as SimKeyframe);
      }

      // Record recent actions
      if (recentActions) {
        for (const action of recentActions) {
          deriver.recordUserAction(action as TrackedUserAction);
        }
      }

      // Derive context
      const context = deriver.deriveContext();

      // Build prompt
      const prompt = buildPrompt(context, question);

      // Call LLM or use fallback
      let answer: string;
      let isFallback = false;
      let fallbackReason: string | undefined;

      if (openai) {
        try {
          const completion = await openai.chat.completions.create({
            model: "gpt-4o-mini",
            messages: [
              {
                role: "system",
                content: "You are a helpful simulation tutor.",
              },
              { role: "user", content: prompt },
            ],
            max_tokens: 500,
            temperature: 0.7,
          });
          answer =
            completion.choices[0]?.message?.content ?? "I'm here to help!";
        } catch (error) {
          console.error(
            "[SimulationTutor] OpenAI API error, using context-based fallback:",
            error,
          );
          answer = generateStubAnswer(context, question);
          isFallback = true;
          fallbackReason = "AI service temporarily unavailable";
        }
      } else {
        console.info(
          "[SimulationTutor] No OpenAI configured, using context-based responses",
        );
        answer = generateStubAnswer(context, question);
        isFallback = true;
        fallbackReason = "AI service not configured";
      }

      const response: SimulationTutorResponse = {
        answer,
        followUpQuestions: generateFollowUpQuestions(context, answer),
        hints: context.hints,
        relatedConcepts: extractRelatedConcepts(context),
        suggestedActions: generateSuggestedActions(context),
        confidence: isFallback ? 0.6 : 0.85,
        isFallback,
        fallbackReason,
      };

      return reply.send(response);
    },
  );

  // POST /tutor/simulation/stream - Stream tutor response
  app.post(
    "/tutor/simulation/stream",
    {
      schema: {
        body: {
          type: "object",
          required: ["simulationId", "question", "manifest"],
          properties: {
            simulationId: { type: "string" },
            question: { type: "string" },
            manifest: { type: "object" },
            currentKeyframe: { type: "object" },
            recentActions: { type: "array" },
          },
        },
      },
    },
    async (
      request: FastifyRequest<{
        Body: Omit<SimulationTutorRequest, "tenantId" | "userId">;
      }>,
      reply: FastifyReply,
    ) => {
      const {
        simulationId,
        question,
        manifest,
        currentKeyframe,
        recentActions,
      } = request.body;

      // Get or create context deriver
      const deriver = getOrCreateDeriver(
        simulationId,
        manifest as SimulationManifest,
      );

      if (currentKeyframe) {
        deriver.updateState(currentKeyframe as SimKeyframe);
      }

      if (recentActions) {
        for (const action of recentActions) {
          deriver.recordUserAction(action as TrackedUserAction);
        }
      }

      const context = deriver.deriveContext();
      const prompt = buildPrompt(context, question);

      // Set up SSE
      reply.raw.setHeader("Content-Type", "text/event-stream");
      reply.raw.setHeader("Cache-Control", "no-cache");
      reply.raw.setHeader("Connection", "keep-alive");

      if (openai) {
        try {
          const stream = await openai.chat.completions.create({
            model: "gpt-4o-mini",
            messages: [
              {
                role: "system",
                content: "You are a helpful simulation tutor.",
              },
              { role: "user", content: prompt },
            ],
            max_tokens: 500,
            temperature: 0.7,
            stream: true,
          });

          for await (const chunk of stream) {
            const content = chunk.choices[0]?.delta?.content;
            if (content) {
              const data: StreamChunk = { type: "text", content };
              reply.raw.write(`data: ${JSON.stringify(data)}\n\n`);
            }
          }
        } catch (error) {
          console.error("Stream error:", error);
          const data: StreamChunk = {
            type: "text",
            content: generateStubAnswer(context, question),
          };
          reply.raw.write(`data: ${JSON.stringify(data)}\n\n`);
        }
      } else {
        // Simulate streaming for stub
        const answer = generateStubAnswer(context, question);
        const words = answer.split(" ");
        for (const word of words) {
          const data: StreamChunk = { type: "text", content: word + " " };
          reply.raw.write(`data: ${JSON.stringify(data)}\n\n`);
          await new Promise((r) => setTimeout(r, 50));
        }
      }

      // Send hints
      for (const hint of context.hints.slice(0, 2)) {
        const data: StreamChunk = { type: "hint", content: hint };
        reply.raw.write(`data: ${JSON.stringify(data)}\n\n`);
      }

      // Send suggestions
      for (const suggestion of generateSuggestedActions(context)) {
        const data: StreamChunk = { type: "suggestion", content: suggestion };
        reply.raw.write(`data: ${JSON.stringify(data)}\n\n`);
      }

      // Done
      const doneData: StreamChunk = { type: "done" };
      reply.raw.write(`data: ${JSON.stringify(doneData)}\n\n`);
      reply.raw.end();
    },
  );

  // GET /tutor/simulation/:simulationId/context - Get current context
  app.get(
    "/tutor/simulation/:simulationId/context",
    {
      schema: {
        params: {
          type: "object",
          required: ["simulationId"],
          properties: {
            simulationId: { type: "string" },
          },
        },
      },
    },
    async (
      request: FastifyRequest<{
        Params: { simulationId: string };
      }>,
      reply: FastifyReply,
    ) => {
      const { simulationId } = request.params;
      const deriver = contextCache.get(simulationId);

      if (!deriver) {
        return reply
          .status(404)
          .send({ error: "No context found for this simulation" });
      }

      const context = deriver.deriveContext();
      return reply.send(context);
    },
  );
}

// =============================================================================
// Helpers
// =============================================================================

/**
 * Generate a stub answer when no LLM is available.
 */
function generateStubAnswer(
  context: SimulationTutorContext,
  question: string,
): string {
  const questionLower = question.toLowerCase();
  const domain = context.domainContext.domain;

  // Check for common question patterns
  if (questionLower.includes("why")) {
    return (
      `That's a great question! In ${domain.toLowerCase()} simulations, understanding the "why" is key. ` +
      `Looking at the current state, can you identify what parameters might be influencing this behavior? ` +
      `${context.hints[0] ?? "Try observing the changes step by step."}`
    );
  }

  if (questionLower.includes("how")) {
    return (
      `To understand how this works, let's break it down. ` +
      `You're at step ${(context.currentStep?.index ?? 0) + 1}. ` +
      `${context.currentStep?.objective ?? "Observe the entities and their interactions."}`
    );
  }

  if (questionLower.includes("what")) {
    return (
      `Looking at the simulation, I see ${context.entities.length} active entities. ` +
      `${context.metrics[0] ? `The ${context.metrics[0].name} is currently ${context.metrics[0].value}. ` : ""}` +
      `What patterns do you notice?`
    );
  }

  // Default response
  return (
    `I see you're exploring a ${domain.toLowerCase()} simulation. ` +
    `${context.simulationSummary} ` +
    `What specific aspect would you like to understand better?`
  );
}

/**
 * Extract related concepts from context.
 */
function extractRelatedConcepts(context: SimulationTutorContext): string[] {
  const concepts: string[] = [];

  switch (context.domainContext.domain) {
    case "PHYSICS":
      concepts.push("Newton's Laws", "Conservation of Energy", "Momentum");
      break;
    case "CHEMISTRY":
      concepts.push(
        "Reaction Mechanisms",
        "Molecular Orbitals",
        "Thermodynamics",
      );
      break;
    case "BIOLOGY":
      concepts.push("Cell Signaling", "Metabolism", "Gene Expression");
      break;
    case "MEDICINE":
      concepts.push("Pharmacokinetics", "Therapeutic Window", "Half-Life");
      break;
    case "ECONOMICS":
      concepts.push("System Dynamics", "Feedback Loops", "Equilibrium");
      break;
    case "CS_DISCRETE":
      concepts.push(
        "Time Complexity",
        "Space Complexity",
        "Algorithm Analysis",
      );
      break;
    default:
      concepts.push("Simulation", "Modeling", "Analysis");
  }

  return concepts.slice(0, 3);
}
