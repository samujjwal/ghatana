/**
 * Simulation Item Helper Functions
 *
 * Pure functions to construct, validate, and transform simulation-based
 * assessment items. Provides builders for different assessment modes.
 *
 * @doc.type module
 * @doc.purpose Helper functions for simulation assessment items
 * @doc.layer libs
 * @doc.pattern Builder
 */

import type {
  SimulationAssessmentItem,
  SimulationItemId,
  SimulationItemMode,
  SimulationRef,
  SimulationGradingStrategy,
  ParameterConstraint,
  EntityFocus,
  PredictionOptions,
  PredictionTarget,
  ManipulationOptions,
  ManipulationCondition,
  ExplanationOptions,
  SimulationHint,
  SimulationFeedbackConfig,
  SimulationResponse,
  SimulationGradingResult,
  createSimulationItemId,
} from "@ghatana/tutorputor-contracts/v1/assessments";
import type { SimulationManifest, SimulationDomain, SimEntityId } from "@ghatana/tutorputor-contracts/v1/simulation";
import type { LearningObjective } from "@ghatana/tutorputor-contracts/v1/types";

// =============================================================================
// Validation
// =============================================================================

/**
 * Validation error for simulation items.
 */
export interface SimulationItemValidationError {
  field: string;
  message: string;
  severity: "error" | "warning";
}

/**
 * Validation result for simulation items.
 */
export interface SimulationItemValidationResult {
  valid: boolean;
  errors: SimulationItemValidationError[];
  warnings: SimulationItemValidationError[];
}

/**
 * Validate a simulation assessment item.
 */
export function validateSimulationItem(
  item: SimulationAssessmentItem
): SimulationItemValidationResult {
  const errors: SimulationItemValidationError[] = [];
  const warnings: SimulationItemValidationError[] = [];

  // Required fields
  if (!item.id) {
    errors.push({ field: "id", message: "Item ID is required", severity: "error" });
  }
  if (!item.prompt || item.prompt.trim().length === 0) {
    errors.push({ field: "prompt", message: "Prompt is required", severity: "error" });
  }
  if (item.points <= 0) {
    errors.push({ field: "points", message: "Points must be positive", severity: "error" });
  }
  if (!item.simulationRef?.manifestId) {
    errors.push({
      field: "simulationRef.manifestId",
      message: "Simulation manifest ID is required",
      severity: "error",
    });
  }
  if (!item.gradingStrategy?.method) {
    errors.push({
      field: "gradingStrategy.method",
      message: "Grading method is required",
      severity: "error",
    });
  }

  // Mode-specific validation
  switch (item.mode) {
    case "prediction":
      if (!item.predictionOptions?.targetVariables?.length) {
        errors.push({
          field: "predictionOptions.targetVariables",
          message: "Prediction mode requires at least one target variable",
          severity: "error",
        });
      }
      break;
    case "manipulation":
      if (!item.manipulationOptions?.targetConditions?.length) {
        errors.push({
          field: "manipulationOptions.targetConditions",
          message: "Manipulation mode requires at least one target condition",
          severity: "error",
        });
      }
      break;
    case "explanation":
      if (!item.explanationOptions?.rubricCriteria?.length) {
        warnings.push({
          field: "explanationOptions.rubricCriteria",
          message: "Explanation mode should have rubric criteria for consistent grading",
          severity: "warning",
        });
      }
      break;
  }

  // Grading strategy validation
  if (item.gradingStrategy.method === "kernel_replay" && !item.gradingStrategy.kernelReplayConfig) {
    warnings.push({
      field: "gradingStrategy.kernelReplayConfig",
      message: "Kernel replay method should have configuration",
      severity: "warning",
    });
  }
  if (item.gradingStrategy.method === "rubric" && !item.gradingStrategy.rubricConfig) {
    errors.push({
      field: "gradingStrategy.rubricConfig",
      message: "Rubric method requires rubric configuration",
      severity: "error",
    });
  }

  // Hints validation
  if (item.hints) {
    const hintIds = new Set<string>();
    for (const hint of item.hints) {
      if (hintIds.has(hint.hintId)) {
        errors.push({
          field: `hints[${hint.hintId}]`,
          message: "Duplicate hint ID",
          severity: "error",
        });
      }
      hintIds.add(hint.hintId);
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

// =============================================================================
// Builder Pattern
// =============================================================================

/**
 * Builder for creating simulation assessment items.
 */
export class SimulationItemBuilder {
  private item: Partial<SimulationAssessmentItem> = {
    type: "simulation",
    gradingStrategy: {
      method: "kernel_replay",
      partialCredit: true,
    },
  };

  /**
   * Set the item ID.
   */
  withId(id: string): this {
    this.item.id = id as SimulationItemId;
    return this;
  }

  /**
   * Set the assessment mode.
   */
  withMode(mode: SimulationItemMode): this {
    this.item.mode = mode;
    return this;
  }

  /**
   * Set the prompt.
   */
  withPrompt(prompt: string): this {
    this.item.prompt = prompt;
    return this;
  }

  /**
   * Set the stimulus/context.
   */
  withStimulus(stimulus: string): this {
    this.item.stimulus = stimulus;
    return this;
  }

  /**
   * Set the points.
   */
  withPoints(points: number): this {
    this.item.points = points;
    return this;
  }

  /**
   * Set the simulation reference.
   */
  withSimulationRef(ref: SimulationRef): this {
    this.item.simulationRef = ref;
    return this;
  }

  /**
   * Add parameter constraints.
   */
  withParameterConstraints(constraints: ParameterConstraint[]): this {
    this.item.parameterConstraints = constraints;
    return this;
  }

  /**
   * Add entity focus.
   */
  withEntityFocus(focus: EntityFocus[]): this {
    this.item.entityFocus = focus;
    return this;
  }

  /**
   * Set prediction options.
   */
  withPredictionOptions(options: PredictionOptions): this {
    this.item.mode = "prediction";
    this.item.predictionOptions = options;
    return this;
  }

  /**
   * Set manipulation options.
   */
  withManipulationOptions(options: ManipulationOptions): this {
    this.item.mode = "manipulation";
    this.item.manipulationOptions = options;
    return this;
  }

  /**
   * Set explanation options.
   */
  withExplanationOptions(options: ExplanationOptions): this {
    this.item.mode = "explanation";
    this.item.explanationOptions = options;
    return this;
  }

  /**
   * Set grading strategy.
   */
  withGradingStrategy(strategy: SimulationGradingStrategy): this {
    this.item.gradingStrategy = strategy;
    return this;
  }

  /**
   * Add hints.
   */
  withHints(hints: SimulationHint[]): this {
    this.item.hints = hints;
    return this;
  }

  /**
   * Set feedback configuration.
   */
  withFeedback(feedback: SimulationFeedbackConfig): this {
    this.item.feedback = feedback;
    return this;
  }

  /**
   * Set taxonomy level.
   */
  withTaxonomyLevel(level: LearningObjective["taxonomyLevel"]): this {
    this.item.taxonomyLevel = level;
    return this;
  }

  /**
   * Build the simulation item.
   */
  build(): SimulationAssessmentItem {
    const validation = validateSimulationItem(this.item as SimulationAssessmentItem);
    if (!validation.valid) {
      throw new Error(
        `Invalid simulation item: ${validation.errors.map((e) => e.message).join(", ")}`
      );
    }
    return this.item as SimulationAssessmentItem;
  }
}

/**
 * Create a new simulation item builder.
 */
export function createSimulationItemBuilder(): SimulationItemBuilder {
  return new SimulationItemBuilder();
}

// =============================================================================
// Factory Functions
// =============================================================================

/**
 * Create a prediction-mode simulation item.
 */
export function createPredictionItem(params: {
  id: string;
  prompt: string;
  manifestId: string;
  domain: SimulationDomain;
  targetVariables: PredictionTarget[];
  points?: number;
  taxonomyLevel?: LearningObjective["taxonomyLevel"];
}): SimulationAssessmentItem {
  return createSimulationItemBuilder()
    .withId(params.id)
    .withMode("prediction")
    .withPrompt(params.prompt)
    .withPoints(params.points ?? 10)
    .withSimulationRef({
      manifestId: params.manifestId as any,
      domain: params.domain,
    })
    .withPredictionOptions({
      targetVariables: params.targetVariables,
      showActualAfterSubmit: true,
    })
    .withGradingStrategy({
      method: "kernel_replay",
      partialCredit: true,
      kernelReplayConfig: {
        tolerances: params.targetVariables.reduce(
          (acc, v) => ({ ...acc, [v.variableId]: v.tolerance }),
          {}
        ),
        metricWeights: params.targetVariables.reduce(
          (acc, v) => ({ ...acc, [v.variableId]: 1.0 }),
          {}
        ),
      },
    })
    .withTaxonomyLevel(params.taxonomyLevel ?? "apply")
    .build();
}

/**
 * Create a manipulation-mode simulation item.
 */
export function createManipulationItem(params: {
  id: string;
  prompt: string;
  manifestId: string;
  domain: SimulationDomain;
  targetConditions: ManipulationCondition[];
  parameterConstraints?: ParameterConstraint[];
  maxActions?: number;
  timeLimit?: number;
  points?: number;
  taxonomyLevel?: LearningObjective["taxonomyLevel"];
}): SimulationAssessmentItem {
  return createSimulationItemBuilder()
    .withId(params.id)
    .withMode("manipulation")
    .withPrompt(params.prompt)
    .withPoints(params.points ?? 15)
    .withSimulationRef({
      manifestId: params.manifestId as any,
      domain: params.domain,
    })
    .withParameterConstraints(params.parameterConstraints ?? [])
    .withManipulationOptions({
      targetConditions: params.targetConditions,
      maxActions: params.maxActions,
      timeLimit: params.timeLimit,
    })
    .withGradingStrategy({
      method: "state_comparison",
      partialCredit: true,
    })
    .withTaxonomyLevel(params.taxonomyLevel ?? "analyze")
    .build();
}

/**
 * Create an explanation-mode simulation item.
 */
export function createExplanationItem(params: {
  id: string;
  prompt: string;
  manifestId: string;
  domain: SimulationDomain;
  requiredConcepts: string[];
  rubricCriteria?: Array<{ criterion: string; weight: number }>;
  minWordCount?: number;
  maxWordCount?: number;
  points?: number;
  taxonomyLevel?: LearningObjective["taxonomyLevel"];
}): SimulationAssessmentItem {
  return createSimulationItemBuilder()
    .withId(params.id)
    .withMode("explanation")
    .withPrompt(params.prompt)
    .withPoints(params.points ?? 20)
    .withSimulationRef({
      manifestId: params.manifestId as any,
      domain: params.domain,
    })
    .withExplanationOptions({
      requiredConcepts: params.requiredConcepts,
      minWordCount: params.minWordCount ?? 50,
      maxWordCount: params.maxWordCount ?? 500,
      rubricCriteria: params.rubricCriteria ?? [
        { criterion: "Accuracy of concepts", weight: 0.4 },
        { criterion: "Clarity of explanation", weight: 0.3 },
        { criterion: "Use of simulation evidence", weight: 0.3 },
      ],
    })
    .withGradingStrategy({
      method: "rubric",
      partialCredit: true,
      rubricConfig: {
        criteria: [
          {
            id: "accuracy",
            description: "Accuracy of concepts",
            maxPoints: Math.round((params.points ?? 20) * 0.4),
            levels: [
              { level: 3, description: "All concepts accurate", points: 100 },
              { level: 2, description: "Most concepts accurate", points: 70 },
              { level: 1, description: "Some concepts accurate", points: 40 },
              { level: 0, description: "Major inaccuracies", points: 0 },
            ],
          },
          {
            id: "clarity",
            description: "Clarity of explanation",
            maxPoints: Math.round((params.points ?? 20) * 0.3),
            levels: [
              { level: 3, description: "Very clear", points: 100 },
              { level: 2, description: "Mostly clear", points: 70 },
              { level: 1, description: "Somewhat unclear", points: 40 },
              { level: 0, description: "Unclear", points: 0 },
            ],
          },
          {
            id: "evidence",
            description: "Use of simulation evidence",
            maxPoints: Math.round((params.points ?? 20) * 0.3),
            levels: [
              { level: 3, description: "Strong evidence use", points: 100 },
              { level: 2, description: "Good evidence use", points: 70 },
              { level: 1, description: "Limited evidence", points: 40 },
              { level: 0, description: "No evidence", points: 0 },
            ],
          },
        ],
      },
    })
    .withTaxonomyLevel(params.taxonomyLevel ?? "evaluate")
    .build();
}

// =============================================================================
// Conversion Functions
// =============================================================================

/**
 * Infer simulation item from a manifest.
 */
export function inferSimulationItemFromManifest(
  manifest: SimulationManifest,
  mode: SimulationItemMode = "manipulation"
): Partial<SimulationAssessmentItem> {
  const domain = manifest.domain;

  // Extract entities for focus
  const entityFocus: EntityFocus[] = manifest.initialEntities
    .slice(0, 5)
    .map((e) => ({
      entityId: e.id as SimEntityId,
      entityType: e.type,
      observableProperties: ["x", "y", "value"],
    }));

  // Infer prompt based on domain and mode
  const prompt = inferPromptFromDomain(domain, mode, manifest.title);

  // Infer taxonomy level based on mode
  const taxonomyLevel: LearningObjective["taxonomyLevel"] =
    mode === "prediction"
      ? "apply"
      : mode === "manipulation"
        ? "analyze"
        : mode === "explanation"
          ? "evaluate"
          : mode === "design"
            ? "create"
            : "analyze";

  return {
    type: "simulation",
    mode,
    prompt,
    simulationRef: {
      manifestId: manifest.id,
      domain,
    },
    entityFocus,
    taxonomyLevel,
    points: mode === "explanation" ? 20 : mode === "manipulation" ? 15 : 10,
    gradingStrategy: {
      method: mode === "explanation" ? "rubric" : "kernel_replay",
      partialCredit: true,
    },
  };
}

/**
 * Infer prompt based on domain and mode.
 */
function inferPromptFromDomain(
  domain: SimulationDomain,
  mode: SimulationItemMode,
  title: string
): string {
  const domainPrompts: Record<SimulationDomain, Record<SimulationItemMode, string>> = {
    PHYSICS: {
      prediction: `Based on the initial setup in "${title}", predict the final state of the system.`,
      manipulation: `Modify the parameters in "${title}" to achieve the target outcome.`,
      explanation: `Explain the physics principles demonstrated in "${title}".`,
      design: `Design a configuration that demonstrates the concept shown in "${title}".`,
      diagnosis: `Identify any issues with the physics setup in "${title}".`,
    },
    CHEMISTRY: {
      prediction: `Predict the products and equilibrium state of the reaction in "${title}".`,
      manipulation: `Adjust the conditions to optimize the reaction yield in "${title}".`,
      explanation: `Explain the chemical principles at work in "${title}".`,
      design: `Design a reaction pathway that achieves the goal in "${title}".`,
      diagnosis: `Identify issues with the reaction setup in "${title}".`,
    },
    BIOLOGY: {
      prediction: `Predict the outcome of the biological process in "${title}".`,
      manipulation: `Modify conditions to achieve the target biological response in "${title}".`,
      explanation: `Explain the biological mechanisms shown in "${title}".`,
      design: `Design an experiment configuration for "${title}".`,
      diagnosis: `Identify issues with the biological model in "${title}".`,
    },
    MEDICINE: {
      prediction: `Predict patient outcomes based on the treatment in "${title}".`,
      manipulation: `Adjust dosing to optimize therapeutic effect in "${title}".`,
      explanation: `Explain the pharmacological principles in "${title}".`,
      design: `Design a treatment protocol for "${title}".`,
      diagnosis: `Identify potential drug interactions in "${title}".`,
    },
    ECONOMICS: {
      prediction: `Predict market equilibrium based on "${title}" parameters.`,
      manipulation: `Adjust economic variables to achieve target outcome in "${title}".`,
      explanation: `Explain the economic principles demonstrated in "${title}".`,
      design: `Design a market scenario that illustrates "${title}".`,
      diagnosis: `Identify market inefficiencies in "${title}".`,
    },
    CS_DISCRETE: {
      prediction: `Predict the output of the algorithm in "${title}".`,
      manipulation: `Modify the data structure to achieve optimal complexity in "${title}".`,
      explanation: `Explain the algorithm behavior shown in "${title}".`,
      design: `Design a solution for the problem in "${title}".`,
      diagnosis: `Identify bugs or inefficiencies in "${title}".`,
    },
    ENGINEERING: {
      prediction: `Predict system behavior based on "${title}" configuration.`,
      manipulation: `Optimize the engineering design in "${title}".`,
      explanation: `Explain the engineering principles in "${title}".`,
      design: `Design a system that meets requirements in "${title}".`,
      diagnosis: `Identify design flaws in "${title}".`,
    },
    MATHEMATICS: {
      prediction: `Predict the mathematical result in "${title}".`,
      manipulation: `Find parameter values that satisfy conditions in "${title}".`,
      explanation: `Explain the mathematical concepts in "${title}".`,
      design: `Construct a proof or solution for "${title}".`,
      diagnosis: `Identify errors in the mathematical reasoning in "${title}".`,
    },
  };

  return domainPrompts[domain]?.[mode] ?? `Complete the task in "${title}".`;
}

// =============================================================================
// Scoring Utilities
// =============================================================================

/**
 * Calculate prediction accuracy score.
 */
export function calculatePredictionScore(
  predictions: Array<{ variableId: string; predictedValue: number }>,
  targets: PredictionTarget[]
): number {
  if (targets.length === 0) return 0;

  let totalScore = 0;
  for (const target of targets) {
    const prediction = predictions.find((p) => p.variableId === target.variableId);
    if (!prediction) continue;

    const error = Math.abs(prediction.predictedValue - target.expectedValue);
    let normalizedError: number;

    switch (target.toleranceType) {
      case "percentage":
        normalizedError = error / (Math.abs(target.expectedValue) * (target.tolerance / 100));
        break;
      case "relative":
        normalizedError = error / (Math.abs(target.expectedValue) * target.tolerance);
        break;
      case "absolute":
      default:
        normalizedError = error / target.tolerance;
    }

    // Score is 1 if within tolerance, decreases linearly beyond
    const variableScore = Math.max(0, 1 - normalizedError);
    totalScore += variableScore;
  }

  return (totalScore / targets.length) * 100;
}

/**
 * Calculate manipulation success score.
 */
export function calculateManipulationScore(
  conditionResults: Array<{ conditionId: string; achieved: boolean; partialScore?: number }>,
  conditions: ManipulationCondition[]
): number {
  if (conditions.length === 0) return 0;

  let totalScore = 0;
  for (const condition of conditions) {
    const result = conditionResults.find((r) => r.conditionId === condition.conditionId);
    if (!result) continue;

    if (result.achieved) {
      totalScore += 1;
    } else if (result.partialScore !== undefined && condition.partialCreditThreshold) {
      totalScore += Math.min(result.partialScore, condition.partialCreditThreshold);
    }
  }

  return (totalScore / conditions.length) * 100;
}

/**
 * Apply CBM scoring to a base score.
 */
export function applyCBMScoring(
  baseScore: number,
  confidenceLevel: number,
  cbmConfig: { confidenceLevels: Array<{ level: number; correctMultiplier: number; incorrectPenalty: number }> }
): number {
  const levelConfig = cbmConfig.confidenceLevels.find((l) => l.level === confidenceLevel);
  if (!levelConfig) return baseScore;

  const isCorrect = baseScore >= 50; // Threshold for "correct"

  if (isCorrect) {
    return Math.min(100, baseScore * levelConfig.correctMultiplier);
  } else {
    return Math.max(0, baseScore - levelConfig.incorrectPenalty);
  }
}
