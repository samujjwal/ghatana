/**
 * AI Confidence Indicator Component
 *
 * Displays AI confidence level with consistent visual treatment.
 * Part of the centralized AI request/response pattern.
 *
 * @doc.type component
 * @doc.purpose Visualize AI confidence for transparency
 * @doc.layer shared
 * @doc.pattern AI Component
 * @example
 * ```tsx
 * <AIConfidenceIndicator confidence="high" />
 * <AIConfidenceIndicator confidence="medium" showLabel />
 * ```
 */

import { AlertTriangle, CheckCircle2, Sparkles } from "lucide-react";
import React from "react";
import { cn } from "../../lib/theme";

export type AIConfidence = "high" | "medium" | "low" | "unknown";

interface AIConfidenceIndicatorProps {
  confidence: AIConfidence;
  /** Show text label alongside icon */
  showLabel?: boolean;
  /** Additional context text */
  context?: string;
  className?: string;
}

const confidenceConfig: Record<
  AIConfidence,
  {
    label: string;
    icon: React.ReactNode;
    badgeClass: string;
    textClass: string;
  }
> = {
  high: {
    label: "High confidence",
    icon: <CheckCircle2 className="h-4 w-4" aria-hidden="true" />,
    badgeClass:
      "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
    textClass: "text-green-700 dark:text-green-300",
  },
  medium: {
    label: "Medium confidence — review recommended",
    icon: <Sparkles className="h-4 w-4" aria-hidden="true" />,
    badgeClass:
      "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
    textClass: "text-amber-700 dark:text-amber-300",
  },
  low: {
    label: "Low confidence — careful review required",
    icon: <AlertTriangle className="h-4 w-4" aria-hidden="true" />,
    badgeClass: "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300",
    textClass: "text-red-700 dark:text-red-300",
  },
  unknown: {
    label: "Confidence unknown",
    icon: <Sparkles className="h-4 w-4" aria-hidden="true" />,
    badgeClass: "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400",
    textClass: "text-gray-600 dark:text-gray-400",
  },
};

export const AIConfidenceIndicator = React.memo(function AIConfidenceIndicator({
  confidence,
  showLabel = false,
  context,
  className,
}: AIConfidenceIndicatorProps) {
  const config = confidenceConfig[confidence];

  return (
    <div
      className={cn("inline-flex items-center gap-1.5", className)}
      role="status"
      aria-label={config.label}
    >
      <span
        className={cn(
          "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium",
          config.badgeClass,
        )}
      >
        {config.icon}
        {showLabel && <span>{config.label}</span>}
      </span>
      {context && (
        <span className={cn("text-xs", config.textClass)}>{context}</span>
      )}
    </div>
  );
});

AIConfidenceIndicator.displayName = "AIConfidenceIndicator";

export default AIConfidenceIndicator;
