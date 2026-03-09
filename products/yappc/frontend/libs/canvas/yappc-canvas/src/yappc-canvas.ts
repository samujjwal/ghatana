/**
 * YAPPC Canvas Integration
 * 
 * Complete YAPPC canvas setup using the generic canvas system.
 * This module initializes the canvas with YAPPC-specific configuration.
 * 
 * @doc.type yappc
 * @doc.purpose YAPPC canvas initialization
 * @doc.layer application
 */

import { setCanvasConfig } from '../core/canvas-config';
import { createYAPPCConfig, YAPPCActionContext } from './yappc-config';
import {
    getYAPPCLayerActions,
    getYAPPCPhaseActions,
    getYAPPCRoleActions,
    getYAPPCUniversalActions,
} from './yappc-actions';
import { initializeActionRegistry } from '../actions/action-initializer';
import { connectActionHandlers } from '../actions/action-handlers-connector';

/**
 * Initialize YAPPC Canvas
 * 
 * Sets up the canvas with YAPPC-specific configuration and actions.
 * Call this once at application startup.
 */
export function initializeYAPPCCanvas(): void {
    console.log('🚀 Initializing YAPPC Canvas...');

    // Create YAPPC configuration
    const config = createYAPPCConfig({
        layerActions: getYAPPCLayerActions(),
        phaseActions: getYAPPCPhaseActions(),
        roleActions: getYAPPCRoleActions(),
        universalActions: getYAPPCUniversalActions(),
    });

    // Set canvas configuration
    setCanvasConfig(config);

    // Initialize action registry
    initializeActionRegistry();

    // Connect action handlers
    connectActionHandlers();

    console.log('✅ YAPPC Canvas initialized');
}

/**
 * YAPPC-specific exports
 */
export * from './yappc-config';
export * from './yappc-actions';
