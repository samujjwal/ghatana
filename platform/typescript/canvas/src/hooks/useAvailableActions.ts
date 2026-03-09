/**
 * Available Actions Hook
 * 
 * React hook for getting context-aware actions based on current canvas state.
 * Integrates layer, phase, and role information to provide relevant actions.
 * 
 * @doc.type hook
 * @doc.purpose Context-aware action resolution
 * @doc.layer presentation
 */

import { useMemo, useCallback, useEffect } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import {
    chromeSemanticLayerAtom,
    chromeCurrentPhaseAtom,
    chromeActiveRolesAtom,
    chromeAvailableActionsAtom,
    Action,
} from '../chrome';
import { getActionRegistry, ActionContext } from '../core/action-registry';
import { initializeActionRegistry } from '../actions/action-initializer';

/**
 * Hook for getting available actions based on current context
 */
export function useAvailableActions() {
    const layer = useAtomValue(chromeSemanticLayerAtom);
    const phase = useAtomValue(chromeCurrentPhaseAtom);
    const roles = useAtomValue(chromeActiveRolesAtom);
    const setAvailableActions = useSetAtom(chromeAvailableActionsAtom);

    // Initialize registry on first use
    useEffect(() => {
        initializeActionRegistry();
    }, []);

    // Compute available actions based on context
    const actions = useMemo(() => {
        const registry = getActionRegistry();
        const context: ActionContext = {
            layer,
            phase,
            roles,
            selection: 'none',
        };

        const contextActions = registry.getActionsForContext(context);

        // Convert to chrome Action format
        const chromeActions: Action[] = contextActions.map((action) => ({
            id: action.id,
            label: action.label,
            icon: action.icon,
            shortcut: action.shortcut,
            category: action.category,
            handler: () => action.handler(context),
        }));

        return chromeActions;
    }, [layer, phase, roles]);

    // Update atom whenever actions change
    useEffect(() => {
        setAvailableActions(actions);
    }, [actions, setAvailableActions]);

    return actions;
}

/**
 * Hook for executing actions by ID
 */
export function useActionExecutor() {
    const layer = useAtomValue(chromeSemanticLayerAtom);
    const phase = useAtomValue(chromeCurrentPhaseAtom);
    const roles = useAtomValue(chromeActiveRolesAtom);

    const executeAction = useCallback(
        async (actionId: string, selection?: 'none' | 'single' | 'multiple') => {
            const registry = getActionRegistry();
            const context: ActionContext = {
                layer,
                phase,
                roles,
                selection: selection || 'none',
            };

            try {
                await registry.executeAction(actionId, context);
                console.log(`✅ Action executed: ${actionId}`);
            } catch (error) {
                console.error(`❌ Action execution failed: ${actionId}`, error);
                throw error;
            }
        },
        [layer, phase, roles]
    );

    return { executeAction };
}

/**
 * Hook for searching actions
 */
export function useActionSearch() {
    const layer = useAtomValue(chromeSemanticLayerAtom);
    const phase = useAtomValue(chromeCurrentPhaseAtom);
    const roles = useAtomValue(chromeActiveRolesAtom);

    const searchActions = useCallback(
        (query: string) => {
            const registry = getActionRegistry();
            const context: ActionContext = {
                layer,
                phase,
                roles,
                selection: 'none',
            };

            const results = registry.searchActions(query, context);

            // Convert to chrome Action format
            return results.map((action) => ({
                id: action.id,
                label: action.label,
                icon: action.icon,
                shortcut: action.shortcut,
                category: action.category,
                handler: () => action.handler(context),
            }));
        },
        [layer, phase, roles]
    );

    return { searchActions };
}

/**
 * Hook for getting actions by category
 */
export function useActionsByCategory() {
    const layer = useAtomValue(chromeSemanticLayerAtom);
    const phase = useAtomValue(chromeCurrentPhaseAtom);
    const roles = useAtomValue(chromeActiveRolesAtom);

    const actionsByCategory = useMemo(() => {
        const registry = getActionRegistry();
        const context: ActionContext = {
            layer,
            phase,
            roles,
            selection: 'none',
        };

        return registry.getActionsByCategory(context);
    }, [layer, phase, roles]);

    return actionsByCategory;
}

/**
 * Hook for getting action by keyboard shortcut
 */
export function useActionShortcut() {
    const layer = useAtomValue(chromeSemanticLayerAtom);
    const phase = useAtomValue(chromeCurrentPhaseAtom);
    const roles = useAtomValue(chromeActiveRolesAtom);

    const handleShortcut = useCallback(
        async (shortcut: string) => {
            const registry = getActionRegistry();
            const action = registry.getActionByShortcut(shortcut);

            if (action) {
                const context: ActionContext = {
                    layer,
                    phase,
                    roles,
                    selection: 'none',
                };

                try {
                    await action.handler(context);
                    console.log(`✅ Shortcut executed: ${shortcut} → ${action.label}`);
                } catch (error) {
                    console.error(`❌ Shortcut execution failed: ${shortcut}`, error);
                }
            }
        },
        [layer, phase, roles]
    );

    return { handleShortcut };
}
