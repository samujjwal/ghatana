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
  type UseEventLogStreamOptions,
  type UseEventLogStreamReturn,
  type TopologyUpdateEvent,
  type MetricsUpdateEvent,
} from "./useEventLogStream";

// Re-export core diagram hooks for convenience
export { useTopology, useHistory } from "@ghatana/canvas/topology";

// Re-export realtime hooks
export {
  useWebSocket,
  useActiveJStream,
  useActiveJSubscription,
} from "@ghatana/realtime";

// Route state and async state hooks
export * from "./useRouteEntryState";
export * from "./useAsyncState";

// Operation history hook (DC-UX-044)
export * from "./useOperationHistory";
