/**
 * AI Explanation Component
 *
 * Hover explanation component that provides AI-generated context
 * for any data element, field, or value.
 *
 * Features:
 * - AI-generated explanations on hover
 * - Context-aware descriptions
 * - PII detection badges
 * - Anomaly highlighting
 *
 * @doc.type component
 * @doc.purpose AI-powered hover explanations
 * @doc.layer frontend
 */

import React, { useState, useRef, useEffect } from 'react';
import {
  Sparkles,
  AlertTriangle,
  Shield,
  Info,
  Loader2,
  X,
} from 'lucide-react';
import { cn } from '../../lib/theme';

/**
 * Explanation types
 */
export type ExplanationType = 'value' | 'field' | 'anomaly' | 'pii' | 'pattern';

/**
 * PII types
 */
export type PIIType = 'SSN' | 'EMAIL' | 'PHONE' | 'CREDIT_CARD' | 'ADDRESS' | 'NAME';

/**
 * Explanation data
 */
export interface ExplanationData {
  type: ExplanationType;
  title: string;
  description: string;
  confidence?: number;
  piiType?: PIIType;
  isAnomaly?: boolean;
  suggestedAction?: string;
  metadata?: Record<string, unknown>;
}

export interface AIExplanationProps {
  explanation?: ExplanationData;
  isLoading?: boolean;
  showOnHover?: boolean;
  position?: 'top' | 'bottom' | 'left' | 'right';
  className?: string;
  children: React.ReactNode;
}

/**
 * Type icons and colors
 */
const TYPE_CONFIG: Record<
  ExplanationType,
  { icon: React.ReactNode; color: string; bgColor: string }
> = {
  value: {
    icon: <Info className="h-3 w-3" />,
    color: 'text-blue-600 dark:text-blue-400',
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
  },
  field: {
    icon: <Sparkles className="h-3 w-3" />,
    color: 'text-purple-600 dark:text-purple-400',
    bgColor: 'bg-purple-100 dark:bg-purple-900/30',
  },
  anomaly: {
    icon: <AlertTriangle className="h-3 w-3" />,
    color: 'text-amber-600 dark:text-amber-400',
    bgColor: 'bg-amber-100 dark:bg-amber-900/30',
  },
  pii: {
    icon: <Shield className="h-3 w-3" />,
    color: 'text-red-600 dark:text-red-400',
    bgColor: 'bg-red-100 dark:bg-red-900/30',
  },
  pattern: {
    icon: <Sparkles className="h-3 w-3" />,
    color: 'text-green-600 dark:text-green-400',
    bgColor: 'bg-green-100 dark:bg-green-900/30',
  },
};

/**
 * AI Explanation Component
 */
export function AIExplanation({
  explanation,
  isLoading = false,
  showOnHover = true,
  position = 'top',
  className,
  children,
}: AIExplanationProps) {
  const [isVisible, setIsVisible] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Clean up timeout on unmount
  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  const handleMouseEnter = () => {
    if (showOnHover) {
      timeoutRef.current = setTimeout(() => {
        setIsVisible(true);
      }, 500); // 500ms delay before showing
    }
  };

  const handleMouseLeave = () => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
    setIsVisible(false);
  };

  // Position classes
  const positionClasses = {
    top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
    bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
    left: 'right-full top-1/2 -translate-y-1/2 mr-2',
    right: 'left-full top-1/2 -translate-y-1/2 ml-2',
  };

  const arrowClasses = {
    top: 'top-full left-1/2 -translate-x-1/2 border-t-white dark:border-t-gray-900 border-x-transparent border-b-transparent',
    bottom: 'bottom-full left-1/2 -translate-x-1/2 border-b-white dark:border-b-gray-900 border-x-transparent border-t-transparent',
    left: 'left-full top-1/2 -translate-y-1/2 border-l-white dark:border-l-gray-900 border-y-transparent border-r-transparent',
    right: 'right-full top-1/2 -translate-y-1/2 border-r-white dark:border-r-gray-900 border-y-transparent border-l-transparent',
  };

  const config = explanation ? TYPE_CONFIG[explanation.type] : TYPE_CONFIG.value;

  return (
    <div
      ref={containerRef}
      className={cn('relative inline-flex', className)}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      {/* Content with optional visual indicator */}
      <span
        className={cn(
          'inline-flex',
          explanation?.isAnomaly && 'border-b-2 border-dashed border-amber-400',
          explanation?.piiType && 'border-b-2 border-dashed border-red-400'
        )}
      >
        {children}
      </span>

      {/* Tooltip */}
      {isVisible && (
        <div
          className={cn(
            'absolute z-50',
            'w-64 bg-white dark:bg-gray-900',
            'border border-gray-200 dark:border-gray-700',
            'rounded-lg shadow-xl',
            'animate-in fade-in-0 zoom-in-95 duration-200',
            positionClasses[position]
          )}
        >
          {isLoading ? (
            <div className="flex items-center justify-center gap-2 p-4">
              <Loader2 className="h-4 w-4 text-primary-500 animate-spin" />
              <span className="text-xs text-gray-500">AI analyzing...</span>
            </div>
          ) : explanation ? (
            <>
              {/* Header */}
              <div
                className={cn(
                  'flex items-center gap-2 px-3 py-2',
                  'border-b border-gray-100 dark:border-gray-800',
                  config.bgColor
                )}
              >
                <span className={config.color}>{config.icon}</span>
                <span className={cn('text-xs font-medium', config.color)}>
                  {explanation.title}
                </span>
                {explanation.confidence !== undefined && (
                  <span className="ml-auto text-xs text-gray-400">
                    {Math.round(explanation.confidence * 100)}%
                  </span>
                )}
              </div>

              {/* Body */}
              <div className="px-3 py-2">
                <p className="text-xs text-gray-600 dark:text-gray-300">
                  {explanation.description}
                </p>

                {/* PII Badge */}
                {explanation.piiType && (
                  <div className="mt-2 flex items-center gap-1">
                    <Shield className="h-3 w-3 text-red-500" />
                    <span className="text-xs font-medium text-red-600 dark:text-red-400">
                      Contains {explanation.piiType}
                    </span>
                  </div>
                )}

                {/* Anomaly Badge */}
                {explanation.isAnomaly && (
                  <div className="mt-2 flex items-center gap-1">
                    <AlertTriangle className="h-3 w-3 text-amber-500" />
                    <span className="text-xs font-medium text-amber-600 dark:text-amber-400">
                      Anomaly Detected
                    </span>
                  </div>
                )}

                {/* Suggested Action */}
                {explanation.suggestedAction && (
                  <div className="mt-2 pt-2 border-t border-gray-100 dark:border-gray-800">
                    <p className="text-xs text-gray-500">
                      💡 {explanation.suggestedAction}
                    </p>
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="p-3 text-center">
              <p className="text-xs text-gray-400">No explanation available</p>
            </div>
          )}

          {/* Arrow */}
          <div
            className={cn(
              'absolute border-8',
              arrowClasses[position]
            )}
          />
        </div>
      )}
    </div>
  );
}

export default AIExplanation;

