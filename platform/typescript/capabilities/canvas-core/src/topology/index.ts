/**
 * @ghatana/canvas/topology — Topology visualization module
 *
 * Consolidated topology/diagram functionality. All types, components,
 * hooks, and utilities for topology visualizations live here.
 *
 * Previously split across @ghatana/diagram (now removed) and this sub-path.
 *
 * @doc.type library
 * @doc.purpose Topology visualization sub-path for canvas
 * @doc.layer shared
 */

// ============================================
// DIAGRAM BUILDER (formerly @ghatana/diagram root)
// ============================================

// Builder types
export type {
  PaletteItem,
  PropertyField,
  NodeConfig as DiagramNodeConfig,
  DiagramSnapshot,
  ValidationError as DiagramValidationError,
  DiagramBuilderProps,
  HistoryManager,
  Node,
  Edge,
  Connection,
  NodeTypes,
  EdgeTypes,
} from './builder/types';

// Builder hooks
export { useHistory } from './builder/useHistory';

// Builder validation utilities
export {
  validateDiagram,
  validatePattern,
  validatePipeline,
} from './builder/validation';

// ============================================
// TOPOLOGY VISUALIZATION
// ============================================

// Topology types
export * from './types';

// Topology components
export {
  BaseTopologyNode,
  MetricsDisplay,
  StatusIndicator,
  STATUS_COLORS,
  STATUS_LABELS,
  formatMetricValue,
} from './BaseTopologyNode';
export type { BaseTopologyNodeProps } from './BaseTopologyNode';

export {
  BaseTopologyEdge,
  AnimatedEdgePath,
  EdgeLabel,
  EDGE_STATUS_COLORS,
} from './BaseTopologyEdge';
export type { BaseTopologyEdgeProps, EdgePathType } from './BaseTopologyEdge';

// Layout utilities
export {
  calculateDagreLayout,
  calculateForceLayout,
  autoLayout,
  centerNodes,
  fitNodesToSize,
} from './layout';

// Topology hook
export { useTopology } from './useTopology';
export type { UseTopologyOptions, UseTopologyReturn } from './useTopology';

// Accessibility utilities (WCAG AA)
export {
  getNodeAriaLabel,
  getEdgeAriaLabel,
  getUpdateAnnouncement,
  useTopologyKeyboardNav,
  useTopologyFocus,
  useFocusTrap,
  getTopologySummary,
  checkContrastRatio,
  accessibleStatusColors,
  ScreenReaderAnnouncer,
} from './accessibility';
export type { KeyboardNavigationOptions } from './accessibility';

// Accessibility Provider
export {
  TopologyA11yProvider,
  useTopologyA11y,
  AccessibleNodeWrapper,
  TopologySkipLink,
  getHighContrastClasses,
} from './TopologyA11yProvider';
export type {
  TopologyA11yProviderProps,
  AccessibleNodeWrapperProps,
  SkipLinkProps,
} from './TopologyA11yProvider';

// Performance utilities
export {
  // Virtualization
  virtualizeNodes,
  virtualizeEdges,
  useVirtualization,
  // Throttling
  throttle,
  useThrottledState,
  useBatchedUpdates,
  // Memoization
  nodesEqual,
  edgesEqual,
  useMemoizedNodes,
  useMemoizedEdges,
  // Render optimization
  useStableCallback,
  withNodeMemo,
  useRenderMetrics,
  useRAFUpdate,
  usePerformanceStats,
} from './performance';
export type {
  VirtualizationOptions,
  VirtualizationResult,
  ThrottleOptions,
  PerformanceStats,
} from './performance';
