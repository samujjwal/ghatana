/**
 * Hooks Module Index
 * 
 * Re-exports all custom hooks for data-cloud UI.
 * 
 * @doc.type module
 * @doc.purpose Hooks module exports
 * @doc.layer frontend
 */

// Collection hooks
export * from './useCollections';
export * from './useCollectionData';

// Workflow hooks
export * from './useWorkflows';

// Utility hooks
export * from './useUndoRedo';

// Command Bar hook
export * from './useCommandBar';

// Ambient Intelligence hook
export * from './useAmbientIntelligence';

// Real-time streaming hooks
export {
    useEventCloudStream,
    useStreamMetrics,
    type UseEventCloudStreamOptions,
    type UseEventCloudStreamReturn,
    type TopologyUpdateEvent,
    type MetricsUpdateEvent,
} from './useEventCloudStream';

// Re-export core diagram hooks for convenience
export { useTopology, useHistory } from '@ghatana/canvas/topology';

// Re-export realtime hooks
export {
    useWebSocket,
    useActiveJStream,
    useActiveJSubscription,
} from '@ghatana/realtime';

