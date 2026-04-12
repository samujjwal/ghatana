/**
 * Action Registry Initializer
 * 
 * Initializes the global action registry with all layer, phase, and role actions.
 * Provides a single entry point for action system setup.
 * 
 * @doc.type core
 * @doc.purpose Action system initialization
 * @doc.layer core
 */

import type { ActionDefinition } from '../core/action-registry';
import { ActionRegistry, getActionRegistry } from '../core/action-registry';
import {
    getAllLayerActions,
    UNIVERSAL_LAYER_ACTIONS,
} from './layer-actions';

export interface ActionRegistryOptions {
    /**
     * Product-supplied phase actions keyed by phase name.
     * Platform canvas does not bundle phase actions — they are product-specific.
     * Pass these from the product entrypoint (e.g. YAPPC's phase-actions.ts).
     */
    phaseActions?: Record<string, ActionDefinition[]>;
    /**
     * Product-supplied role actions keyed by role name.
     * Platform canvas does not bundle role actions — they are product-specific.
     * Pass these from the product entrypoint (e.g. YAPPC's role-actions.ts).
     */
    roleActions?: Record<string, ActionDefinition[]>;
}

/**
 * Initialize the global action registry with all actions.
 *
 * Phase and role actions are intentionally NOT included by default — they are
 * product-specific and must be supplied via `options.phaseActions` /
 * `options.roleActions`.
 */
export function initializeActionRegistry(options: ActionRegistryOptions = {}): ActionRegistry {
    const registry = getActionRegistry();

    // Clear existing actions
    registry.clear();

    // Register layer-specific actions
    const layerActions = getAllLayerActions();
    Object.entries(layerActions).forEach(([layer, actions]) => {
        registry.registerLayerActions(layer, actions);
    });

    // Register product-supplied phase-specific actions (if any)
    if (options.phaseActions) {
        Object.entries(options.phaseActions).forEach(([phase, actions]) => {
            registry.registerPhaseActions(phase, actions);
        });
    }

    // Register product-supplied role-specific actions (if any)
    if (options.roleActions) {
        Object.entries(options.roleActions).forEach(([role, actions]) => {
            registry.registerRoleActions(role, actions);
        });
    }

    // Register universal actions
    registry.registerMany(UNIVERSAL_LAYER_ACTIONS);

    return registry;
}

/**
 * Get action registry stats for debugging
 */
export function getActionRegistryStats() {
    const registry = getActionRegistry();
    return registry.getStats();
}

/**
 * Verify action registry is properly initialized
 */
export function verifyActionRegistry(): boolean {
    const registry = getActionRegistry();
    const stats = registry.getStats();

    const hasLayerActions = Object.keys(stats.byLayer).length > 0;
    const hasTotalActions = stats.total > 0;

    const isValid = hasLayerActions && hasTotalActions;

    if (!isValid) {
        console.warn('⚠️ Action registry not properly initialized:', stats);
    }

    return isValid;
}
