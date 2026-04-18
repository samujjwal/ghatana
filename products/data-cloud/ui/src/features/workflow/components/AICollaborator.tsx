import React, { useState } from 'react';
import { useAtom } from 'jotai';
import { workflowAtom, nodesAtom } from '@/stores/workflow.store';
import {
  AI_COLLABORATOR_BOUNDARY_MESSAGE,
  AI_COLLABORATOR_BOUNDARY_TITLE,
  AI_COLLABORATOR_CONTEXT_HINT,
  AI_COLLABORATOR_FOOTER_NOTE,
} from '@/lib/runtime-boundaries';

/**
 * AI Collaborator Component for Agentic Workflow Co-Pilot.
 *
 * <p><b>Purpose</b><br>
 * Enables the workflow designer to receive step-by-step plan suggestions,
 * auto-remediations, and template generation from Data Cloud orchestration agents.
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
  payload: Record<string, unknown>;
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

export { AI_COLLABORATOR_BOUNDARY_MESSAGE } from '@/lib/runtime-boundaries';

export function AICollaborator(): React.ReactElement {
  const [workflow] = useAtom(workflowAtom);
  const [nodes] = useAtom(nodesAtom);
  const [isOpen, setIsOpen] = useState(false);
  const hasWorkflowContext = workflow !== null || nodes.length > 0;

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
          <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-3 text-sm text-amber-900">
            <p className="font-medium">{AI_COLLABORATOR_BOUNDARY_TITLE}</p>
            <p className="mt-1">{AI_COLLABORATOR_BOUNDARY_MESSAGE}</p>
            {!hasWorkflowContext && (
              <p className="mt-2 text-xs text-amber-800">
                {AI_COLLABORATOR_CONTEXT_HINT}
              </p>
            )}
          </div>
        </div>
      )}

      {/* Footer */}
      <div className="px-4 py-2 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-500 dark:text-gray-400">
        {AI_COLLABORATOR_FOOTER_NOTE}
      </div>
    </div>
  );
}

export default AICollaborator;
