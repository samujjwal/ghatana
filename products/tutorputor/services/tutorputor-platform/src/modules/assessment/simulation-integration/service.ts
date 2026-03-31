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
  response:
    | Extract<AssessmentResponse, { type: "simulation_interaction" }>
    | undefined;
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
      .filter((entry) => entry.type === "parameter_change" && entry.parameterId)
      .map((entry) => String(entry.parameterId)),
  );
  const matchedParameters =
    metadata.expectedParameters.length === 0
      ? 1
      : metadata.expectedParameters.filter((parameterId) =>
          touchedParameters.has(parameterId),
        ).length / metadata.expectedParameters.length;
  const predictionMatches = interactions.filter((entry) => {
    const predicted = String(entry.predictedOutcome ?? "").toLowerCase();
    const observed = String(entry.observedOutcome ?? "").toLowerCase();
    if (!predicted || !observed) return false;
    return predicted === observed;
  }).length;
  const predictionOpportunities = interactions.filter(
    (entry) => entry.predictedOutcome || entry.observedOutcome,
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
      | Extract<AssessmentResponse, { type: "simulation_interaction" }>
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
            insights.reduce((sum, insight) => sum + insight.scorePercent, 0) /
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
  const actions = steps.flatMap((step) => {
    if (!step || typeof step !== "object") return [];
    const candidate = (step as Record<string, unknown>).actions;
    return Array.isArray(candidate) ? candidate : [];
  });
  const parameterIds = actions
    .map((action) => {
      if (!action || typeof action !== "object") return null;
      const entry = action as Record<string, unknown>;
      return entry.parameterId ?? entry.target ?? entry.entityId ?? null;
    })
    .filter((value): value is string => typeof value === "string");
  const outcomeKeywords = [
    ...new Set(
      actions
        .map((action) => {
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
