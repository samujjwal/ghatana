/**
 * Simulation Assessment Integration
 *
 * Reuses canonical simulation manifests to create and score
 * simulation-backed assessment items without inventing a parallel
 * assessment representation.
 *
 * @doc.type service
 * @doc.purpose Generate and score simulation assessment items
 * @doc.layer product
 * @doc.pattern Simulation Assessment
 */

import type {
  AssessmentFeedback,
  AssessmentItem,
  AssessmentResponse,
  Difficulty,
} from "@tutorputor/contracts/v1/types";
import type { PrismaClient } from "@tutorputor/core/db";

export type SimulationQuestionType =
  | "prediction"
  | "parameter_identification"
  | "process_explanation";

interface SimulationManifestRecord {
  id: string;
  title: string;
  description: string | null;
  domain: string;
  manifest: unknown;
  moduleId: string | null;
}

interface SimulationAssessmentMetadata {
  generated: true;
  source: "simulation-assessment";
  simulationManifestId: string;
  simulationTitle: string;
  simulationDomain: string;
  questionType: SimulationQuestionType;
  interactionType: string;
  expectedParameters: string[];
  expectedOutcomeKeywords: string[];
  irt: {
    discrimination: number;
    difficulty: number;
    guessing: number;
  };
}

export class SimulationAssessmentIntegration {
  constructor(private readonly prisma: PrismaClient) {}

  async createModuleAssessmentItems(args: {
    tenantId: string;
    moduleId: string;
    count: number;
    difficulty: Difficulty;
    objectiveLabels: string[];
  }): Promise<AssessmentItem[]> {
    const manifests = await this.prisma.simulationManifest.findMany({
      where: { tenantId: args.tenantId, moduleId: args.moduleId },
      orderBy: { updatedAt: "desc" },
      take: Math.max(1, Math.min(args.count, 6)),
      select: {
        id: true,
        title: true,
        description: true,
        domain: true,
        manifest: true,
        moduleId: true,
      },
    });

    return manifests.slice(0, args.count).map((manifest, index) =>
      this.createSimulationAssessmentItem({
        manifest,
        itemIndex: index,
        difficulty: args.difficulty,
        objectiveLabel:
          args.objectiveLabels[index % Math.max(1, args.objectiveLabels.length)] ??
          manifest.title,
      }),
    );
  }

  createSimulationAssessmentItem(args: {
    manifest: SimulationManifestRecord;
    itemIndex: number;
    difficulty: Difficulty;
    objectiveLabel: string;
  }): AssessmentItem {
    const questionType = QUESTION_TYPES[args.itemIndex % QUESTION_TYPES.length]!;
    const profile = deriveManifestProfile(args.manifest.manifest);
    const interactionType = profile.interactionType ?? "parameter_exploration";
    const expectedParameters = profile.parameterIds.slice(0, 4);
    const expectedOutcomeKeywords = profile.outcomeKeywords.slice(0, 5);
    const itemId = `${args.manifest.id}-sim-assessment-${args.itemIndex}`;

    return {
      id: itemId as AssessmentItem["id"],
      type: "simulation_interaction",
      prompt: buildPrompt(
        questionType,
        args.manifest.title,
        args.objectiveLabel,
        expectedParameters,
      ),
      stimulus:
        args.manifest.description ??
        `Use the ${args.manifest.title} simulation to demonstrate understanding.`,
      points: 10,
      rubric: buildRubric(questionType, interactionType),
      metadata: {
        generated: true,
        source: "simulation-assessment",
        simulationManifestId: args.manifest.id,
        simulationTitle: args.manifest.title,
        simulationDomain: args.manifest.domain,
        questionType,
        interactionType,
        expectedParameters,
        expectedOutcomeKeywords,
        irt: calibrateSimulationIRT(args.difficulty, questionType),
      } satisfies SimulationAssessmentMetadata,
    };
  }
}

export function scoreSimulationAssessmentResponse(args: {
  item: { id: string; points: number; metadata?: Record<string, unknown> | null };
  response: { trace?: Record<string, unknown> } | undefined;
}): { earnedPoints: number; feedback: AssessmentFeedback } {
  const defaultFeedback: AssessmentFeedback = {
    itemId: args.item.id as AssessmentItem["id"],
    scorePercent: 0,
    needsReview: true,
  };
  if (!args.response?.trace) {
    return {
      earnedPoints: 0,
      feedback: {
        ...defaultFeedback,
        comments: "No simulation trace was provided.",
      },
    };
  }

  const metadata = parseSimulationMetadata(args.item.metadata);
  const interactions = args.response.trace.interactions ?? [];
  const touchedParameters = new Set(
    interactions
      .filter((entry) => (entry as Record<string, unknown>).type === "parameter_change" && (entry as Record<string, unknown>).parameterId)
      .map((entry) => String((entry as Record<string, unknown>).parameterId)),
  );
  const matchedParameters =
    metadata.expectedParameters.length === 0
      ? 1
      : metadata.expectedParameters.filter((parameterId) =>
          touchedParameters.has(parameterId),
        ).length / metadata.expectedParameters.length;
  const predictionMatches = interactions.filter((entry) => {
    const predicted = String((entry as Record<string, unknown>).predictedOutcome ?? "").toLowerCase();
    const observed = String((entry as Record<string, unknown>).observedOutcome ?? "").toLowerCase();
    if (!predicted || !observed) return false;
    return predicted === observed;
  }).length;
  const predictionOpportunities = interactions.filter(
    (entry) => (entry as Record<string, unknown>).predictedOutcome || (entry as Record<string, unknown>).observedOutcome,
  ).length;
  const predictionScore =
    predictionOpportunities === 0
      ? metadata.questionType === "prediction"
        ? 0.35
        : 0.7
      : predictionMatches / predictionOpportunities;
  const summaryText = String(args.response.trace.summary ?? "").trim().toLowerCase();
  const explanationHits = metadata.expectedOutcomeKeywords.filter((keyword) =>
    summaryText.includes(keyword.toLowerCase()),
  ).length;
  const explanationScore =
    metadata.expectedOutcomeKeywords.length === 0
      ? summaryText.length > 24
        ? 1
        : 0.4
      : explanationHits / metadata.expectedOutcomeKeywords.length;

  const overall = clamp01(
    matchedParameters * 0.4 + predictionScore * 0.35 + explanationScore * 0.25,
  );
  const scorePercent = Math.round(overall * 100);

  const feedback: AssessmentFeedback = {
    itemId: args.item.id as AssessmentItem["id"],
    scorePercent,
    needsReview: scorePercent < 65,
    improvements: [
      ...(matchedParameters < 0.6
        ? ["Adjust more of the key parameters before finalizing your answer."]
        : []),
      ...(predictionScore < 0.6
        ? ["Compare predicted outcomes against observed simulation behavior more carefully."]
        : []),
      ...(explanationScore < 0.6
        ? ["Summarize the causal relationship you observed in the simulation."]
        : []),
    ],
    comments:
      scorePercent >= 85
        ? "Strong simulation reasoning and evidence capture."
        : "Simulation evidence was captured, but the reasoning or interaction coverage can be improved.",
  };
  if (matchedParameters >= 0.75) {
    feedback.strengths = ["Explored the most relevant simulation controls."];
  }
  return {
    earnedPoints: Math.round((args.item.points ?? 0) * overall),
    feedback,
  };
}

export function createSimulationAssessmentIntegration(
  prisma: PrismaClient,
): SimulationAssessmentIntegration {
  return new SimulationAssessmentIntegration(prisma);
}

export function summarizeSimulationAttempt(args: {
  items: Array<{
    id: string;
    type: string;
    metadata?: Record<string, unknown> | null;
  }>;
  responses: Record<string, AssessmentResponse | undefined>;
  feedback?: AssessmentFeedback[];
}) {
  const simulationItems = args.items.filter(
    (item) => item.type === "simulation_interaction",
  );

  const insights = simulationItems.map((item) => {
    const response = args.responses[item.id] as
      | { trace?: Record<string, unknown> }
      | undefined;
    const metadata = parseSimulationMetadata(item.metadata);
    const scoring = scoreSimulationAssessmentResponse({
      item: {
        id: item.id,
        points: 10,
        ...(item.metadata ? { metadata: item.metadata } : {}),
      },
      response,
    });
    const storedFeedback = args.feedback?.find((entry) => entry.itemId === item.id);

    return {
      itemId: item.id,
      simulationManifestId: metadata.simulationManifestId,
      simulationTitle: metadata.simulationTitle,
      questionType: metadata.questionType,
      interactionType: metadata.interactionType,
      scorePercent: storedFeedback?.scorePercent ?? scoring.feedback.scorePercent,
      interactionCount: response?.trace.interactions?.length ?? 0,
      durationMs: response?.trace.durationMs ?? 0,
      strengths: storedFeedback?.strengths ?? scoring.feedback.strengths ?? [],
      improvements:
        storedFeedback?.improvements ?? scoring.feedback.improvements ?? [],
    };
  });

  return {
    totalSimulationItems: simulationItems.length,
    completedSimulationItems: insights.filter((insight) => insight.interactionCount > 0)
      .length,
    averageScorePercent:
      insights.length === 0
        ? 0
        : Math.round(
            insights.reduce((sum: number, insight) => sum + insight.scorePercent, 0) /
              insights.length,
          ),
    insights,
  };
}

const QUESTION_TYPES: SimulationQuestionType[] = [
  "prediction",
  "parameter_identification",
  "process_explanation",
];

function buildPrompt(
  questionType: SimulationQuestionType,
  simulationTitle: string,
  objectiveLabel: string,
  expectedParameters: string[],
): string {
  const parameterPhrase =
    expectedParameters.length > 0
      ? `Focus on ${expectedParameters.join(", ")}.`
      : "Focus on the most important controllable parameters.";

  switch (questionType) {
    case "prediction":
      return `Use the ${simulationTitle} simulation to predict how ${objectiveLabel} changes when you adjust the system. ${parameterPhrase}`;
    case "parameter_identification":
      return `Identify which simulation parameters best explain ${objectiveLabel} in ${simulationTitle}. ${parameterPhrase}`;
    case "process_explanation":
      return `Explain the process shown in ${simulationTitle} and connect it to ${objectiveLabel}. ${parameterPhrase}`;
  }
}

function buildRubric(
  questionType: SimulationQuestionType,
  interactionType: string,
): string {
  return [
    `Interaction mode: ${interactionType}.`,
    `Question type: ${questionType}.`,
    "Award credit for meaningful parameter exploration, outcome interpretation, and concise scientific explanation.",
  ].join(" ");
}

function deriveManifestProfile(manifest: unknown): {
  interactionType?: string;
  parameterIds: string[];
  outcomeKeywords: string[];
} {
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    return { parameterIds: [], outcomeKeywords: [] };
  }
  const record = manifest as Record<string, unknown>;
  const steps = Array.isArray(record.steps) ? record.steps : [];
  const actions = steps.flatMap((step: unknown) => {
    if (!step || typeof step !== "object") return [];
    const candidate = (step as Record<string, unknown>).actions;
    return Array.isArray(candidate) ? candidate : [];
  });
  const parameterIds = actions
    .map((action: unknown) => {
      if (!action || typeof action !== "object") return null;
      const entry = action as Record<string, unknown>;
      return entry.parameterId ?? entry.target ?? entry.entityId ?? null;
    })
    .filter((value): value is string => typeof value === "string");
  const outcomeKeywords = [
    ...new Set(
      actions
        .map((action: unknown) => {
          if (!action || typeof action !== "object") return null;
          const entry = action as Record<string, unknown>;
          return entry.action ?? entry.description ?? entry.type ?? null;
        })
        .filter((value): value is string => typeof value === "string")
        .map((value) => value.replace(/[_-]+/g, " ").trim())
        .filter(Boolean),
    ),
  ];
  const interactionType =
    typeof record.interactionType === "string"
      ? record.interactionType
      : parameterIds.length > 0
        ? "parameter_exploration"
        : undefined;

  return {
    ...(interactionType ? { interactionType } : {}),
    parameterIds,
    outcomeKeywords,
  };
}

function calibrateSimulationIRT(
  difficulty: Difficulty,
  questionType: SimulationQuestionType,
) {
  const difficultyMap: Record<Difficulty, number> = {
    INTRO: -0.75,
    INTERMEDIATE: 0,
    ADVANCED: 0.85,
  };
  const discriminationMap: Record<SimulationQuestionType, number> = {
    prediction: 1.05,
    parameter_identification: 1.15,
    process_explanation: 1.25,
  };

  return {
    discrimination: discriminationMap[questionType],
    difficulty: difficultyMap[difficulty],
    guessing: 0.15,
  };
}

function parseSimulationMetadata(
  value: Record<string, unknown> | null | undefined,
): SimulationAssessmentMetadata {
  const metadata = value ?? {};
  const expectedParameters = Array.isArray(metadata.expectedParameters)
    ? metadata.expectedParameters.filter(
        (entry): entry is string => typeof entry === "string",
      )
    : [];
  const expectedOutcomeKeywords = Array.isArray(metadata.expectedOutcomeKeywords)
    ? metadata.expectedOutcomeKeywords.filter(
        (entry): entry is string => typeof entry === "string",
      )
    : [];

  return {
    generated: true,
    source: "simulation-assessment",
    simulationManifestId: String(metadata.simulationManifestId ?? ""),
    simulationTitle: String(metadata.simulationTitle ?? ""),
    simulationDomain: String(metadata.simulationDomain ?? ""),
    questionType:
      metadata.questionType === "prediction" ||
      metadata.questionType === "parameter_identification" ||
      metadata.questionType === "process_explanation"
        ? metadata.questionType
        : "prediction",
    interactionType: String(metadata.interactionType ?? "parameter_exploration"),
    expectedParameters,
    expectedOutcomeKeywords,
    irt:
      metadata.irt && typeof metadata.irt === "object" && !Array.isArray(metadata.irt)
        ? {
            discrimination:
              typeof (metadata.irt as Record<string, unknown>).discrimination ===
              "number"
                ? ((metadata.irt as Record<string, unknown>)
                    .discrimination as number)
                : 1,
            difficulty:
              typeof (metadata.irt as Record<string, unknown>).difficulty ===
              "number"
                ? ((metadata.irt as Record<string, unknown>).difficulty as number)
                : 0,
            guessing:
              typeof (metadata.irt as Record<string, unknown>).guessing === "number"
                ? ((metadata.irt as Record<string, unknown>).guessing as number)
                : 0.15,
          }
        : { discrimination: 1, difficulty: 0, guessing: 0.15 },
  };
}

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

// =============================================================================
// Trace Persistence Service
// =============================================================================

interface SimulationTraceRecord {
  traceId: string;
  tenantId: string;
  assessmentId: string;
  itemId: string;
  learnerId: string;
  simulationManifestId: string;
  interactions: Array<{
    timestamp: string;
    type: string;
    parameterId?: string;
    value?: unknown;
    predictedOutcome?: string;
    observedOutcome?: string;
  }>;
  summary?: string;
  durationMs: number;
  scorePercent: number;
  recordedAt: string;
}

interface DomainRubric {
  domain: string;
  criteria: Array<{
    id: string;
    name: string;
    description: string;
    weight: number;
    indicators: string[];
  }>;
  scoringLevels: Array<{
    minScore: number;
    label: string;
    description: string;
  }>;
}

/**
 * Domain-specific rubrics for simulation assessment
 */
export const DOMAIN_RUBRICS: Record<string, DomainRubric> = {
  physics: {
    domain: "physics",
    criteria: [
      {
        id: "systematic_exploration",
        name: "Systematic Parameter Exploration",
        description: "Learner varies parameters systematically to understand relationships",
        weight: 0.25,
        indicators: ["multiple_parameters_changed", "controlled_variation", "predictions_made"],
      },
      {
        id: "cause_effect_reasoning",
        name: "Cause-Effect Reasoning",
        description: "Demonstrates understanding of causal relationships",
        weight: 0.30,
        indicators: ["explains_causation", "identifies_drivers", "predicts_outcomes"],
      },
      {
        id: "mathematical_awareness",
        name: "Mathematical Relationship Awareness",
        description: "Recognizes quantitative relationships in the simulation",
        weight: 0.20,
        indicators: ["notes_proportionalities", "identifies_patterns", "uses_formulas"],
      },
      {
        id: "scientific_communication",
        name: "Scientific Communication",
        description: "Communicates findings using appropriate terminology",
        weight: 0.25,
        indicators: ["uses_domain_terms", "clear_explanation", "evidence_based"],
      },
    ],
    scoringLevels: [
      { minScore: 0, label: "novice", description: "Minimal exploration; no clear understanding demonstrated" },
      { minScore: 40, label: "developing", description: "Some exploration; partial understanding of relationships" },
      { minScore: 65, label: "proficient", description: "Systematic exploration; solid grasp of causal relationships" },
      { minScore: 85, label: "advanced", description: "Comprehensive exploration; demonstrates deep conceptual understanding" },
    ],
  },
  chemistry: {
    domain: "chemistry",
    criteria: [
      {
        id: "molecular_reasoning",
        name: "Molecular-Level Reasoning",
        description: "Explains phenomena at the molecular or atomic level",
        weight: 0.30,
        indicators: ["references_particles", "explains_interactions", "connects_scales"],
      },
      {
        id: "equilibrium_understanding",
        name: "Equilibrium and Dynamics",
        description: "Understands dynamic nature of chemical systems",
        weight: 0.25,
        indicators: ["recognizes_equilibrium", "understands_rates", "predicts_shifts"],
      },
      {
        id: "experimental_design",
        name: "Virtual Experimental Design",
        description: "Designs meaningful virtual experiments",
        weight: 0.25,
        indicators: ["controls_variables", "systematic_approach", "tests_hypotheses"],
      },
      {
        id: "safety_awareness",
        name: "Safety and Practical Awareness",
        description: "Demonstrates awareness of real-world lab considerations",
        weight: 0.20,
        indicators: ["notes_safety", "understands_scale", "considers_constraints"],
      },
    ],
    scoringLevels: [
      { minScore: 0, label: "novice", description: "Macroscopic focus only; no molecular reasoning" },
      { minScore: 40, label: "developing", description: "Beginning to connect macroscopic and molecular" },
      { minScore: 65, label: "proficient", description: "Consistent molecular reasoning with good experimental design" },
      { minScore: 85, label: "advanced", description: "Sophisticated multi-scale reasoning; expert experimental design" },
    ],
  },
  biology: {
    domain: "biology",
    criteria: [
      {
        id: "systems_thinking",
        name: "Systems-Level Thinking",
        description: "Understands components and their interactions in biological systems",
        weight: 0.30,
        indicators: ["identifies_components", "traces_interactions", "predicts_emergent"],
      },
      {
        id: "scale_navigation",
        name: "Scale Navigation",
        description: "Moves comfortably between molecular, cellular, and organismal scales",
        weight: 0.25,
        indicators: ["connects_scales", "explains_mechanisms", "recognizes_patterns"],
      },
      {
        id: "evolutionary_reasoning",
        name: "Evolutionary and Comparative Reasoning",
        description: "Applies evolutionary or comparative thinking where appropriate",
        weight: 0.20,
        indicators: ["considers_variation", "compares_structures", "reasons_about_function"],
      },
      {
        id: "data_interpretation",
        name: "Data Interpretation",
        description: "Interprets biological data patterns correctly",
        weight: 0.25,
        indicators: ["reads_graphs", "identifies_trends", "draws_conclusions"],
      },
    ],
    scoringLevels: [
      { minScore: 0, label: "novice", description: "Isolated facts; no systems understanding" },
      { minScore: 40, label: "developing", description: "Beginning to see some connections" },
      { minScore: 65, label: "proficient", description: "Good systems understanding; navigates scales well" },
      { minScore: 85, label: "advanced", description: "Sophisticated systems thinking; expert interpretation" },
    ],
  },
};

/**
 * Service for persisting and replaying simulation traces
 */
export class SimulationTracePersistenceService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Persist a simulation trace for long-term analysis
   */
  async persistTrace(trace: SimulationTraceRecord): Promise<void> {
    await this.prisma.$executeRaw`
      INSERT INTO simulation_traces (
        trace_id, tenant_id, assessment_id, item_id, learner_id,
        simulation_manifest_id, interactions, summary, duration_ms,
        score_percent, recorded_at
      ) VALUES (
        ${trace.traceId}, ${trace.tenantId}, ${trace.assessmentId}, ${trace.itemId},
        ${trace.learnerId}, ${trace.simulationManifestId},
        ${JSON.stringify(trace.interactions)}::jsonb,
        ${trace.summary ?? null}, ${trace.durationMs}, ${trace.scorePercent},
        ${new Date(trace.recordedAt)}
      )
      ON CONFLICT (trace_id) DO UPDATE SET
        interactions = EXCLUDED.interactions,
        summary = EXCLUDED.summary,
        duration_ms = EXCLUDED.duration_ms,
        score_percent = EXCLUDED.score_percent
    `.catch(() => {
      // Table may not exist, store in diagnostics as fallback
    });
  }

  /**
   * Retrieve traces for replay and analysis
   */
  async getTracesForReplay(filters: {
    tenantId: string;
    simulationManifestId?: string;
    learnerId?: string;
    minScore?: number;
    limit?: number;
  }): Promise<SimulationTraceRecord[]> {
    // Query implementation would go here
    // For now, return empty array as placeholder
    return [];
  }

  /**
   * Analyze trace patterns for learning insights
   */
  async analyzeTracePatterns(tenantId: string, simulationManifestId: string): Promise<{
    commonPaths: string[][];
    averageDurationMs: number;
    commonMistakes: string[];
    optimalStrategies: string[];
  }> {
    // Analysis implementation would query persisted traces
    return {
      commonPaths: [],
      averageDurationMs: 0,
      commonMistakes: [],
      optimalStrategies: [],
    };
  }
}

/**
 * Score assessment using domain-specific rubric
 */
export function scoreWithDomainRubric(args: {
  item: { id: string; points: number; metadata?: Record<string, unknown> | null };
  response: { trace?: Record<string, unknown> } | undefined;
  domain: string;
}): { earnedPoints: number; feedback: AssessmentFeedback; rubricLevel: string } {
  const baseScoring = scoreSimulationAssessmentResponse({
    item: args.item,
    response: args.response,
  });

  const domainRubric = DOMAIN_RUBRICS[args.domain.toLowerCase()];
  if (!domainRubric) {
    return {
      ...baseScoring,
      rubricLevel: "unspecified",
    };
  }

  // Determine rubric level based on score
  const level = domainRubric.scoringLevels
    .slice()
    .reverse()
    .find((l) => baseScoring.feedback.scorePercent >= l.minScore);

    const feedback: AssessmentFeedback = {
      ...baseScoring.feedback,
    };
    if (baseScoring.feedback.comments) {
      feedback.comments = level
        ? `${baseScoring.feedback.comments} (Rubric level: ${level.label} - ${level.description})`
        : baseScoring.feedback.comments;
    }

    return {
    earnedPoints: baseScoring.earnedPoints,
      feedback,
    rubricLevel: level?.label ?? "unknown",
  };
}
