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

import { ActionRegistry, getActionRegistry } from '../core/action-registry';
import {
    getAllLayerActions,
    UNIVERSAL_LAYER_ACTIONS,
} from './layer-actions';
import { getAllPhaseActions } from './phase-actions';
import { getAllRoleActions } from './role-actions';

/**
 * Initialize the global action registry with all actions
 */
export function initializeActionRegistry(): ActionRegistry {
    const registry = getActionRegistry();

    // Clear existing actions
    registry.clear();

    // Register layer-specific actions
    const layerActions = getAllLayerActions();
    Object.entries(layerActions).forEach(([layer, actions]) => {
        registry.registerLayerActions(layer, actions);
    });

    // Register phase-specific actions
    const phaseActions = getAllPhaseActions();
    Object.entries(phaseActions).forEach(([phase, actions]) => {
        registry.registerPhaseActions(phase, actions);
    });

    // Register role-specific actions
    const roleActions = getAllRoleActions();
    Object.entries(roleActions).forEach(([role, actions]) => {
        registry.registerRoleActions(role, actions);
    });

    // Register universal actions
    registry.registerMany(UNIVERSAL_LAYER_ACTIONS);

    console.log('✅ Action registry initialized:', registry.getStats());

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
    const hasPhaseActions = Object.keys(stats.byPhase).length > 0;
    const hasRoleActions = Object.keys(stats.byRole).length > 0;
    const hasTotalActions = stats.total > 0;

    const isValid = hasLayerActions && hasPhaseActions && hasRoleActions && hasTotalActions;

    if (!isValid) {
        console.warn('⚠️ Action registry not properly initialized:', stats);
    }

    return isValid;
}
