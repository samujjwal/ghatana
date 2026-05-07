export type AllowedHelpMode = "hint" | "explain" | "socratic";

export interface TutorAttemptContext {
  attemptId: string;
  taskId?: string;
  correct?: boolean;
  confidence?: "low" | "medium" | "high";
  misconceptionId?: string;
}

export interface TutorGroundingContext {
  moduleId?: string;
  moduleTitle?: string;
  moduleDescription?: string;
  claimIds?: string[];
  currentSimulationState?: Record<string, unknown>;
  recentAttempts?: TutorAttemptContext[];
  misconceptions?: string[];
  allowedHelpMode?: AllowedHelpMode;
  learningObjectives?: string[];
  relevantContent?: Array<{
    blockId: string;
    blockType: string;
    textContent: string;
    relevanceScore?: number;
  }>;
}

export interface TutorGroundingValidation {
  publishable: boolean;
  errors: string[];
}

export function validateTutorGroundingContext(
  context: TutorGroundingContext,
): TutorGroundingValidation {
  const errors: string[] = [];

  if (!context.moduleId) {
    errors.push("moduleId is required");
  }
  if (!context.claimIds || context.claimIds.length === 0) {
    errors.push("claimIds are required");
  }
  if (!context.currentSimulationState) {
    errors.push("currentSimulationState is required");
  }
  if (!context.recentAttempts || context.recentAttempts.length === 0) {
    errors.push("recentAttempts are required");
  }
  if (!context.misconceptions) {
    errors.push("misconceptions are required");
  }
  if (!context.allowedHelpMode) {
    errors.push("allowedHelpMode is required");
  }

  return {
    publishable: errors.length === 0,
    errors,
  };
}

export function classifyTutorQuestion(question: string): {
  answerSeeking: boolean;
  offTopic: boolean;
} {
  const normalized = question.toLowerCase();
  const answerSeeking =
    /\b(give|tell|show)\s+me\s+the\s+answer\b/.test(normalized) ||
    /\bjust\s+answer\b/.test(normalized) ||
    /\bsolve\s+it\s+for\s+me\b/.test(normalized) ||
    /\bwhat\s+is\s+the\s+answer\b/.test(normalized);
  const offTopic =
    /\b(weather|celebrity|stock price|sports score|movie recommendation)\b/.test(
      normalized,
    );

  return { answerSeeking, offTopic };
}

export function buildSocraticTutorPrompt(
  question: string,
  context: TutorGroundingContext,
): string {
  const validation = validateTutorGroundingContext(context);
  if (!validation.publishable) {
    throw new Error(
      `Tutor grounding context incomplete: ${validation.errors.join(", ")}`,
    );
  }

  const classification = classifyTutorQuestion(question);
  const relevantContent = context.relevantContent ?? [];
  const learningObjectives = context.learningObjectives ?? [];
  const attempts = context.recentAttempts ?? [];
  const misconceptions = context.misconceptions ?? [];

  return `You are TutorPutor's AI tutor. Teach with a Socratic, context-grounded style.

Grounding contract:
- Module ID: ${context.moduleId}
- Module title: ${context.moduleTitle ?? "unknown"}
- Claim IDs: ${(context.claimIds ?? []).join(", ")}
- Allowed help mode: ${context.allowedHelpMode}
- Current simulation state: ${JSON.stringify(context.currentSimulationState)}
- Recent attempts: ${JSON.stringify(attempts)}
- Known misconceptions: ${misconceptions.length > 0 ? misconceptions.join(", ") : "none observed"}
- Learning objectives: ${learningObjectives.length > 0 ? learningObjectives.join("; ") : "none supplied"}
- Retrieved content IDs: ${relevantContent.map((item) => item.blockId).join(", ") || "none"}

Retrieved content:
${relevantContent.map((item) => `[${item.blockType}:${item.blockId}] ${item.textContent.slice(0, 500)}`).join("\n\n") || "No retrieved content supplied."}

Student question:
${question}

Policy:
1. Do not give direct final answers. Ask one targeted question or give one next-step hint.
2. If the student asks for the answer, acknowledge that and redirect to a reasoning step.
3. If the question is off-topic, redirect to the current module and claims.
4. Use the current simulation state, recent attempts, and misconceptions when choosing the next hint.
5. Stay within the allowed help mode: ${context.allowedHelpMode}.
6. Cite the module claim or retrieved content when relevant.

Detected request flags:
- answerSeeking: ${classification.answerSeeking}
- offTopic: ${classification.offTopic}

Response:`;
}
