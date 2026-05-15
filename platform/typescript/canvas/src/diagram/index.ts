/**
 * @fileoverview Diagram primitives barrel export.
 *
 * @doc.type module
 * @doc.purpose Product-neutral diagram primitives public API
 * @doc.layer platform
 */

export type {
  DiagramType,
  Diagram,
  DiagramNode,
  DiagramEdge,
  LayoutConfig,
  LayoutValidationResult,
  DiagramValidationResult,
  DiagramValidationError,
  DiagramValidationWarning,
  NodeShape,
  NodeStyle,
  EdgeRouting,
  ArrowHead,
  EdgeStyle,
  Swimlane,
  SwimlanePhase,
  // Flow
  FlowNode,
  FlowNodeType,
  FlowEdge,
  // DAG
  DagNode,
  DagEdge,
  // Topology
  TopologyNode,
  TopologyNodeType,
  TopologyEdge,
  TopologyEdgeType,
  // Swimlane
  SwimlaneNode,
  // Dependency
  DependencyNode,
  DependencyEdge,
  DependencyType,
  // Provenance
  ProvenanceNode,
  ProvenanceNodeType,
  ProvenanceEdge,
  ProvenanceEdgeType,
} from './types.js';

export {
  isValidDiagramType,
  getValidDiagramTypes,
  validateDiagram,
  validateLayoutConfig,
  createDiagram,
  DiagramBuilder,
} from './types.js';
