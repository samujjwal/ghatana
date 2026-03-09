/**
 * Type definitions for diagram builder (canvas/topology)
 * 
 * @doc.type types
 * @doc.purpose Type definitions for diagram builder
 * @doc.layer shared
 */

import type { Node, Edge, NodeTypes, EdgeTypes } from '@xyflow/react';

/**
 * Palette item for drag-and-drop
 */
export interface PaletteItem {
  id: string;
  type: string;
  label: string;
  icon: string;
  category: string;
  description: string;
  defaultData?: Record<string, unknown>;
}

/**
 * Property field configuration for node/edge properties
 */
export interface PropertyField {
  key: string;
  label: string;
  type: 'text' | 'number' | 'select' | 'boolean' | 'textarea';
  options?: { value: string; label: string }[];
  required?: boolean;
  placeholder?: string;
  helpText?: string;
}

/**
 * Node configuration for a specific node type
 */
export interface NodeConfig {
  type: string;
  label: string;
  icon: string;
  color: string;
  properties: PropertyField[];
}

/**
 * Diagram state snapshot for history
 */
export interface DiagramSnapshot {
  nodes: Node[];
  edges: Edge[];
  timestamp: number;
}

/**
 * Validation error
 */
export interface ValidationError {
  id: string;
  type: 'node' | 'edge' | 'diagram';
  message: string;
  severity: 'error' | 'warning';
}

/**
 * Diagram builder props
 */
export interface DiagramBuilderProps {
  /** Palette items for drag-and-drop */
  palette: PaletteItem[];
  /** Node type configurations */
  nodeConfigs: Record<string, NodeConfig>;
  /** Custom node components */
  nodeTypes?: NodeTypes;
  /** Custom edge components */
  edgeTypes?: EdgeTypes;
  /** Initial nodes */
  initialNodes?: Node[];
  /** Initial edges */
  initialEdges?: Edge[];
  /** Callback when diagram changes */
  onChange?: (nodes: Node[], edges: Edge[]) => void;
  /** Callback when node is selected */
  onNodeSelect?: (node: Node | null) => void;
  /** Callback when edge is selected */
  onEdgeSelect?: (edge: Edge | null) => void;
  /** Callback on save action */
  onSave?: (data: { nodes: Node[]; edges: Edge[] }) => void;
  /** Validation function */
  onValidate?: (nodes: Node[], edges: Edge[]) => ValidationError[];
  /** Title for the diagram */
  title?: string;
  /** Read-only mode */
  readOnly?: boolean;
  /** Show minimap */
  showMinimap?: boolean;
  /** Enable undo/redo */
  enableHistory?: boolean;
  /** Enable auto-save */
  enableAutoSave?: boolean;
  /** Auto-save delay in ms */
  autoSaveDelay?: number;
  /** className for container */
  className?: string;
}

/**
 * History manager for undo/redo
 */
export interface HistoryManager {
  past: DiagramSnapshot[];
  present: DiagramSnapshot | null;
  future: DiagramSnapshot[];
}

/**
 * Re-export ReactFlow types
 */
export type { Node, Edge, Connection, NodeTypes, EdgeTypes } from '@xyflow/react';
