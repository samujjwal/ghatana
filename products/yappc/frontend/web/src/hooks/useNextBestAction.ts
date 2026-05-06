/**
 * useNextBestAction Hook
 *
 * Derives the ranked next-best actions for the current workflow context and
 * registers the top action into the ActionRegistry so it appears consistently
 * in both the dashboard NextActionDashboard and the global CommandPalette.
 *
 * Design contract:
 * - The same top action (same title) must appear on the dashboard card AND
 *   as the first entry in the command palette when the user presses Cmd+K.
 * - The hook reads ranked actions from WorkflowContext.guidance.nextActions,
 *   which is already computed by rankNextActions() in WorkflowContextProvider.
 * - A dynamic action entry (id: 'next-best-action-top') is registered into
 *   ActionRegistry and kept current via useEffect with cleanup on unmount.
 *
 * @doc.type hook
 * @doc.purpose Consistent next-best-action surface across dashboard and command palette
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router';
import ActionRegistry from '../services/ActionRegistry';
import type { ActionDefinition } from '../services/ActionRegistry';
import { useGuidanceContext, useWorkflowContext } from '../context/WorkflowContextProvider';
import type { NextAction } from '../components/dashboard/NextActionDashboard';

// ============================================================================
// Constants
// ============================================================================

const TOP_ACTION_ID = 'next-best-action-top';

// ============================================================================
// Types
// ============================================================================

export interface UseNextBestActionResult {
  /** Top-ranked action ready for NextActionDashboard */
  primaryAction: NextAction | null;
  /** Second-ranked action (optional secondary card) */
  secondaryAction: NextAction | null;
  /** All ranked action titles, ordered by priority */
  rankedTitles: readonly string[];
  /** Whether actions are available for the current context */
  hasActions: boolean;
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Returns the next-best actions for the current workflow context and keeps
 * the ActionRegistry in sync so the command palette always shows the top action.
 */
export function useNextBestAction(): UseNextBestActionResult {
  const { nextActions } = useGuidanceContext();
  const { project } = useWorkflowContext();
  const projectId = project.id;
  const phase = project.phase;
  const navigate = useNavigate();

  // Build navigation target for the top action
  const buildActionTarget = useCallback(
    (title: string): (() => void) => {
      const lower = title.toLowerCase();

      if (!projectId) {
        return () => { navigate('/'); };
      }

      // Route to the right panel based on title semantics
      if (lower.includes('save') || lower.includes('synchronize')) {
        return () => {
          // Dispatch a save event the canvas can intercept
          window.dispatchEvent(new CustomEvent('yappc:save-requested'));
        };
      }

      if (lower.includes('generat') || lower.includes('code')) {
        return () => { navigate(`/p/${projectId}/canvas`); };
      }

      if (lower.includes('deploy') || lower.includes('run')) {
        return () => { navigate(`/p/${projectId}/deploy`); };
      }

      if (lower.includes('preview') || lower.includes('observe')) {
        return () => { navigate(`/p/${projectId}/preview`); };
      }

      if (lower.includes('validate') || lower.includes('check') || lower.includes('review')) {
        return () => { navigate(`/p/${projectId}/canvas`); };
      }

      // Default: navigate to project canvas
      return () => { navigate(`/p/${projectId}/canvas`); };
    },
    [projectId, navigate]
  );

  // Convert ranked string titles to structured NextAction objects
  const structuredActions = useMemo<readonly NextAction[]>(() => {
    if (!nextActions || nextActions.length === 0) {
      return [];
    }

    return nextActions.slice(0, 3).map((title, index): NextAction => ({
      id: `next-best-action-${index}`,
      title,
      description: buildActionDescription(title, phase ?? undefined),
      priority: index === 0 ? 'primary' : index === 1 ? 'secondary' : 'tertiary',
      action: buildActionTarget(title),
    }));
  }, [nextActions, phase, buildActionTarget]);

  // Keep ActionRegistry in sync — register/update the top action so it
  // always appears first in the command palette under the 'ai' category.
  useEffect(() => {
    if (structuredActions.length === 0) {
      ActionRegistry.unregister(TOP_ACTION_ID);
      return;
    }

    const top = structuredActions[0];
    const registryEntry: ActionDefinition = {
      id: TOP_ACTION_ID,
      label: top.title,
      description: top.description,
      icon: 'TrendingUp',
      category: 'ai',
      priority: 9999, // Always sort first in its category
      context: {
        // Available in all phases and routes
      },
      handler: () => {
        top.action();
      },
    };

    ActionRegistry.register(registryEntry);

    return () => {
      ActionRegistry.unregister(TOP_ACTION_ID);
    };
  }, [structuredActions]);

  return useMemo<UseNextBestActionResult>(() => ({
    primaryAction: structuredActions[0] ?? null,
    secondaryAction: structuredActions[1] ?? null,
    rankedTitles: nextActions ?? [],
    hasActions: structuredActions.length > 0,
  }), [structuredActions, nextActions]);
}

// ============================================================================
// Helpers
// ============================================================================

function buildActionDescription(title: string, phase: string | undefined): string {
  const lower = title.toLowerCase();

  if (lower.includes('save') || lower.includes('synchronize')) {
    return 'Persist unsaved changes to the artifact store.';
  }
  if (lower.includes('stabili') || lower.includes('signal')) {
    return 'Resolve failing health signals before advancing the phase.';
  }
  if (phase) {
    return `Recommended next step for the ${phase} phase.`;
  }
  return 'AI-recommended next step for your workflow.';
}
