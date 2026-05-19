/**
 * @fileoverview Diagram presets for common graph visualization patterns.
 *
 * Provides opinionated, named presets built on top of the base {@link DiagramType}
 * primitives. Each preset captures a well-known visualization pattern with
 * sensible layout defaults and feature-flag settings. Products instantiate a
 * preset rather than configuring every diagram property from scratch.
 *
 * @doc.type module
 * @doc.purpose Named diagram preset configurations for common visualization patterns
 * @doc.layer platform
 */

import type { DiagramType, LayoutConfig } from './types.js';

// ============================================================================
// PRESET ID
// ============================================================================

/**
 * Named preset identifiers for common diagram configurations.
 *
 * | Preset ID          | Base type          | Use case                                      |
 * |--------------------|--------------------|-----------------------------------------------|
 * | `lifecycle-plan`   | `swimlane`         | Sequential lifecycle phase planning + gates   |
 * | `dependency-graph` | `dependency-graph` | Module/package/service dependency tracking    |
 * | `topology`         | `topology`         | Network or infrastructure topology            |
 * | `provenance-graph` | `provenance-graph` | Data lineage and provenance tracing           |
 * | `gate-flow`        | `flow`             | Gate evaluation flow with decision branches   |
 */
export type DiagramPresetId =
  | "lifecycle-plan"
  | "dependency-graph"
  | "topology"
  | "provenance-graph"
  | "gate-flow";

// ============================================================================
// PRESET INTERFACE
// ============================================================================

/**
 * Feature flags controlling UI behaviour for a specific diagram preset.
 */
export interface DiagramPresetFeatureFlags {
  /** Show a legend panel describing node/edge types. */
  readonly showLegend: boolean;
  /** Show a background grid. */
  readonly showGrid: boolean;
  /** Show labels on edges. */
  readonly enableEdgeLabels: boolean;
  /** Allow visual grouping of nodes into clusters. */
  readonly enableGrouping: boolean;
}

/**
 * A diagram preset providing opinionated defaults for a common visualization pattern.
 *
 * Presets do NOT add new diagram types — they are configuration profiles over
 * the existing {@link DiagramType} set. Products may override individual
 * properties after applying a preset.
 */
export interface DiagramPreset {
  /** Unique preset identifier. */
  readonly id: DiagramPresetId;
  /** Human-readable display name. */
  readonly label: string;
  /** Longer description of the visualization pattern. */
  readonly description: string;
  /** The underlying base diagram type used to render this preset. */
  readonly diagramType: DiagramType;
  /** Default layout configuration applied when the preset is instantiated. */
  readonly defaultLayoutConfig: LayoutConfig;
  /** Feature flags controlling UI behaviour for this preset. */
  readonly featureFlags: DiagramPresetFeatureFlags;
}

// ============================================================================
// PRESET REGISTRY
// ============================================================================

/**
 * All built-in diagram presets indexed by {@link DiagramPresetId}.
 *
 * Consumers should treat this as read-only. Products that need custom defaults
 * should derive a modified copy rather than mutating this object.
 */
export const DIAGRAM_PRESETS: Readonly<Record<DiagramPresetId, DiagramPreset>> =
  {
    "lifecycle-plan": {
      id: "lifecycle-plan",
      label: "Lifecycle Plan",
      description:
        "Sequential lifecycle phase planning with gate checkpoints. " +
        "Visualizes phases, milestones, and gate dependencies across a product lifecycle. " +
        "Best rendered as a horizontal swimlane where each lane represents a lifecycle phase.",
      diagramType: "swimlane",
      defaultLayoutConfig: {
        algorithm: "hierarchical",
        direction: "horizontal",
        nodeSpacing: 40,
        rankSpacing: 80,
        padding: 20,
      },
      featureFlags: {
        showLegend: true,
        showGrid: false,
        enableEdgeLabels: true,
        enableGrouping: true,
      },
    },

    "dependency-graph": {
      id: "dependency-graph",
      label: "Dependency Graph",
      description:
        "Module, package, or service dependency relationships. " +
        "Highlights circular dependencies, transitive chains, and dependency depth " +
        "to support impact analysis and refactoring decisions.",
      diagramType: "dependency-graph",
      defaultLayoutConfig: {
        algorithm: "dagre",
        direction: "vertical",
        nodeSpacing: 30,
        rankSpacing: 60,
        padding: 16,
      },
      featureFlags: {
        showLegend: false,
        showGrid: false,
        enableEdgeLabels: true,
        enableGrouping: true,
      },
    },

    topology: {
      id: "topology",
      label: "Network Topology",
      description:
        "Network or service topology visualization. " +
        "Maps infrastructure components — servers, load balancers, databases, and cloud services — " +
        "alongside their network connectivity and traffic paths.",
      diagramType: "topology",
      defaultLayoutConfig: {
        algorithm: "force-directed",
        nodeSpacing: 50,
        padding: 20,
      },
      featureFlags: {
        showLegend: true,
        showGrid: true,
        enableEdgeLabels: false,
        enableGrouping: true,
      },
    },

    "provenance-graph": {
      id: "provenance-graph",
      label: "Provenance Graph",
      description:
        "Data lineage and provenance tracking. " +
        "Traces data from original sources through transformation checkpoints to final sinks, " +
        "supporting audit, compliance, and debugging workflows.",
      diagramType: "provenance-graph",
      defaultLayoutConfig: {
        algorithm: "dagre",
        direction: "horizontal",
        nodeSpacing: 35,
        rankSpacing: 70,
        padding: 16,
      },
      featureFlags: {
        showLegend: true,
        showGrid: false,
        enableEdgeLabels: true,
        enableGrouping: false,
      },
    },

    "gate-flow": {
      id: "gate-flow",
      label: "Gate Flow",
      description:
        "Gate evaluation flow with decision points and outcome branches. " +
        "Visualizes gate conditions, pass/fail outcomes, fallback paths, and escalation routes " +
        "for lifecycle gate evaluation pipelines.",
      diagramType: "flow",
      defaultLayoutConfig: {
        algorithm: "hierarchical",
        direction: "vertical",
        nodeSpacing: 30,
        rankSpacing: 60,
        padding: 16,
      },
      featureFlags: {
        showLegend: true,
        showGrid: false,
        enableEdgeLabels: true,
        enableGrouping: false,
      },
    },
  } as const;

// ============================================================================
// PRESET UTILITIES
// ============================================================================

/**
 * Get a diagram preset by ID.
 *
 * @param id - The {@link DiagramPresetId} to look up.
 * @returns The matching {@link DiagramPreset}.
 */
export function getDiagramPreset(id: DiagramPresetId): DiagramPreset {
  return DIAGRAM_PRESETS[id];
}

/**
 * Get all available diagram preset IDs.
 *
 * @returns A readonly array of all registered {@link DiagramPresetId} values.
 */
export function getDiagramPresetIds(): readonly DiagramPresetId[] {
  return Object.keys(DIAGRAM_PRESETS) as DiagramPresetId[];
}

/**
 * Check whether a string is a valid {@link DiagramPresetId}.
 *
 * @param value - The string to test.
 * @returns `true` if the value is a recognized preset ID.
 */
export function isDiagramPresetId(value: string): value is DiagramPresetId {
  return value in DIAGRAM_PRESETS;
}
