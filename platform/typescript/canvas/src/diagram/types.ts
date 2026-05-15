/**
 * @fileoverview Product-neutral diagram primitives for canvas.
 *
 * Provides type definitions for flow, DAG, topology, swimlane, dependency graph,
 * and provenance graph diagrams. No product-specific semantics.
 *
 * @doc.type module
 * @doc.purpose Product-neutral diagram type definitions
 * @doc.layer platform
 */

// ============================================================================
// DIAGRAM TYPES
// ============================================================================

/** Supported diagram types. */
export type DiagramType =
  | "flow" // Flowchart / process diagram
  | "dag" // Directed Acyclic Graph
  | "topology" // Network topology
  | "swimlane" // Swimlane / cross-functional flowchart
  | "dependency-graph" // Dependency relationships
  | "provenance-graph"; // Data provenance / lineage

/** Validate diagram type. */
export function isValidDiagramType(type: string): type is DiagramType {
  return [
    "flow",
    "dag",
    "topology",
    "swimlane",
    "dependency-graph",
    "provenance-graph",
  ].includes(type);
}

/** Get all valid diagram types. */
export function getValidDiagramTypes(): readonly DiagramType[] {
  return [
    "flow",
    "dag",
    "topology",
    "swimlane",
    "dependency-graph",
    "provenance-graph",
  ] as const;
}

// ============================================================================
// NODE DEFINITIONS
// ============================================================================

/** Node shape variants. */
export type NodeShape =
  | "rectangle"
  | "rounded-rectangle"
  | "circle"
  | "ellipse"
  | "diamond"
  | "hexagon"
  | "parallelogram"
  | "document"
  | "database"
  | "cloud"
  | "custom";

/** Base node properties shared across all diagram types. */
export interface BaseNode {
  readonly id: string;
  readonly type: "node";
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly label?: string;
  readonly shape: NodeShape;
  readonly style?: NodeStyle;
  readonly metadata?: Record<string, unknown>;
}

/** Node styling options. */
export interface NodeStyle {
  readonly backgroundColor?: string;
  readonly borderColor?: string;
  readonly borderWidth?: number;
  readonly borderStyle?: "solid" | "dashed" | "dotted";
  readonly borderRadius?: number;
  readonly opacity?: number;
  readonly shadow?: boolean;
  readonly selected?: boolean;
  readonly hovered?: boolean;
  readonly focused?: boolean;
}

/** Flowchart node types. */
export type FlowNodeType =
  | "process"
  | "decision"
  | "terminator"
  | "input"
  | "output"
  | "document"
  | "predefined-process"
  | "connector"
  | "merge"
  | "data";

/** Flowchart node. */
export interface FlowNode extends BaseNode {
  readonly diagramType: "flow";
  readonly flowType: FlowNodeType;
  readonly incomingEdges: readonly string[];
  readonly outgoingEdges: readonly string[];
}

/** DAG node. */
export interface DagNode extends BaseNode {
  readonly diagramType: "dag";
  readonly depth: number;
  readonly parents: readonly string[];
  readonly children: readonly string[];
  readonly rank?: number;
}

/** Topology node types. */
export type TopologyNodeType =
  | "device"
  | "subnet"
  | "gateway"
  | "firewall"
  | "load-balancer"
  | "cloud"
  | "service"
  | "database";

/** Topology node. */
export interface TopologyNode extends BaseNode {
  readonly diagramType: "topology";
  readonly topologyType: TopologyNodeType;
  readonly connectedTo: readonly string[];
  readonly layer: number;
}

/** Swimlane node. */
export interface SwimlaneNode extends BaseNode {
  readonly diagramType: "swimlane";
  readonly lane: string;
  readonly phase?: string;
  readonly flowType: FlowNodeType;
}

/** Dependency relationship type. */
export type DependencyType =
  | "depends-on"
  | "required-by"
  | "uses"
  | "used-by"
  | "implements"
  | "implemented-by"
  | "extends"
  | "extended-by";

/** Dependency graph node. */
export interface DependencyNode extends BaseNode {
  readonly diagramType: "dependency-graph";
  readonly dependencies: readonly string[];
  readonly dependents: readonly string[];
  readonly dependencyType: DependencyType;
  readonly circular: boolean;
}

/** Provenance node types. */
export type ProvenanceNodeType =
  | "source"
  | "transformation"
  | "sink"
  | "checkpoint"
  | "query"
  | "result";

/** Provenance graph node. */
export interface ProvenanceNode extends BaseNode {
  readonly diagramType: "provenance-graph";
  readonly provenanceType: ProvenanceNodeType;
  readonly sources: readonly string[];
  readonly outputs: readonly string[];
  readonly timestamp?: string;
}

/** Union type for all diagram nodes. */
export type DiagramNode =
  | FlowNode
  | DagNode
  | TopologyNode
  | SwimlaneNode
  | DependencyNode
  | ProvenanceNode;

// ============================================================================
// EDGE DEFINITIONS
// ============================================================================

/** Edge routing styles. */
export type EdgeRouting =
  | "straight" // Direct line
  | "orthogonal" // Right-angle bends
  | "curved" // Bezier curve
  | "spline"; // Smooth spline

/** Arrow head types. */
export type ArrowHead =
  | "none"
  | "arrow"
  | "triangle"
  | "diamond"
  | "circle"
  | "vee"
  | "crow";

/** Base edge properties. */
export interface BaseEdge {
  readonly id: string;
  readonly type: "edge";
  readonly source: string;
  readonly target: string;
  readonly label?: string;
  readonly routing: EdgeRouting;
  readonly sourceArrow: ArrowHead;
  readonly targetArrow: ArrowHead;
  readonly style?: EdgeStyle;
  readonly metadata?: Record<string, unknown>;
}

/** Edge styling options. */
export interface EdgeStyle {
  readonly strokeColor?: string;
  readonly strokeWidth?: number;
  readonly strokeStyle?: "solid" | "dashed" | "dotted";
  readonly opacity?: number;
  readonly selected?: boolean;
  readonly hovered?: boolean;
  readonly animated?: boolean;
}

/** Flowchart edge. */
export interface FlowEdge extends BaseEdge {
  readonly diagramType: "flow";
  readonly flowType: "default" | "true" | "false" | "yes" | "no";
}

/** DAG edge. */
export interface DagEdge extends BaseEdge {
  readonly diagramType: "dag";
  readonly weight: number;
  readonly critical: boolean;
}

/** Topology edge types. */
export type TopologyEdgeType =
  | "wired"
  | "wireless"
  | "virtual"
  | "logical"
  | "physical";

/** Topology edge. */
export interface TopologyEdge extends BaseEdge {
  readonly diagramType: "topology";
  readonly topologyType: TopologyEdgeType;
  readonly bandwidth?: number;
  readonly latency?: number;
}

/** Swimlane edge. */
export interface SwimlaneEdge extends BaseEdge {
  readonly diagramType: "swimlane";
  readonly crossesLane: boolean;
}

/** Dependency edge. */
export interface DependencyEdge extends BaseEdge {
  readonly diagramType: "dependency-graph";
  readonly dependencyType: DependencyType;
  readonly optional: boolean;
  readonly versionConstraint?: string;
}

/** Provenance edge types. */
export type ProvenanceEdgeType =
  | "derived-from"
  | "transformed-into"
  | "influenced-by"
  | "generated-by"
  | "used-by";

/** Provenance edge. */
export interface ProvenanceEdge extends BaseEdge {
  readonly diagramType: "provenance-graph";
  readonly provenanceType: ProvenanceEdgeType;
  readonly timestamp?: string;
  readonly confidence?: number;
}

/** Union type for all diagram edges. */
export type DiagramEdge =
  | FlowEdge
  | DagEdge
  | TopologyEdge
  | SwimlaneEdge
  | DependencyEdge
  | ProvenanceEdge;

// ============================================================================
// DIAGRAM MODEL
// ============================================================================

/** Swimlane definition. */
export interface Swimlane {
  readonly id: string;
  readonly label: string;
  readonly color?: string;
  readonly order: number;
  readonly minWidth?: number;
  readonly maxWidth?: number;
}

/** Swimlane phase definition. */
export interface SwimlanePhase {
  readonly id: string;
  readonly label: string;
  readonly order: number;
  readonly color?: string;
}

/** Layout configuration for diagrams. */
export interface LayoutConfig {
  readonly algorithm:
    | "hierarchical"
    | "force-directed"
    | "circular"
    | "grid"
    | "dagre"
    | "custom";
  readonly direction?: "horizontal" | "vertical";
  readonly nodeSpacing?: number;
  readonly edgeSpacing?: number;
  readonly rankSpacing?: number;
  readonly padding?: number;
  readonly animate?: boolean;
  readonly animationDuration?: number;
  readonly customOptions?: Record<string, unknown>;
}

/** Validation result for layout configuration. */
export interface LayoutValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

/** Validate layout configuration. */
export function validateLayoutConfig(
  config: LayoutConfig,
): LayoutValidationResult {
  const errors: string[] = [];

  const validAlgorithms = [
    "hierarchical",
    "force-directed",
    "circular",
    "grid",
    "dagre",
    "custom",
  ];
  if (!validAlgorithms.includes(config.algorithm)) {
    errors.push(
      `Invalid layout algorithm: ${config.algorithm}. Valid options: ${validAlgorithms.join(", ")}`,
    );
  }

  if (config.nodeSpacing !== undefined && config.nodeSpacing < 0) {
    errors.push("nodeSpacing must be non-negative");
  }

  if (config.edgeSpacing !== undefined && config.edgeSpacing < 0) {
    errors.push("edgeSpacing must be non-negative");
  }

  if (config.rankSpacing !== undefined && config.rankSpacing < 0) {
    errors.push("rankSpacing must be non-negative");
  }

  if (config.padding !== undefined && config.padding < 0) {
    errors.push("padding must be non-negative");
  }

  if (
    config.animationDuration !== undefined &&
    (config.animationDuration < 0 || config.animationDuration > 5000)
  ) {
    errors.push("animationDuration must be between 0 and 5000ms");
  }

  return { valid: errors.length === 0, errors };
}

/** Complete diagram model. */
export interface Diagram {
  readonly id: string;
  readonly diagramType: DiagramType;
  readonly nodes: ReadonlyMap<string, DiagramNode>;
  readonly edges: ReadonlyMap<string, DiagramEdge>;
  readonly layout: LayoutConfig;
  readonly swimlanes?: readonly Swimlane[];
  readonly phases?: readonly SwimlanePhase[];
  readonly metadata?: Record<string, unknown>;
}

/** Diagram validation result. */
export interface DiagramValidationResult {
  readonly valid: boolean;
  readonly errors: readonly DiagramValidationError[];
  readonly warnings: readonly DiagramValidationWarning[];
}

/** Diagram validation error. */
export interface DiagramValidationError {
  readonly code: string;
  readonly message: string;
  readonly elementId?: string;
  readonly elementType?: "node" | "edge";
}

/** Diagram validation warning. */
export interface DiagramValidationWarning {
  readonly code: string;
  readonly message: string;
  readonly elementId?: string;
  readonly elementType?: "node" | "edge";
}

// ============================================================================
// DIAGRAM VALIDATION
// ============================================================================

/**
 * Validate a diagram model.
 */
export function validateDiagram(diagram: Diagram): DiagramValidationResult {
  const errors: DiagramValidationError[] = [];
  const warnings: DiagramValidationWarning[] = [];

  // Validate diagram type
  if (!isValidDiagramType(diagram.diagramType)) {
    errors.push({
      code: "INVALID_DIAGRAM_TYPE",
      message: `Unknown diagram type: ${diagram.diagramType}`,
    });
    return { valid: false, errors, warnings };
  }

  // Validate nodes
  if (diagram.nodes.size === 0) {
    warnings.push({
      code: "EMPTY_DIAGRAM",
      message: "Diagram contains no nodes",
    });
  }

  const nodeIds = new Set<string>();
  for (const [id, node] of diagram.nodes) {
    // Check for duplicate IDs
    if (nodeIds.has(id)) {
      errors.push({
        code: "DUPLICATE_NODE_ID",
        message: `Duplicate node ID: ${id}`,
        elementId: id,
        elementType: "node",
      });
    }
    nodeIds.add(id);

    // Validate node-diagram type consistency
    if (node.diagramType !== diagram.diagramType) {
      errors.push({
        code: "NODE_TYPE_MISMATCH",
        message: `Node ${id} has type ${node.diagramType} but diagram is ${diagram.diagramType}`,
        elementId: id,
        elementType: "node",
      });
    }

    // Validate dimensions
    if (node.width <= 0 || node.height <= 0) {
      errors.push({
        code: "INVALID_NODE_DIMENSIONS",
        message: `Node ${id} has invalid dimensions: ${node.width}x${node.height}`,
        elementId: id,
        elementType: "node",
      });
    }
  }

  // Validate edges
  for (const [id, edge] of diagram.edges) {
    // Validate edge-diagram type consistency
    if (edge.diagramType !== diagram.diagramType) {
      errors.push({
        code: "EDGE_TYPE_MISMATCH",
        message: `Edge ${id} has type ${edge.diagramType} but diagram is ${diagram.diagramType}`,
        elementId: id,
        elementType: "edge",
      });
    }

    // Validate source and target exist
    if (!diagram.nodes.has(edge.source)) {
      errors.push({
        code: "MISSING_SOURCE_NODE",
        message: `Edge ${id} references non-existent source: ${edge.source}`,
        elementId: id,
        elementType: "edge",
      });
    }
    if (!diagram.nodes.has(edge.target)) {
      errors.push({
        code: "MISSING_TARGET_NODE",
        message: `Edge ${id} references non-existent target: ${edge.target}`,
        elementId: id,
        elementType: "edge",
      });
    }

    // Self-loop warning
    if (edge.source === edge.target) {
      warnings.push({
        code: "SELF_LOOP",
        message: `Edge ${id} is a self-loop on node ${edge.source}`,
        elementId: id,
        elementType: "edge",
      });
    }
  }

  // Validate swimlane-specific requirements
  if (diagram.diagramType === "swimlane") {
    if (!diagram.swimlanes || diagram.swimlanes.length === 0) {
      errors.push({
        code: "MISSING_SWIMLANES",
        message: "Swimlane diagram must define at least one swimlane",
      });
    }

    // Validate all nodes reference valid swimlanes
    const validLaneIds = new Set(diagram.swimlanes?.map((s) => s.id) ?? []);
    for (const [id, node] of diagram.nodes) {
      if (node.diagramType === "swimlane") {
        const swimlaneNode = node as SwimlaneNode;
        if (!validLaneIds.has(swimlaneNode.lane)) {
          errors.push({
            code: "INVALID_SWIMLANE_REFERENCE",
            message: `Node ${id} references invalid swimlane: ${swimlaneNode.lane}`,
            elementId: id,
            elementType: "node",
          });
        }
      }
    }
  }

  // Validate layout
  const layoutValidation = validateLayoutConfig(diagram.layout);
  for (const error of layoutValidation.errors) {
    errors.push({
      code: "INVALID_LAYOUT_CONFIG",
      message: error,
    });
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

// ============================================================================
// DIAGRAM BUILDER
// ============================================================================

/** Builder for constructing diagrams. */
export class DiagramBuilder {
  private id: string;
  private diagramType: DiagramType;
  private nodes: Map<string, DiagramNode> = new Map();
  private edges: Map<string, DiagramEdge> = new Map();
  private layout: LayoutConfig;
  private swimlanes?: Swimlane[];
  private phases?: SwimlanePhase[];
  private metadata: Record<string, unknown> = {};

  constructor(id: string, diagramType: DiagramType) {
    if (!isValidDiagramType(diagramType)) {
      throw new Error(`Invalid diagram type: ${diagramType}`);
    }
    this.id = id;
    this.diagramType = diagramType;
    this.layout = {
      algorithm: "hierarchical",
      direction: "vertical",
    };
  }

  /** Set layout configuration. */
  setLayout(layout: LayoutConfig): this {
    const validation = validateLayoutConfig(layout);
    if (!validation.valid) {
      throw new Error(`Invalid layout: ${validation.errors.join(", ")}`);
    }
    this.layout = layout;
    return this;
  }

  /** Add a node to the diagram. */
  addNode(node: DiagramNode): this {
    if (node.diagramType !== this.diagramType) {
      throw new Error(
        `Node diagramType (${node.diagramType}) does not match builder type (${this.diagramType})`,
      );
    }
    if (this.nodes.has(node.id)) {
      throw new Error(`Node with ID ${node.id} already exists`);
    }
    this.nodes.set(node.id, node);
    return this;
  }

  /** Add an edge to the diagram. */
  addEdge(edge: DiagramEdge): this {
    if (edge.diagramType !== this.diagramType) {
      throw new Error(
        `Edge diagramType (${edge.diagramType}) does not match builder type (${this.diagramType})`,
      );
    }
    if (this.edges.has(edge.id)) {
      throw new Error(`Edge with ID ${edge.id} already exists`);
    }
    this.edges.set(edge.id, edge);
    return this;
  }

  /** Add a swimlane (only valid for swimlane diagrams). */
  addSwimlane(swimlane: Swimlane): this {
    if (this.diagramType !== "swimlane") {
      throw new Error("Swimlanes only valid for swimlane diagrams");
    }
    if (!this.swimlanes) {
      this.swimlanes = [];
    }
    this.swimlanes.push(swimlane);
    return this;
  }

  /** Add a phase (only valid for swimlane diagrams). */
  addPhase(phase: SwimlanePhase): this {
    if (this.diagramType !== "swimlane") {
      throw new Error("Phases only valid for swimlane diagrams");
    }
    if (!this.phases) {
      this.phases = [];
    }
    this.phases.push(phase);
    return this;
  }

  /** Set metadata. */
  setMetadata(key: string, value: unknown): this {
    this.metadata[key] = value;
    return this;
  }

  /** Build the diagram. */
  build(): Diagram {
    const diagram: Diagram = {
      id: this.id,
      diagramType: this.diagramType,
      nodes: this.nodes,
      edges: this.edges,
      layout: this.layout,
      metadata: this.metadata,
    };

    if (this.swimlanes) {
      Object.assign(diagram, { swimlanes: this.swimlanes });
    }
    if (this.phases) {
      Object.assign(diagram, { phases: this.phases });
    }

    const validation = validateDiagram(diagram);
    if (!validation.valid) {
      throw new Error(
        `Diagram validation failed: ${validation.errors.map((e) => e.message).join(", ")}`,
      );
    }

    return diagram;
  }
}

/** Create a diagram builder. */
export function createDiagram(
  id: string,
  diagramType: DiagramType,
): DiagramBuilder {
  return new DiagramBuilder(id, diagramType);
}
