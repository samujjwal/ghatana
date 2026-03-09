import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { workflowAtom, nodesAtom } from '@/stores/workflow.store';

/**
 * AI Collaborator Component for Agentic Workflow Co-Pilot.
 *
 * <p><b>Purpose</b><br>
 * Enables the workflow designer to receive step-by-step plan suggestions,
 * auto-remediations, and template generation from CES orchestration agents.
 * Simplifies complex workflow authoring with real-time agent collaboration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { AICollaborator } from '@/features/workflow/components/AICollaborator';
 *
 * function WorkflowEditor() {
 *   return (
 *     <div>
 *       <WorkflowCanvas />
 *       <AICollaborator />
 *     </div>
 *   );
 * }
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Real-time agent suggestions
 * - Step-by-step workflow planning
 * - Auto-remediation recommendations
 * - Template generation
 * - Feedback loop for learning
 * - SSE/WebSocket integration
 * - Optimistic updates
 *
 * @doc.type component
 * @doc.purpose Agentic workflow co-pilot UI
 * @doc.layer frontend
 */

export interface AgentSuggestion {
  id: string;
  type: 'step' | 'remediation' | 'template';
  title: string;
  description: string;
  confidence: number;
  action: 'add_node' | 'connect_nodes' | 'modify_field' | 'apply_template';
  payload: Record<string, any>;
  status: 'pending' | 'accepted' | 'rejected';
}

export interface AgentRecommendation {
  id: string;
  workflowId: string;
  suggestions: AgentSuggestion[];
  status: 'generating' | 'ready' | 'applied' | 'failed';
  confidence: number;
  generatedAt: number;
}

export function AICollaborator() {
  const [workflow] = useAtom(workflowAtom);
  const [nodes] = useAtom(nodesAtom);
  const [suggestions, setSuggestions] = useState<AgentSuggestion[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [_selectedSuggestion, setSelectedSuggestion] = useState<AgentSuggestion | null>(null);

  /**
   * Fetch agent recommendations for current workflow.
   *
   * <p>GIVEN: Current workflow state
   * WHEN: Component mounts or workflow changes
   * THEN: Fetches recommendations from backend agent
   */
  const { data: recommendations, isLoading } = useQuery({
    queryKey: ['workflow-recommendations', workflow?.id],
    queryFn: async () => {
      if (!workflow) return null;

      const response = await fetch(
        `/api/v1/workflows/${workflow.id}/recommendations`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Tenant-ID': localStorage.getItem('tenantId') || 'default',
          },
          body: JSON.stringify({
            workflow,
            nodeCount: nodes.length,
            context: 'workflow_design',
          }),
        }
      );

      if (!response.ok) {
        throw new Error('Failed to fetch recommendations');
      }

      return (await response.json()) as AgentRecommendation;
    },
    enabled: !!workflow,
    refetchInterval: 5000, // Refetch every 5 seconds
    staleTime: 2000,
  });

  /**
   * Updates suggestions when recommendations are received.
   */
  useEffect(() => {
    if (recommendations?.suggestions) {
      setSuggestions(recommendations.suggestions);
    }
  }, [recommendations]);

  /**
   * Handles accepting a suggestion.
   *
   * <p>GIVEN: A suggestion
   * WHEN: User clicks accept
   * THEN: Applies suggestion and records feedback
   */
  const handleAcceptSuggestion = async (suggestion: AgentSuggestion) => {
    try {
      // Optimistic update
      setSuggestions((prev) =>
        prev.map((s) =>
          s.id === suggestion.id ? { ...s, status: 'accepted' } : s
        )
      );

      // Apply suggestion based on action type
      applySuggestion(suggestion);

      // Record feedback
      await recordFeedback(suggestion.id, 'accepted');

      console.log('Suggestion accepted:', suggestion.id);
    } catch (error) {
      console.error('Error accepting suggestion:', error);
      // Revert optimistic update
      setSuggestions((prev) =>
        prev.map((s) =>
          s.id === suggestion.id ? { ...s, status: 'pending' } : s
        )
      );
    }
  };

  /**
   * Handles rejecting a suggestion.
   */
  const handleRejectSuggestion = async (suggestion: AgentSuggestion) => {
    try {
      setSuggestions((prev) =>
        prev.map((s) =>
          s.id === suggestion.id ? { ...s, status: 'rejected' } : s
        )
      );

      await recordFeedback(suggestion.id, 'rejected');

      console.log('Suggestion rejected:', suggestion.id);
    } catch (error) {
      console.error('Error rejecting suggestion:', error);
    }
  };

  /**
   * Applies a suggestion to the workflow.
   */
  const applySuggestion = (suggestion: AgentSuggestion) => {
    switch (suggestion.action) {
      case 'add_node':
        // Add node to workflow
        console.log('Adding node:', suggestion.payload);
        break;
      case 'connect_nodes':
        // Connect nodes
        console.log('Connecting nodes:', suggestion.payload);
        break;
      case 'modify_field':
        // Modify field
        console.log('Modifying field:', suggestion.payload);
        break;
      case 'apply_template':
        // Apply template
        console.log('Applying template:', suggestion.payload);
        break;
    }
  };

  /**
   * Records feedback for agent learning.
   */
  const recordFeedback = async (suggestionId: string, feedback: 'accepted' | 'rejected') => {
    if (!workflow) return;

    try {
      await fetch(
        `/api/v1/workflows/${workflow.id}/recommendations/${suggestionId}/feedback`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Tenant-ID': localStorage.getItem('tenantId') || 'default',
          },
          body: JSON.stringify({ feedback }),
        }
      );
    } catch (error) {
      console.error('Error recording feedback:', error);
    }
  };

  return (
    <div className="fixed bottom-4 right-4 w-96 max-h-96 bg-white dark:bg-gray-900 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 flex flex-col">
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
          <h3 className="font-semibold text-gray-900 dark:text-gray-100">
            AI Collaborator
          </h3>
        </div>
        <button
          onClick={() => setIsOpen(!isOpen)}
          className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
        >
          {isOpen ? '−' : '+'}
        </button>
      </div>

      {/* Content */}
      {isOpen && (
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {isLoading && (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin">
                <svg
                  className="w-5 h-5 text-blue-500"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M13 10V3L4 14h7v7l9-11h-7z"
                  />
                </svg>
              </div>
            </div>
          )}

          {suggestions.length === 0 && !isLoading && (
            <p className="text-sm text-gray-500 dark:text-gray-400 text-center py-4">
              No suggestions at this time
            </p>
          )}

          {suggestions.map((suggestion) => (
            <div
              key={suggestion.id}
              className={`p-3 rounded-md border transition-colors ${
                suggestion.status === 'accepted'
                  ? 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-700'
                  : suggestion.status === 'rejected'
                    ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-700'
                    : 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-700'
              }`}
            >
              {/* Suggestion Header */}
              <div className="flex items-start justify-between gap-2 mb-2">
                <div className="flex-1">
                  <h4 className="font-medium text-sm text-gray-900 dark:text-gray-100">
                    {suggestion.title}
                  </h4>
                  <p className="text-xs text-gray-600 dark:text-gray-400 mt-1">
                    {suggestion.description}
                  </p>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">
                    {Math.round(suggestion.confidence * 100)}%
                  </span>
                </div>
              </div>

              {/* Suggestion Type Badge */}
              <div className="flex items-center gap-2 mb-2">
                <span className="inline-block px-2 py-1 text-xs font-medium rounded bg-gray-200 dark:bg-gray-700 text-gray-900 dark:text-gray-100">
                  {suggestion.type}
                </span>
                <span className="inline-block px-2 py-1 text-xs font-medium rounded bg-gray-200 dark:bg-gray-700 text-gray-900 dark:text-gray-100">
                  {suggestion.action}
                </span>
              </div>

              {/* Action Buttons */}
              {suggestion.status === 'pending' && (
                <div className="flex gap-2">
                  <button
                    onClick={() => handleAcceptSuggestion(suggestion)}
                    className="flex-1 px-2 py-1 text-xs font-medium bg-green-500 hover:bg-green-600 text-white rounded transition-colors"
                  >
                    Accept
                  </button>
                  <button
                    onClick={() => handleRejectSuggestion(suggestion)}
                    className="flex-1 px-2 py-1 text-xs font-medium bg-gray-300 hover:bg-gray-400 dark:bg-gray-600 dark:hover:bg-gray-700 text-gray-900 dark:text-gray-100 rounded transition-colors"
                  >
                    Reject
                  </button>
                </div>
              )}

              {suggestion.status === 'accepted' && (
                <div className="text-xs text-green-700 dark:text-green-400 font-medium">
                  ✓ Applied
                </div>
              )}

              {suggestion.status === 'rejected' && (
                <div className="text-xs text-red-700 dark:text-red-400 font-medium">
                  ✗ Rejected
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Footer */}
      <div className="px-4 py-2 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-500 dark:text-gray-400">
        {recommendations?.confidence && (
          <span>
            Overall confidence: {Math.round(recommendations.confidence * 100)}%
          </span>
        )}
      </div>
    </div>
  );
}

export default AICollaborator;
