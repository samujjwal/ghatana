/**
 * Co-Pilot Bubble Component
 *
 * AI-powered suggestions bubble that appears in the workflow builder.
 * Part of Journey 4: Co-Pilot Building and Journey 8: Workflow Builder
 *
 * @doc.type component
 * @doc.purpose AI suggestion interface for workflow building
 * @doc.layer frontend
 */

import React, { useState, useEffect } from 'react';
import { Sparkles, X, CheckCircle, ArrowRight, Lightbulb, Zap } from 'lucide-react';
import { Button } from '../common/Button';

interface CoPilotSuggestion {
  id: string;
  type: 'OPTIMIZATION' | 'PATTERN' | 'AUTOMATION' | 'BEST_PRACTICE';
  title: string;
  description: string;
  confidence: number;
  impact: 'LOW' | 'MEDIUM' | 'HIGH';
  action?: {
    label: string;
    autoApply?: boolean;
  };
}

interface CoPilotBubbleProps {
  context?: Record<string, any>;
  suggestions?: CoPilotSuggestion[];
  position?: { x: number; y: number };
  onApply?: (suggestion: CoPilotSuggestion) => void;
  onDismiss?: (suggestionId: string) => void;
  onClose?: () => void;
  autoSuggest?: boolean;
}

export function CoPilotBubble({
  context = {},
  suggestions: externalSuggestions,
  position = { x: 100, y: 100 },
  onApply,
  onDismiss,
  onClose,
  autoSuggest = true,
}: CoPilotBubbleProps) {
  const [suggestions, setSuggestions] = useState<CoPilotSuggestion[]>(
    externalSuggestions || []
  );
  const [activeIndex, setActiveIndex] = useState(0);
  const [isThinking, setIsThinking] = useState(false);

  // Auto-generate suggestions based on context
  useEffect(() => {
    if (autoSuggest && !externalSuggestions && Object.keys(context).length > 0) {
      setIsThinking(true);

      // Simulate AI thinking
      const timer = setTimeout(() => {
        const generatedSuggestions = generateSuggestions(context);
        setSuggestions(generatedSuggestions);
        setIsThinking(false);
      }, 1500);

      return () => clearTimeout(timer);
    }
  }, [context, externalSuggestions, autoSuggest]);

  const handleApply = (suggestion: CoPilotSuggestion) => {
    onApply?.(suggestion);
    // Remove applied suggestion
    setSuggestions(prev => prev.filter(s => s.id !== suggestion.id));
    if (suggestions.length <= 1) {
      onClose?.();
    }
  };

  const handleDismiss = (suggestionId: string) => {
    onDismiss?.(suggestionId);
    setSuggestions(prev => prev.filter(s => s.id !== suggestionId));
    if (suggestions.length <= 1) {
      onClose?.();
    }
  };

  const handleNext = () => {
    setActiveIndex((prev) => (prev + 1) % suggestions.length);
  };

  const handlePrevious = () => {
    setActiveIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'OPTIMIZATION':
        return <Zap className="h-4 w-4" />;
      case 'PATTERN':
        return <Sparkles className="h-4 w-4" />;
      case 'AUTOMATION':
        return <ArrowRight className="h-4 w-4" />;
      case 'BEST_PRACTICE':
        return <Lightbulb className="h-4 w-4" />;
      default:
        return <Sparkles className="h-4 w-4" />;
    }
  };

  const getTypeColor = (type: string): string => {
    switch (type) {
      case 'OPTIMIZATION':
        return 'bg-yellow-100 text-yellow-700 border-yellow-300';
      case 'PATTERN':
        return 'bg-purple-100 text-purple-700 border-purple-300';
      case 'AUTOMATION':
        return 'bg-blue-100 text-blue-700 border-blue-300';
      case 'BEST_PRACTICE':
        return 'bg-green-100 text-green-700 border-green-300';
      default:
        return 'bg-gray-100 text-gray-700 border-gray-300';
    }
  };

  const getImpactColor = (impact: string): string => {
    switch (impact) {
      case 'HIGH':
        return 'text-red-600';
      case 'MEDIUM':
        return 'text-orange-600';
      case 'LOW':
        return 'text-green-600';
      default:
        return 'text-gray-600';
    }
  };

  if (isThinking) {
    return (
      <div
        className="fixed bg-white rounded-lg shadow-2xl border-2 border-primary-300 p-4 w-80 z-50"
        style={{ left: `${position.x}px`, top: `${position.y}px` }}
      >
        <div className="flex items-center gap-3">
          <div className="relative">
            <Sparkles className="h-8 w-8 text-primary-600 animate-pulse" />
            <div className="absolute inset-0 bg-primary-200 rounded-full animate-ping opacity-75"></div>
          </div>
          <div>
            <div className="text-sm font-semibold text-gray-900">
              AI Co-Pilot Thinking...
            </div>
            <div className="text-xs text-gray-600 mt-1">
              Analyzing your workflow
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (suggestions.length === 0) {
    return null;
  }

  const activeSuggestion = suggestions[activeIndex];

  return (
    <div
      className="fixed bg-white rounded-lg shadow-2xl border-2 border-primary-300 w-96 z-50 overflow-hidden"
      style={{ left: `${position.x}px`, top: `${position.y}px` }}
    >
      {/* Header */}
      <div className="bg-gradient-to-r from-primary-500 to-purple-500 p-3 text-white">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sparkles className="h-5 w-5" />
            <span className="font-semibold text-sm">AI Co-Pilot</span>
          </div>
          <button
            onClick={onClose}
            className="text-white hover:bg-white/20 rounded p-1 transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        {suggestions.length > 1 && (
          <div className="text-xs mt-1 opacity-90">
            {activeIndex + 1} of {suggestions.length} suggestions
          </div>
        )}
      </div>

      {/* Suggestion Content */}
      <div className="p-4">
        {/* Type Badge */}
        <div className="flex items-center justify-between mb-3">
          <div
            className={`flex items-center gap-1 px-2 py-1 rounded text-xs font-semibold border ${getTypeColor(
              activeSuggestion.type
            )}`}
          >
            {getTypeIcon(activeSuggestion.type)}
            {activeSuggestion.type.replace('_', ' ')}
          </div>
          <div className="flex items-center gap-2">
            <span className={`text-xs font-semibold ${getImpactColor(activeSuggestion.impact)}`}>
              {activeSuggestion.impact} IMPACT
            </span>
            <div className="text-xs text-gray-600">
              {Math.round(activeSuggestion.confidence * 100)}% confidence
            </div>
          </div>
        </div>

        {/* Title */}
        <h3 className="text-base font-bold text-gray-900 mb-2">
          {activeSuggestion.title}
        </h3>

        {/* Description */}
        <p className="text-sm text-gray-700 mb-4">{activeSuggestion.description}</p>

        {/* Confidence Bar */}
        <div className="mb-4">
          <div className="h-1.5 bg-gray-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-primary-500 transition-all duration-300"
              style={{ width: `${activeSuggestion.confidence * 100}%` }}
            />
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2">
          {activeSuggestion.action && (
            <Button
              variant="primary"
              size="sm"
              onClick={() => handleApply(activeSuggestion)}
              className="flex-1"
            >
              <CheckCircle className="h-4 w-4" />
              {activeSuggestion.action.label}
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleDismiss(activeSuggestion.id)}
          >
            Dismiss
          </Button>
        </div>
      </div>

      {/* Navigation */}
      {suggestions.length > 1 && (
        <div className="border-t border-gray-200 p-2 flex items-center justify-between">
          <button
            onClick={handlePrevious}
            className="px-3 py-1 text-sm text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded transition-colors"
          >
            ← Previous
          </button>
          <div className="flex gap-1">
            {suggestions.map((_, index) => (
              <div
                key={index}
                className={`w-2 h-2 rounded-full transition-colors ${
                  index === activeIndex ? 'bg-primary-500' : 'bg-gray-300'
                }`}
              />
            ))}
          </div>
          <button
            onClick={handleNext}
            className="px-3 py-1 text-sm text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded transition-colors"
          >
            Next →
          </button>
        </div>
      )}

      {/* Sparkle Animation */}
      <div className="absolute top-0 right-0 pointer-events-none">
        {[...Array(3)].map((_, i) => (
          <Sparkles
            key={i}
            className="absolute text-primary-300 animate-pulse"
            style={{
              width: '12px',
              height: '12px',
              top: `${10 + i * 15}px`,
              right: `${10 + i * 10}px`,
              animationDelay: `${i * 0.3}s`,
              opacity: 0.4,
            }}
          />
        ))}
      </div>
    </div>
  );
}

// Helper function to generate suggestions based on context
function generateSuggestions(context: Record<string, any>): CoPilotSuggestion[] {
  const suggestions: CoPilotSuggestion[] = [];

  // Example: Detect missing error handling
  if (context.hasErrorHandling === false) {
    suggestions.push({
      id: 'suggestion-1',
      type: 'BEST_PRACTICE',
      title: 'Add Error Handling',
      description: 'Your workflow lacks error handling. Add try-catch blocks to handle failures gracefully.',
      confidence: 0.92,
      impact: 'HIGH',
      action: {
        label: 'Add Error Handler',
        autoApply: false,
      },
    });
  }

  // Example: Detect optimization opportunity
  if (context.queryCount && context.queryCount > 5) {
    suggestions.push({
      id: 'suggestion-2',
      type: 'OPTIMIZATION',
      title: 'Batch Database Queries',
      description: `You have ${context.queryCount} sequential queries. Batching them can reduce latency by 60%.`,
      confidence: 0.87,
      impact: 'MEDIUM',
      action: {
        label: 'Apply Batching',
        autoApply: false,
      },
    });
  }

  // Example: Detect pattern
  if (context.pattern === 'ETL') {
    suggestions.push({
      id: 'suggestion-3',
      type: 'PATTERN',
      title: 'Use ETL Template',
      description: 'This looks like an ETL pattern. Apply our optimized template with built-in validation.',
      confidence: 0.95,
      impact: 'HIGH',
      action: {
        label: 'Apply Template',
        autoApply: false,
      },
    });
  }

  // Example: Automation suggestion
  if (context.manualSteps > 0) {
    suggestions.push({
      id: 'suggestion-4',
      type: 'AUTOMATION',
      title: 'Automate Manual Steps',
      description: `${context.manualSteps} manual steps detected. These can be automated for consistency.`,
      confidence: 0.78,
      impact: 'MEDIUM',
      action: {
        label: 'Auto-Generate',
        autoApply: false,
      },
    });
  }

  return suggestions;
}

export default CoPilotBubble;

