/**
 * Confidence Capture Component
 *
 * Captures learner confidence levels (low/medium/high) for assessment submissions.
 * Blocks submission until confidence is selected for CBM scoring.
 *
 * @doc.type component
 * @doc.purpose Confidence-based marking input for assessments
 * @doc.layer web
 * @doc.pattern Form
 */

import React, { useState } from "react";
import { z } from "zod";

// ============================================================================
// Types
// ============================================================================

export enum ConfidenceLevel {
  LOW = "low",
  MEDIUM = "medium",
  HIGH = "high",
}

export interface ConfidenceCaptureProps {
  onSubmit: (confidence: ConfidenceLevel) => void;
  disabled?: boolean;
  initialConfidence?: ConfidenceLevel;
  showExplanation?: boolean;
  className?: string;
}

// ============================================================================
// Zod Schema
// ============================================================================

export const ConfidenceSchema = z.object({
  confidence: z.nativeEnum(ConfidenceLevel),
});

export type ConfidenceInput = z.infer<typeof ConfidenceSchema>;

// ============================================================================
// Component
// ============================================================================

export function ConfidenceCapture({
  onSubmit,
  disabled = false,
  initialConfidence,
  showExplanation = true,
  className = "",
}: ConfidenceCaptureProps) {
  const [confidence, setConfidence] = useState<ConfidenceLevel | undefined>(
    initialConfidence
  );
  const [error, setError] = useState<string | undefined>();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!confidence) {
      setError("Please select your confidence level before submitting.");
      return;
    }

    const result = ConfidenceSchema.safeParse({ confidence });
    
    if (!result.success) {
      setError("Invalid confidence level.");
      return;
    }

    setError(undefined);
    onSubmit(result.data.confidence);
  };

  const confidenceDescriptions: Record<ConfidenceLevel, { label: string; description: string; color: string }> = {
    [ConfidenceLevel.LOW]: {
      label: "Low",
      description: "I'm guessing or not sure",
      color: "bg-red-100 text-red-800 border-red-300",
    },
    [ConfidenceLevel.MEDIUM]: {
      label: "Medium",
      description: "I'm somewhat confident",
      color: "bg-yellow-100 text-yellow-800 border-yellow-300",
    },
    [ConfidenceLevel.HIGH]: {
      label: "High",
      description: "I'm very confident",
      color: "bg-green-100 text-green-800 border-green-300",
    },
  };

  return (
    <div className={`confidence-capture ${className}`}>
      {showExplanation && (
        <div className="mb-4">
          <h3 className="text-lg font-semibold mb-2">How confident are you in your answer?</h3>
          <p className="text-sm text-gray-600">
            Your confidence level affects how your answer is scored. Be honest - low confidence won't penalize you, but helps us understand your learning.
          </p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {(Object.keys(confidenceDescriptions) as ConfidenceLevel[]).map((level) => {
            const config = confidenceDescriptions[level];
            const isSelected = confidence === level;

            return (
              <button
                key={level}
                type="button"
                onClick={() => {
                  setConfidence(level);
                  setError(undefined);
                }}
                disabled={disabled}
                className={`
                  p-4 rounded-lg border-2 transition-all
                  ${isSelected 
                    ? `${config.color} border-current ring-2 ring-offset-2 ring-current` 
                    : 'bg-white border-gray-200 hover:border-gray-300'
                  }
                  ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
                `}
                aria-pressed={isSelected}
              >
                <div className="font-semibold mb-1">{config.label}</div>
                <div className="text-sm opacity-80">{config.description}</div>
              </button>
            );
          })}
        </div>

        {error && (
          <div className="text-red-600 text-sm mt-2" role="alert">
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={!confidence || disabled}
          className="w-full py-3 px-4 bg-blue-600 text-white rounded-lg font-semibold
                     disabled:bg-gray-300 disabled:cursor-not-allowed
                     hover:bg-blue-700 transition-colors"
        >
          Submit Answer
        </button>
      </form>
    </div>
  );
}

// ============================================================================
// Confidence Indicator (for display)
// ============================================================================

export interface ConfidenceIndicatorProps {
  confidence: ConfidenceLevel;
  size?: "sm" | "md" | "lg";
  showLabel?: boolean;
}

export function ConfidenceIndicator({
  confidence,
  size = "md",
  showLabel = true,
}: ConfidenceIndicatorProps) {
  const config = {
    [ConfidenceLevel.LOW]: {
      label: "Low",
      color: "bg-red-500",
      sizeClasses: { sm: "w-2 h-2", md: "w-3 h-3", lg: "w-4 h-4" },
    },
    [ConfidenceLevel.MEDIUM]: {
      label: "Medium",
      color: "bg-yellow-500",
      sizeClasses: { sm: "w-2 h-2", md: "w-3 h-3", lg: "w-4 h-4" },
    },
    [ConfidenceLevel.HIGH]: {
      label: "High",
      color: "bg-green-500",
      sizeClasses: { sm: "w-2 h-2", md: "w-3 h-3", lg: "w-4 h-4" },
    },
  };

  const configItem = config[confidence];

  return (
    <div className="flex items-center gap-2">
      <div
        className={`rounded-full ${configItem.color} ${configItem.sizeClasses[size]}`}
        aria-label={`Confidence: ${configItem.label}`}
      />
      {showLabel && (
        <span className="text-sm font-medium">{configItem.label}</span>
      )}
    </div>
  );
}

// ============================================================================
// Confidence Summary (for review)
// ============================================================================

export interface ConfidenceSummaryProps {
  confidenceHistory: Array<{
    questionId: string;
    confidence: ConfidenceLevel;
    timestamp: Date;
  }>;
}

export function ConfidenceSummary({ confidenceHistory }: ConfidenceSummaryProps) {
  const averageConfidence = calculateAverageConfidence(confidenceHistory);

  return (
    <div className="p-4 bg-gray-50 rounded-lg">
      <h4 className="font-semibold mb-3">Confidence Summary</h4>
      
      <div className="grid grid-cols-3 gap-4 mb-4">
        <div className="text-center">
          <div className="text-2xl font-bold">{confidenceHistory.length}</div>
          <div className="text-sm text-gray-600">Questions Answered</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-bold">{averageConfidence}</div>
          <div className="text-sm text-gray-600">Average Confidence</div>
        </div>
        <div className="text-center">
          <div className="text-2xl font-bold">
            {confidenceHistory.filter(c => c.confidence === ConfidenceLevel.HIGH).length}
          </div>
          <div className="text-sm text-gray-600">High Confidence</div>
        </div>
      </div>

      <div className="space-y-2">
        {confidenceHistory.map((item, index) => (
          <div key={item.questionId} className="flex items-center justify-between text-sm">
            <span>Question {index + 1}</span>
            <ConfidenceIndicator confidence={item.confidence} size="sm" />
          </div>
        ))}
      </div>
    </div>
  );
}

// ============================================================================
// Utility Functions
// ============================================================================

export function calculateAverageConfidence(
  history: Array<{ confidence: ConfidenceLevel }>
): string {
  if (history.length === 0) return "N/A";

  const confidenceValues = {
    [ConfidenceLevel.LOW]: 1,
    [ConfidenceLevel.MEDIUM]: 2,
    [ConfidenceLevel.HIGH]: 3,
  };

  const sum = history.reduce((acc, item) => acc + confidenceValues[item.confidence], 0);
  const average = sum / history.length;

  if (average >= 2.5) return "High";
  if (average >= 1.5) return "Medium";
  return "Low";
}

export function getConfidenceColor(confidence: ConfidenceLevel): string {
  const colors = {
    [ConfidenceLevel.LOW]: "text-red-600",
    [ConfidenceLevel.MEDIUM]: "text-yellow-600",
    [ConfidenceLevel.HIGH]: "text-green-600",
  };
  return colors[confidence];
}
