/**
 * AssessmentRunner Component
 *
 * Main runner component that handles different assessment item types,
 * including simulation-based items. Manages state, navigation, and submission.
 *
 * @doc.type component
 * @doc.purpose Run assessment sessions with support for simulation items
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useMemo } from "react";
import { Card, Button, Progress, Badge } from "@ghatana/ui";
import { SimulationItemView } from "./SimulationItemView";
import type {
  SimulationAssessmentItem,
  SimulationResponse,
  SimulationManifest,
} from "./SimulationItemView";

// =============================================================================
// Types
// =============================================================================

export type AssessmentItemType =
  | "multiple-choice"
  | "free-response"
  | "simulation"
  | "code"
  | "diagram";

export interface MultipleChoiceItem {
  id: string;
  type: "multiple-choice";
  prompt: string;
  points: number;
  options: Array<{ id: string; text: string }>;
  correctOptionId?: string; // Only for preview mode
}

export interface FreeResponseItem {
  id: string;
  type: "free-response";
  prompt: string;
  points: number;
  maxLength?: number;
  rubric?: string;
}

export interface SimulationItem {
  id: string;
  type: "simulation";
  prompt: string;
  points: number;
  simulationData: SimulationAssessmentItem;
}

export type AssessmentItem =
  | MultipleChoiceItem
  | FreeResponseItem
  | SimulationItem;

export interface Assessment {
  id: string;
  title: string;
  description?: string;
  timeLimitMinutes?: number;
  items: AssessmentItem[];
  shuffleItems?: boolean;
  showProgress?: boolean;
  allowNavigation?: boolean;
  allowReview?: boolean;
}

export interface ItemResponse {
  itemId: string;
  response: unknown;
  timeSpentSeconds: number;
  confidence?: number;
  hintsUsed?: string[];
}

export interface AssessmentRunnerProps {
  /** The assessment to run */
  assessment: Assessment;
  /** Callback when assessment is submitted */
  onSubmit: (responses: ItemResponse[]) => Promise<void>;
  /** Callback when user exits (with partial responses) */
  onExit?: (responses: ItemResponse[]) => void;
  /** Whether in preview/review mode (shows correct answers) */
  previewMode?: boolean;
  /** Pre-loaded responses (for resuming or review) */
  initialResponses?: ItemResponse[];
  /** Simulation manifests by ID (pre-loaded) */
  manifests?: Map<string, SimulationManifest>;
  /** Additional CSS classes */
  className?: string;
}

export interface AssessmentRunnerState {
  currentIndex: number;
  responses: Map<string, ItemResponse>;
  startTime: Date;
  itemStartTimes: Map<string, Date>;
  isSubmitting: boolean;
  isComplete: boolean;
}

// =============================================================================
// Component
// =============================================================================

export const AssessmentRunner: React.FC<AssessmentRunnerProps> = ({
  assessment,
  onSubmit,
  onExit,
  previewMode = false,
  initialResponses = [],
  manifests = new Map(),
  className = "",
}) => {
  // State
  const [state, setState] = useState<AssessmentRunnerState>(() => {
    const responsesMap = new Map<string, ItemResponse>();
    initialResponses.forEach((r) => responsesMap.set(r.itemId, r));

    return {
      currentIndex: 0,
      responses: responsesMap,
      startTime: new Date(),
      itemStartTimes: new Map([[assessment.items[0]?.id, new Date()]]),
      isSubmitting: false,
      isComplete: false,
    };
  });

  // Derived state
  const items = useMemo(() => {
    if (assessment.shuffleItems && !previewMode) {
      // Shuffle items deterministically based on assessment ID
      return [...assessment.items].sort(() => Math.random() - 0.5);
    }
    return assessment.items;
  }, [assessment.items, assessment.shuffleItems, previewMode]);

  const currentItem = items[state.currentIndex];
  const progress = ((state.currentIndex + 1) / items.length) * 100;
  const answeredCount = state.responses.size;
  const totalPoints = items.reduce((sum, item) => sum + item.points, 0);

  // Handlers
  const recordItemTime = useCallback(
    (itemId: string): number => {
      const startTime = state.itemStartTimes.get(itemId);
      if (!startTime) return 0;
      return Math.floor((Date.now() - startTime.getTime()) / 1000);
    },
    [state.itemStartTimes]
  );

  const handleResponseChange = useCallback(
    (itemId: string, response: unknown, confidence?: number) => {
      setState((prev) => {
        const newResponses = new Map(prev.responses);
        const existing = newResponses.get(itemId);
        newResponses.set(itemId, {
          itemId,
          response,
          timeSpentSeconds:
            (existing?.timeSpentSeconds ?? 0) + recordItemTime(itemId),
          confidence,
          hintsUsed: existing?.hintsUsed,
        });
        return { ...prev, responses: newResponses };
      });
    },
    [recordItemTime]
  );

  const handleHintUsed = useCallback((itemId: string, hintId: string) => {
    setState((prev) => {
      const newResponses = new Map(prev.responses);
      const existing = newResponses.get(itemId) ?? {
        itemId,
        response: null,
        timeSpentSeconds: 0,
        hintsUsed: [],
      };
      const hintsUsed = [...(existing.hintsUsed ?? []), hintId];
      newResponses.set(itemId, { ...existing, hintsUsed });
      return { ...prev, responses: newResponses };
    });
  }, []);

  const navigateTo = useCallback(
    (index: number) => {
      if (index < 0 || index >= items.length) return;

      setState((prev) => {
        const newItemStartTimes = new Map(prev.itemStartTimes);
        newItemStartTimes.set(items[index].id, new Date());
        return {
          ...prev,
          currentIndex: index,
          itemStartTimes: newItemStartTimes,
        };
      });
    },
    [items]
  );

  const handleNext = useCallback(() => {
    if (state.currentIndex < items.length - 1) {
      navigateTo(state.currentIndex + 1);
    }
  }, [state.currentIndex, items.length, navigateTo]);

  const handlePrev = useCallback(() => {
    if (state.currentIndex > 0 && assessment.allowNavigation !== false) {
      navigateTo(state.currentIndex - 1);
    }
  }, [state.currentIndex, assessment.allowNavigation, navigateTo]);

  const handleSubmit = useCallback(async () => {
    setState((prev) => ({ ...prev, isSubmitting: true }));

    try {
      const responses = Array.from(state.responses.values());
      await onSubmit(responses);
      setState((prev) => ({ ...prev, isComplete: true, isSubmitting: false }));
    } catch (error) {
      setState((prev) => ({ ...prev, isSubmitting: false }));
      console.error("Failed to submit assessment:", error);
    }
  }, [state.responses, onSubmit]);

  const handleExit = useCallback(() => {
    if (onExit) {
      onExit(Array.from(state.responses.values()));
    }
  }, [state.responses, onExit]);

  // Render functions
  const renderItem = (item: AssessmentItem) => {
    const response = state.responses.get(item.id);

    switch (item.type) {
      case "simulation":
        return renderSimulationItem(item, response);
      case "multiple-choice":
        return renderMultipleChoiceItem(item, response);
      case "free-response":
        return renderFreeResponseItem(item, response);
      default:
        return (
          <div className="p-4 text-gray-500">
            Unsupported item type: {(item as AssessmentItem).type}
          </div>
        );
    }
  };

  const renderSimulationItem = (
    item: SimulationItem,
    response?: ItemResponse
  ) => {
    const manifest = manifests.get(item.simulationData.simulationRef.manifestId);

    const handleSimResponse = (simResponse: SimulationResponse) => {
      handleResponseChange(item.id, simResponse);
    };

    const handleSimHint = (hintId: string) => {
      handleHintUsed(item.id, hintId);
    };

    return (
      <SimulationItemView
        item={item.simulationData}
        manifest={manifest ?? null}
        onResponse={handleSimResponse}
        onHintUsed={handleSimHint}
        currentResponse={response?.response as SimulationResponse | undefined}
        showFeedback={previewMode}
        isReadOnly={previewMode}
      />
    );
  };

  const renderMultipleChoiceItem = (
    item: MultipleChoiceItem,
    response?: ItemResponse
  ) => {
    const selectedId = response?.response as string | undefined;

    return (
      <div className="space-y-4">
        <p className="text-lg text-gray-800 dark:text-gray-200">{item.prompt}</p>
        <div className="space-y-2">
          {item.options.map((option) => (
            <button
              key={option.id}
              onClick={() => handleResponseChange(item.id, option.id)}
              className={`w-full p-4 text-left rounded-lg border-2 transition-colors ${
                selectedId === option.id
                  ? "border-blue-500 bg-blue-50 dark:bg-blue-900/30"
                  : "border-gray-200 dark:border-gray-700 hover:border-gray-300"
              } ${
                previewMode && item.correctOptionId === option.id
                  ? "border-green-500 bg-green-50 dark:bg-green-900/30"
                  : ""
              }`}
              disabled={previewMode}
            >
              {option.text}
            </button>
          ))}
        </div>
      </div>
    );
  };

  const renderFreeResponseItem = (
    item: FreeResponseItem,
    response?: ItemResponse
  ) => {
    const text = (response?.response as string) ?? "";

    return (
      <div className="space-y-4">
        <p className="text-lg text-gray-800 dark:text-gray-200">{item.prompt}</p>
        <textarea
          value={text}
          onChange={(e) => handleResponseChange(item.id, e.target.value)}
          placeholder="Enter your response..."
          className="w-full h-40 p-4 border rounded-lg resize-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-700"
          maxLength={item.maxLength}
          disabled={previewMode}
        />
        {item.maxLength && (
          <div className="text-sm text-gray-500 text-right">
            {text.length} / {item.maxLength} characters
          </div>
        )}
      </div>
    );
  };

  // Completion screen
  if (state.isComplete) {
    return (
      <Card className={`p-8 text-center ${className}`}>
        <div className="text-6xl mb-4">✅</div>
        <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
          Assessment Submitted
        </h2>
        <p className="text-gray-600 dark:text-gray-400">
          Your responses have been recorded. You answered {answeredCount} of{" "}
          {items.length} items.
        </p>
      </Card>
    );
  }

  return (
    <div className={`space-y-6 ${className}`}>
      {/* Header */}
      <Card className="p-4">
        <div className="flex items-center justify-between mb-2">
          <h1 className="text-xl font-bold text-gray-900 dark:text-white">
            {assessment.title}
          </h1>
          <div className="flex items-center gap-4">
            <Badge variant="soft" tone="neutral">
              {answeredCount} / {items.length} answered
            </Badge>
            <Badge variant="soft" tone="primary">
              {totalPoints} pts total
            </Badge>
          </div>
        </div>
        {assessment.showProgress !== false && (
          <Progress value={progress} className="h-2" />
        )}
      </Card>

      {/* Current Item */}
      <Card className="p-6">
        <div className="flex items-center justify-between mb-4">
          <Badge variant="outline" tone="neutral">
            Question {state.currentIndex + 1} of {items.length}
          </Badge>
          <Badge variant="soft" tone="secondary">
            {currentItem.points} pts
          </Badge>
        </div>

        {renderItem(currentItem)}
      </Card>

      {/* Navigation */}
      <div className="flex items-center justify-between">
        <Button
          variant="outline"
          onClick={handlePrev}
          disabled={
            state.currentIndex === 0 || assessment.allowNavigation === false
          }
        >
          ← Previous
        </Button>

        <div className="flex items-center gap-2">
          {onExit && (
            <Button variant="outline" tone="danger" onClick={handleExit}>
              Save & Exit
            </Button>
          )}

          {state.currentIndex === items.length - 1 ? (
            <Button
              variant="solid"
              tone="success"
              onClick={handleSubmit}
              disabled={state.isSubmitting}
            >
              {state.isSubmitting ? "Submitting..." : "Submit Assessment"}
            </Button>
          ) : (
            <Button variant="solid" onClick={handleNext}>
              Next →
            </Button>
          )}
        </div>
      </div>

      {/* Item Navigator (if allowed) */}
      {assessment.allowNavigation !== false && items.length > 1 && (
        <Card className="p-4">
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">
            Jump to question:
          </p>
          <div className="flex flex-wrap gap-2">
            {items.map((item, index) => {
              const isAnswered = state.responses.has(item.id);
              const isCurrent = index === state.currentIndex;

              return (
                <button
                  key={item.id}
                  onClick={() => navigateTo(index)}
                  className={`w-8 h-8 rounded-full text-sm font-medium transition-colors ${
                    isCurrent
                      ? "bg-blue-600 text-white"
                      : isAnswered
                      ? "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200"
                      : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400"
                  }`}
                >
                  {index + 1}
                </button>
              );
            })}
          </div>
        </Card>
      )}
    </div>
  );
};

export default AssessmentRunner;
