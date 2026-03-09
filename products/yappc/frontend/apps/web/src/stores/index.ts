/**
 * Consolidated Jotai Atom Tree for YAPPC
 * 
 * Single source of truth for all application state.
 * Organized into logical domains for better maintainability.
 * 
 * @doc.type module
 * @doc.purpose State management
 * @doc.layer infrastructure
 * @doc.pattern Atom Tree
 */

// Re-export all atoms from domain stores
export * from './user.store';
export * from './workflow.store';
export * from './ui.store';
export * from './canvas.store';

/**
 * Atom Tree Structure:
 * 
 * ├── User Domain
 * │   ├── currentUserAtom
 * │   ├── userPreferencesAtom
 * │   └── userSessionAtom
 * │
 * ├── Workflow Domain
 * │   ├── workflowsAtom
 * │   ├── activeWorkflowAtom
 * │   └── workflowExecutionAtom
 * │
 * ├── UI Domain
 * │   ├── sidebarOpenAtom
 * │   ├── themeAtom
 * │   ├── loadingStatesAtom
 * │   └── errorStatesAtom
 * │
 * └── Canvas Domain
 *     ├── canvasNodesAtom
 *     ├── canvasEdgesAtom
 *     ├── canvasViewportAtom
 *     └── canvasSelectionAtom
 */
