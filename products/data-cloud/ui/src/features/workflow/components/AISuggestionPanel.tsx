/**
 * AI suggestion panel component.
 *
 * <p><b>Purpose</b><br>
 * Displays AI-generated workflow suggestions based on collection context.
 * Allows users to browse and instantiate suggested workflows.
 *
 * <p><b>Architecture</b><br>
 * - AI suggestion fetching
 * - Confidence scoring
 * - Template instantiation
 * - Smart defaults
 *
 * @doc.type component
 * @doc.purpose AI workflow suggestions
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useEffect, useState } from 'react';
import { useSetAtom } from 'jotai';
import { loadWorkflowAtom } from '../stores/workflow.store';
import { workflowClient } from '../../../lib/api/workflow-client';
import type { WorkflowSuggestion } from '../types/workflow.types';

/**
 * AISuggestionPanel component props.
 *
 * @doc.type interface
 */
export interface AISuggestionPanelProps {
  collectionId: string;
  onSuggestionApplied?: (workflowId: string) => void;
}

/**
 * AISuggestionPanel component.
 *
 * Displays AI-generated workflow suggestions.
 *
 * @param props component props
 * @returns JSX element
 *
 * @doc.type function
 */
export const AISuggestionPanel: React.FC<AISuggestionPanelProps> = ({
  collectionId,
  onSuggestionApplied,
}) => {
  const loadWorkflow = useSetAtom(loadWorkflowAtom);

  const [suggestions, setSuggestions] = useState<WorkflowSuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  /**
   * Fetches suggestions.
   */
  useEffect(() => {
    const fetchSuggestions = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await workflowClient.getSuggestions(collectionId);
        setSuggestions(response.suggestions);
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to fetch suggestions';
        setError(message);
      } finally {
        setLoading(false);
      }
    };

    if (collectionId) {
      fetchSuggestions();
    }
  }, [collectionId]);

  /**
   * Handles suggestion apply.
   */
  const handleApply = (suggestion: WorkflowSuggestion) => {
    loadWorkflow(suggestion.workflow);
    onSuggestionApplied?.(suggestion.id);
  };

  /**
   * Gets confidence color.
   */
  const getConfidenceColor = (confidence: number) => {
    if (confidence >= 0.8) return 'text-green-600';
    if (confidence >= 0.6) return 'text-yellow-600';
    return 'text-red-600';
  };

  return (
    <div className="flex flex-col h-full bg-white border-l border-gray-200">
      {/* Header */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="font-semibold text-gray-900">AI Suggestions</h3>
        <p className="text-xs text-gray-500 mt-1">
          {suggestions.length} suggestions available
        </p>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {loading && (
          <div className="text-center py-8 text-gray-500">
            <p className="text-sm">Loading suggestions...</p>
          </div>
        )}

        {error && (
          <div className="p-3 bg-red-50 rounded-lg border border-red-200">
            <p className="text-xs text-red-700">{error}</p>
          </div>
        )}

        {!loading && suggestions.length === 0 && !error && (
          <div className="text-center py-8 text-gray-500">
            <p className="text-sm">No suggestions available</p>
          </div>
        )}

        {/* Suggestions */}
        <div className="space-y-3">
          {suggestions.map((suggestion) => (
            <div
              key={suggestion.id}
              onClick={() => setSelectedId(selectedId === suggestion.id ? null : suggestion.id)}
              className={`p-3 rounded-lg border-2 cursor-pointer transition-all ${
                selectedId === suggestion.id
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <div className="flex items-start justify-between mb-2">
                <div className="flex-1 min-w-0">
                  <h4 className="font-medium text-sm text-gray-900">{suggestion.name}</h4>
                  <p className="text-xs text-gray-600 mt-1 line-clamp-2">
                    {suggestion.description}
                  </p>
                </div>
                <div className={`text-sm font-semibold ml-2 whitespace-nowrap ${getConfidenceColor(suggestion.confidence)}`}>
                  {(suggestion.confidence * 100).toFixed(0)}%
                </div>
              </div>

              {/* Details */}
              {selectedId === suggestion.id && (
                <div className="mt-3 pt-3 border-t border-gray-200">
                  <div className="text-xs text-gray-600 mb-3">
                    <p>
                      <strong>Nodes:</strong> {suggestion.workflow.nodes.length}
                    </p>
                    <p>
                      <strong>Edges:</strong> {suggestion.workflow.edges.length}
                    </p>
                  </div>

                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleApply(suggestion);
                    }}
                    className="w-full px-3 py-2 text-sm font-medium text-white bg-blue-500 hover:bg-blue-600 rounded"
                  >
                    Use This Workflow
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default AISuggestionPanel;
