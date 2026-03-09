/**
 * useActionState Hook
 * 
 * Builds current action state from context and route information.
 * Used by ActionRegistry to filter available actions.
 * 
 * @doc.type hook
 * @doc.purpose Build action state from current context
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useMemo } from 'react';
import { useLocation } from 'react-router';
import { usePhaseContext, useSelectionContext } from '../context/WorkflowContextProvider';
import { ActionState } from '../services/ActionRegistry';

/**
 * Build current action state
 */
export function useActionState(): ActionState {
    const location = useLocation();
    const { currentPhase } = usePhaseContext();
    const { elements, type: selectionType } = useSelectionContext();

    return useMemo<ActionState>(() => ({
        currentPhase: currentPhase || null,
        currentRoute: location.pathname,
        projectId: location.pathname.includes('/p/')
            ? location.pathname.split('/p/')[1]?.split('/')[0] || null
            : null,
        hasSelection: (elements || []).length > 0,
        selectionType: selectionType || null,
        selectionCount: (elements || []).length,
        isCanvasActive: location.pathname.includes('/canvas'),
        canUndo: false, // NOTE: Connect to canvas undo state
        canRedo: false, // NOTE: Connect to canvas redo state
        isDirty: false, // NOTE: Connect to canvas dirty state
    }), [location.pathname, currentPhase, elements, selectionType]);
}
