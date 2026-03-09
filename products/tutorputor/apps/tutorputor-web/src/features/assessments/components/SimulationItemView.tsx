/**
 * SimulationItemView Component
 *
 * UI component for rendering simulation-based assessment items.
 * Supports prediction, manipulation, explanation, design, and diagnosis modes.
 *
 * @doc.type component
 * @doc.purpose Render simulation assessment items with CBM confidence selectors
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useEffect, useMemo } from "react";
import { Card, Button } from "@ghatana/ui";

// =============================================================================
// Local Types (avoiding external contract dependencies)
// =============================================================================

export type SimulationItemMode =
  | "prediction"
  | "manipulation"
  | "explanation"
  | "design"
  | "diagnosis";

export interface PredictionTarget {
  variableId: string;
  variableName: string;
  unit?: string;
  expectedValue: number;
  tolerance: number;
  toleranceType: "absolute" | "relative" | "percentage";
}

export interface PredictionResponse {
  type: "prediction";
  predictions: Array<{
    variableId: string;
    predictedValue: number;
    confidence?: number;
  }>;
  reasoning?: string;
}

export interface ExplanationResponse {
  type: "explanation";
  explanation: string;
  conceptsMentioned?: string[];
  confidence?: number;
}

export type SimulationResponse = PredictionResponse | ExplanationResponse;

export interface SimulationHint {
  id: string;
  hintId?: string;
  content: string;
  cost: number;
  pointDeduction?: number;
}

export interface ExplanationOptions {
  requiredConcepts?: string[];
  minLength?: number;
  maxLength?: number;
  minWordCount?: number;
  maxWordCount?: number;
}

export interface PredictionOptions {
  targetVariables: PredictionTarget[];
  predictionTime?: number;
  showActualAfterSubmit: boolean;
}

export interface SimulationAssessmentItem {
  id: string;
  mode: SimulationItemMode;
  prompt: string;
  points: number;
  stimulus?: string;
  simulationRef: {
    manifestId: string;
    domain?: string;
  };
  gradingStrategy: {
    method: string;
  };
  predictionOptions?: PredictionOptions;
  explanationOptions?: ExplanationOptions;
  hints?: SimulationHint[];
}

export interface SimulationManifest {
  id: string;
  title: string;
  description?: string;
  domain: string;
}

export interface SimKeyframe {
  stepIndex: number;
  time: number;
}

// =============================================================================
// Component Types
// =============================================================================

export interface SimulationItemViewProps {
  /** The simulation assessment item */
  item: SimulationAssessmentItem;
  /** Simulation manifest */
  manifest: SimulationManifest;
  /** Current simulation keyframe */
  currentKeyframe?: SimKeyframe;
  /** Whether the item is read-only */
  readOnly?: boolean;
  /** Whether to show the answer/feedback */
  showFeedback?: boolean;
  /** Feedback data if available */
  feedback?: {
    score: number;
    maxScore: number;
    breakdown?: Array<{
      criterionId: string;
      criterionName: string;
      score: number;
      maxScore: number;
      feedback?: string;
    }>;
    overallFeedback?: string;
  };
  /** Callback when response changes */
  onResponseChange?: (response: SimulationResponse) => void;
  /** Callback to launch simulation player */
  onLaunchSimulation?: () => void;
  /** Current response (controlled mode) */
  value?: SimulationResponse;
}

// =============================================================================
// Confidence Level Selector (CBM)
// =============================================================================

interface ConfidenceSelectorProps {
  value: number;
  onChange: (level: number) => void;
  disabled?: boolean;
}

const ConfidenceSelector: React.FC<ConfidenceSelectorProps> = ({
  value,
  onChange,
  disabled,
}) => {
  const levels = [
    { level: 1, label: "Low", description: "Not very confident", color: "bg-red-100 dark:bg-red-900/30" },
    { level: 2, label: "Medium", description: "Somewhat confident", color: "bg-yellow-100 dark:bg-yellow-900/30" },
    { level: 3, label: "High", description: "Very confident", color: "bg-green-100 dark:bg-green-900/30" },
  ];

  return (
    <div className="space-y-2">
      <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Confidence Level (CBM)
      </label>
      <div className="flex gap-2">
        {levels.map((l) => (
          <button
            key={l.level}
            onClick={() => onChange(l.level)}
            disabled={disabled}
            className={`
              flex-1 py-2 px-3 rounded-lg border-2 transition-all
              ${
                value === l.level
                  ? `${l.color} border-blue-500`
                  : "border-gray-200 dark:border-gray-700 hover:border-gray-300"
              }
              ${disabled ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}
            `}
          >
            <div className="text-sm font-medium">{l.label}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400">{l.description}</div>
          </button>
        ))}
      </div>
    </div>
  );
};

// =============================================================================
// Prediction Input
// =============================================================================

interface PredictionInputProps {
  targets: PredictionTarget[];
  value?: PredictionResponse;
  onChange: (response: PredictionResponse) => void;
  disabled?: boolean;
}

const PredictionInput: React.FC<PredictionInputProps> = ({
  targets,
  value,
  onChange,
  disabled,
}) => {
  const [predictions, setPredictions] = useState<
    Array<{ variableId: string; predictedValue: number; confidence?: number }>
  >(value?.predictions ?? []);
  const [reasoning, setReasoning] = useState(value?.reasoning ?? "");
  const [confidence, setConfidence] = useState(1);

  const handlePredictionChange = useCallback(
    (variableId: string, predictedValue: number) => {
      const updated = [...predictions];
      const index = updated.findIndex((p) => p.variableId === variableId);
      if (index >= 0) {
        updated[index] = { ...updated[index], predictedValue, confidence };
      } else {
        updated.push({ variableId, predictedValue, confidence });
      }
      setPredictions(updated);
      onChange({
        type: "prediction",
        predictions: updated,
        reasoning,
      });
    },
    [predictions, reasoning, confidence, onChange]
  );

  const handleReasoningChange = useCallback(
    (text: string) => {
      setReasoning(text);
      onChange({
        type: "prediction",
        predictions,
        reasoning: text,
      });
    },
    [predictions, onChange]
  );

  return (
    <div className="space-y-4">
      {/* Prediction inputs for each target */}
      {targets.map((target) => {
        const prediction = predictions.find((p) => p.variableId === target.variableId);
        return (
          <div key={target.variableId} className="space-y-1">
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              {target.variableName}
              {target.unit && <span className="text-gray-500 ml-1">({target.unit})</span>}
            </label>
            <input
              type="number"
              step="any"
              value={prediction?.predictedValue ?? ""}
              onChange={(e) =>
                handlePredictionChange(target.variableId, parseFloat(e.target.value) || 0)
              }
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg 
                       bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100
                       focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder={`Enter your prediction for ${target.variableName}`}
            />
          </div>
        );
      })}

      {/* Reasoning textarea */}
      <div className="space-y-1">
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
          Reasoning (optional)
        </label>
        <textarea
          value={reasoning}
          onChange={(e) => handleReasoningChange(e.target.value)}
          disabled={disabled}
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg
                   bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100
                   focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="Explain your reasoning..."
        />
      </div>

      {/* Confidence selector */}
      <ConfidenceSelector value={confidence} onChange={setConfidence} disabled={disabled} />
    </div>
  );
};

// =============================================================================
// Explanation Input
// =============================================================================

interface ExplanationInputProps {
  item: SimulationAssessmentItem;
  value?: ExplanationResponse;
  onChange: (response: ExplanationResponse) => void;
  disabled?: boolean;
}

const ExplanationInput: React.FC<ExplanationInputProps> = ({
  item,
  value,
  onChange,
  disabled,
}) => {
  const [explanation, setExplanation] = useState(value?.explanation ?? "");
  const options = item.explanationOptions;

  const wordCount = useMemo(() => explanation.split(/\s+/).filter(Boolean).length, [explanation]);

  const handleChange = useCallback(
    (text: string) => {
      setExplanation(text);
      onChange({
        type: "explanation",
        explanation: text,
      });
    },
    [onChange]
  );

  const isWithinLimits =
    (!options?.minWordCount || wordCount >= options.minWordCount) &&
    (!options?.maxWordCount || wordCount <= options.maxWordCount);

  return (
    <div className="space-y-4">
      {/* Required concepts hint */}
      {options?.requiredConcepts && options.requiredConcepts.length > 0 && (
        <div className="p-3 rounded-lg bg-blue-50 dark:bg-blue-900/20 text-sm">
          <span className="font-medium text-blue-800 dark:text-blue-200">
            Consider addressing:
          </span>
          <div className="flex flex-wrap gap-2 mt-2">
            {options.requiredConcepts.map((concept: string) => (
              <span
                key={concept}
                className={`
                  px-2 py-1 rounded text-xs
                  ${
                    explanation.toLowerCase().includes(concept.toLowerCase())
                      ? "bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-200"
                      : "bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300"
                  }
                `}
              >
                {concept}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Explanation textarea */}
      <div className="space-y-1">
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
          Your Explanation
        </label>
        <textarea
          value={explanation}
          onChange={(e) => handleChange(e.target.value)}
          disabled={disabled}
          rows={6}
          className={`
            w-full px-3 py-2 border rounded-lg
            bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100
            focus:outline-none focus:ring-2 focus:ring-blue-500
            ${
              isWithinLimits
                ? "border-gray-300 dark:border-gray-600"
                : "border-yellow-500 dark:border-yellow-600"
            }
          `}
          placeholder="Write your explanation here..."
        />
        <div className="flex justify-between text-xs text-gray-500 dark:text-gray-400">
          <span>
            Word count: {wordCount}
            {options?.minWordCount && options?.maxWordCount && (
              <span className="ml-1">
                ({options.minWordCount}-{options.maxWordCount} recommended)
              </span>
            )}
          </span>
          {!isWithinLimits && (
            <span className="text-yellow-600 dark:text-yellow-400">
              {wordCount < (options?.minWordCount ?? 0)
                ? `Add ${(options?.minWordCount ?? 0) - wordCount} more words`
                : `Reduce by ${wordCount - (options?.maxWordCount ?? Infinity)} words`}
            </span>
          )}
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Feedback Display
// =============================================================================

interface FeedbackDisplayProps {
  feedback: SimulationItemViewProps["feedback"];
}

const FeedbackDisplay: React.FC<FeedbackDisplayProps> = ({ feedback }) => {
  if (!feedback) return null;

  const scorePercent = (feedback.score / feedback.maxScore) * 100;
  const scoreColor =
    scorePercent >= 80
      ? "text-green-600 dark:text-green-400"
      : scorePercent >= 60
        ? "text-yellow-600 dark:text-yellow-400"
        : "text-red-600 dark:text-red-400";

  return (
    <div className="mt-4 p-4 rounded-lg bg-gray-50 dark:bg-gray-800/50 space-y-4">
      {/* Score */}
      <div className="flex items-center justify-between">
        <span className="font-medium text-gray-700 dark:text-gray-300">Score</span>
        <span className={`text-2xl font-bold ${scoreColor}`}>
          {feedback.score.toFixed(1)} / {feedback.maxScore}
        </span>
      </div>

      {/* Progress bar */}
      <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          className={`h-full transition-all ${
            scorePercent >= 80
              ? "bg-green-500"
              : scorePercent >= 60
                ? "bg-yellow-500"
                : "bg-red-500"
          }`}
          style={{ width: `${scorePercent}%` }}
        />
      </div>

      {/* Overall feedback */}
      {feedback.overallFeedback && (
        <p className="text-sm text-gray-600 dark:text-gray-400">{feedback.overallFeedback}</p>
      )}

      {/* Breakdown */}
      {feedback.breakdown && feedback.breakdown.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300">Breakdown</h4>
          {feedback.breakdown.map((criterion) => (
            <div
              key={criterion.criterionId}
              className="p-2 rounded bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700"
            >
              <div className="flex justify-between text-sm">
                <span className="font-medium">{criterion.criterionName}</span>
                <span>
                  {criterion.score.toFixed(1)} / {criterion.maxScore}
                </span>
              </div>
              {criterion.feedback && (
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                  {criterion.feedback}
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const SimulationItemView: React.FC<SimulationItemViewProps> = ({
  item,
  manifest,
  readOnly = false,
  showFeedback = false,
  feedback,
  onResponseChange,
  onLaunchSimulation,
  value,
}) => {
  const [localResponse, setLocalResponse] = useState<SimulationResponse | undefined>(value);

  // Update local response when value prop changes
  useEffect(() => {
    setLocalResponse(value);
  }, [value]);

  const handleResponseChange = useCallback(
    (response: SimulationResponse) => {
      setLocalResponse(response);
      onResponseChange?.(response);
    },
    [onResponseChange]
  );

  const handleLaunchSimulation = useCallback(() => {
    onLaunchSimulation?.();
  }, [onLaunchSimulation]);

  // Get mode icon
  const modeIcon = useMemo(() => {
    switch (item.mode) {
      case "prediction":
        return "🎯";
      case "manipulation":
        return "🔧";
      case "explanation":
        return "📝";
      case "design":
        return "✏️";
      case "diagnosis":
        return "🔍";
      default:
        return "📊";
    }
  }, [item.mode]);

  // Get mode label
  const modeLabel = useMemo(() => {
    switch (item.mode) {
      case "prediction":
        return "Prediction";
      case "manipulation":
        return "Manipulation";
      case "explanation":
        return "Explanation";
      case "design":
        return "Design";
      case "diagnosis":
        return "Diagnosis";
      default:
        return "Simulation";
    }
  }, [item.mode]);

  return (
    <Card className="p-6">
      {/* Header */}
      <div className="flex items-start gap-4 mb-4">
        <div className="text-3xl">{modeIcon}</div>
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-medium px-2 py-0.5 rounded bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300">
              {modeLabel}
            </span>
            <span className="text-xs text-gray-500 dark:text-gray-400">
              {item.points} points
            </span>
          </div>
          <p className="text-gray-900 dark:text-gray-100 font-medium">{item.prompt}</p>
          {item.stimulus && (
            <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{item.stimulus}</p>
          )}
        </div>
      </div>

      {/* Simulation launcher */}
      <div className="mb-4 p-4 rounded-lg bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 border border-blue-200 dark:border-blue-800">
        <div className="flex items-center justify-between">
          <div>
            <h4 className="font-medium text-gray-900 dark:text-gray-100">{manifest.title}</h4>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              {manifest.domain} Simulation
            </p>
          </div>
          <Button onClick={handleLaunchSimulation} disabled={readOnly && !showFeedback}>
            {readOnly ? "View Simulation" : "Open Simulation"}
          </Button>
        </div>
      </div>

      {/* Mode-specific input */}
      {!readOnly && (
        <div className="mb-4">
          {item.mode === "prediction" && item.predictionOptions && (
            <PredictionInput
              targets={item.predictionOptions.targetVariables}
              value={localResponse as PredictionResponse | undefined}
              onChange={handleResponseChange}
              disabled={readOnly}
            />
          )}

          {item.mode === "explanation" && (
            <ExplanationInput
              item={item}
              value={localResponse as ExplanationResponse | undefined}
              onChange={handleResponseChange}
              disabled={readOnly}
            />
          )}

          {item.mode === "manipulation" && (
            <div className="text-center py-8 text-gray-500 dark:text-gray-400">
              <p>Open the simulation to complete this task.</p>
              <p className="text-sm mt-2">
                Your actions will be recorded automatically.
              </p>
            </div>
          )}

          {item.mode === "design" && (
            <div className="text-center py-8 text-gray-500 dark:text-gray-400">
              <p>Open the simulation to design your solution.</p>
            </div>
          )}

          {item.mode === "diagnosis" && (
            <div className="text-center py-8 text-gray-500 dark:text-gray-400">
              <p>Open the simulation to identify issues.</p>
            </div>
          )}
        </div>
      )}

      {/* Hints */}
      {item.hints && item.hints.length > 0 && !readOnly && (
        <div className="mb-4">
          <details className="group">
            <summary className="cursor-pointer text-sm text-blue-600 dark:text-blue-400 hover:underline">
              Need a hint? ({item.hints.length} available)
            </summary>
            <div className="mt-2 space-y-2">
              {item.hints.map((hint: SimulationHint, index: number) => (
                <div
                  key={hint.hintId ?? hint.id}
                  className="p-2 rounded bg-yellow-50 dark:bg-yellow-900/20 text-sm"
                >
                  <span className="font-medium">Hint {index + 1}:</span> {hint.content}
                  {hint.pointDeduction && (
                    <span className="text-yellow-600 dark:text-yellow-400 ml-2">
                      (-{hint.pointDeduction} points)
                    </span>
                  )}
                </div>
              ))}
            </div>
          </details>
        </div>
      )}

      {/* Feedback */}
      {showFeedback && feedback && <FeedbackDisplay feedback={feedback} />}
    </Card>
  );
};

export default SimulationItemView;
