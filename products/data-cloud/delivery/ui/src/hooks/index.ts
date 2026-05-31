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
export * from "./useCollections";

// Workflow hooks
export * from "./useWorkflows";

// Utility hooks
export * from "./useUndoRedo";

// Command Bar hook
export * from "./useCommandBar";

// Ambient Intelligence hook
export * from "./useAmbientIntelligence";

// Real-time streaming hooks
export {
  useEventLogStream,
  useStreamMetrics,
  type MetricsUpdateEvent,
  type TopologyUpdateEvent,
  type UseEventLogStreamOptions,
  type UseEventLogStreamReturn,
} from "./useEventLogStream";

// Re-export core diagram hooks for convenience
export { useHistory, useTopology } from "@ghatana/canvas/topology";

// Re-export realtime hooks
export {
  useActiveJStream,
  useActiveJSubscription,
  useWebSocket,
} from "@ghatana/realtime";

// Route state and async state hooks
export * from "./useAsyncState";
export * from "./useRouteEntryState";

// Operation history hook (DC-UX-044)
export * from "./useOperationHistory";
